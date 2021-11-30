/*
 * Copyright (c) 2018-2020 C4
 *
 * This file is part of Curios, a mod made for Minecraft.
 *
 * Curios is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Curios is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Curios.  If not, see <https://www.gnu.org/licenses/>.
 */

package top.theillusivec4.curios.client;

import static net.minecraft.world.item.ItemStack.ATTRIBUTE_MODIFIER_FORMAT;

import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fmllegacy.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.common.CuriosHelper;
import top.theillusivec4.curios.common.network.NetworkHandler;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;

public class ClientEventHandler {

  private static final UUID ATTACK_DAMAGE_MODIFIER = UUID
      .fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
  private static final UUID ATTACK_SPEED_MODIFIER = UUID
      .fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

  @SubscribeEvent
  public void onKeyInput(TickEvent.ClientTickEvent evt) {

    if (evt.phase != TickEvent.Phase.END) {
      return;
    }

    Minecraft mc = Minecraft.getInstance();

    if (KeyRegistry.openCurios.consumeClick() && mc.isWindowActive()) {
      NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), new CPacketOpenCurios());
    }
  }

  @SubscribeEvent
  public void onTooltip(ItemTooltipEvent evt) {
    ItemStack stack = evt.getItemStack();
    Player player = evt.getPlayer();

    if (!stack.isEmpty()) {
      List<Component> tooltip = evt.getToolTip();
      CompoundTag tag = stack.getTag();
      int i = 0;

      if (tag != null && tag.contains("HideFlags", 99)) {
        i = tag.getInt("HideFlags");
      }

      Set<String> curioTags = CuriosApi.getCuriosHelper().getCurioTags(stack.getItem());
      List<String> slots = new ArrayList<>(curioTags);

      if (!slots.isEmpty()) {
        List<Component> tagTooltips = new ArrayList<>();
        MutableComponent slotsTooltip = new TranslatableComponent("curios.slot")
            .append(": ").withStyle(ChatFormatting.GOLD);

        for (int j = 0; j < slots.size(); j++) {
          String key = "curios.identifier." + slots.get(j);
          MutableComponent type = new TranslatableComponent(key);

          if (j < slots.size() - 1) {
            type = type.append(", ");
          }

          type = type.withStyle(ChatFormatting.YELLOW);
          slotsTooltip.append(type);
        }
        tagTooltips.add(slotsTooltip);

        LazyOptional<ICurio> optionalCurio = CuriosApi.getCuriosHelper().getCurio(stack);
        optionalCurio.ifPresent(curio -> {
          List<Component> actualSlotsTooltip = curio.getSlotsTooltip(tagTooltips);

          if (!actualSlotsTooltip.isEmpty()) {
            tooltip.addAll(1, actualSlotsTooltip);
          }
        });

        if (!optionalCurio.isPresent()) {
          tooltip.addAll(1, tagTooltips);
        }
        List<Component> attributeTooltip = new ArrayList<>();

        for (String identifier : slots) {
          Multimap<Attribute, AttributeModifier> multimap = CuriosApi.getCuriosHelper()
              .getAttributeModifiers(new SlotContext(identifier, player, 0, false, true),
                  UUID.randomUUID(), stack);

          if (!multimap.isEmpty() && (i & 2) == 0) {
            attributeTooltip.add(TextComponent.EMPTY);
            attributeTooltip.add(new TranslatableComponent("curios.modifiers." + identifier)
                .withStyle(ChatFormatting.GOLD));

            for (Map.Entry<Attribute, AttributeModifier> entry : multimap.entries()) {
              AttributeModifier attributemodifier = entry.getValue();
              double amount = attributemodifier.getAmount();
              boolean flag = false;

              if (player != null) {

                if (attributemodifier.getId() == ATTACK_DAMAGE_MODIFIER) {
                  AttributeInstance att = player.getAttribute(Attributes.ATTACK_DAMAGE);

                  if (att != null) {
                    amount = amount + att.getBaseValue();
                  }
                  amount = amount + EnchantmentHelper
                      .getDamageBonus(stack, MobType.UNDEFINED);
                  flag = true;
                } else if (attributemodifier.getId() == ATTACK_SPEED_MODIFIER) {
                  AttributeInstance att = player.getAttribute(Attributes.ATTACK_SPEED);

                  if (att != null) {
                    amount += att.getBaseValue();
                  }
                  flag = true;
                }

                double d1;

                if (attributemodifier.getOperation() != AttributeModifier.Operation.MULTIPLY_BASE
                    && attributemodifier.getOperation()
                    != AttributeModifier.Operation.MULTIPLY_TOTAL) {
                  d1 = amount;
                } else {
                  d1 = amount * 100.0D;
                }

                if (entry.getKey() instanceof CuriosHelper.SlotAttributeWrapper wrapper) {

                  if (amount > 0.0D) {
                    attributeTooltip.add((new TranslatableComponent(
                                "curios.modifiers.slots.plus." +
                                    attributemodifier.getOperation().toValue(),
                                ATTRIBUTE_MODIFIER_FORMAT.format(d1),
                                new TranslatableComponent("curios.identifier." + wrapper.identifier)))
                            .withStyle(ChatFormatting.BLUE));
                  } else {
                    d1 = d1 * -1.0D;
                    attributeTooltip.add((new TranslatableComponent(
                                "curios.modifiers.slots.take." +
                                    attributemodifier.getOperation().toValue(),
                                ATTRIBUTE_MODIFIER_FORMAT.format(d1),
                                new TranslatableComponent("curios.identifier." + wrapper.identifier)))
                            .withStyle(ChatFormatting.RED));
                  }
                } else if (flag) {
                  attributeTooltip.add(
                      (new TextComponent(" ")).append(new TranslatableComponent(
                              "attribute.modifier.equals." + attributemodifier.getOperation().toValue(),
                              ATTRIBUTE_MODIFIER_FORMAT.format(d1),
                              new TranslatableComponent(entry.getKey().getDescriptionId())))
                          .withStyle(ChatFormatting.DARK_GREEN));
                } else if (amount > 0.0D) {
                  attributeTooltip.add((new TranslatableComponent(
                      "attribute.modifier.plus." + attributemodifier.getOperation().toValue(),
                      ATTRIBUTE_MODIFIER_FORMAT.format(d1),
                      new TranslatableComponent(entry.getKey().getDescriptionId())))
                      .withStyle(ChatFormatting.BLUE));
                } else if (amount < 0.0D) {
                  d1 = d1 * -1.0D;
                  attributeTooltip.add((new TranslatableComponent(
                      "attribute.modifier.take." + attributemodifier.getOperation().toValue(),
                      ATTRIBUTE_MODIFIER_FORMAT.format(d1),
                      new TranslatableComponent(entry.getKey().getDescriptionId())))
                      .withStyle(ChatFormatting.RED));
                }
              }
            }
          }
        }
        optionalCurio.ifPresent(curio -> {
          List<Component> actualAttributeTooltips =
              curio.getAttributesTooltip(attributeTooltip);

          if (!actualAttributeTooltips.isEmpty()) {
            tooltip.addAll(actualAttributeTooltips);
          }
        });

        if (!optionalCurio.isPresent()) {
          tooltip.addAll(attributeTooltip);
        }
      }
    }
  }
}
