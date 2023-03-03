package top.theillusivec4.curios.common.objects;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootFunction;
import net.minecraft.loot.LootFunctionType;
import net.minecraft.loot.RandomValueRange;
import net.minecraft.loot.conditions.ILootCondition;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.registry.Registry;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.common.CuriosHelper;

public class SetCurioAttributes extends LootFunction {

  public static LootFunctionType TYPE = null;

  final List<Modifier> modifiers;

  SetCurioAttributes(ILootCondition[] conditions, List<Modifier> modifiers) {
    super(conditions);
    this.modifiers = ImmutableList.copyOf(modifiers);
  }

  public static void register() {
    TYPE = Registry.register(Registry.LOOT_FUNCTION_TYPE,
        new ResourceLocation(CuriosApi.MODID, "set_curio_attributes"),
        new LootFunctionType(new SetCurioAttributes.Serializer()));
  }

  @Nonnull
  public LootFunctionType getFunctionType() {
    return TYPE;
  }

  @Nonnull
  public ItemStack doApply(@Nonnull ItemStack stack, LootContext context) {
    Random random = context.getRandom();

    for (Modifier modifier : this.modifiers) {
      UUID uuid = modifier.id;
      String slot = Util.getRandomObject(modifier.slots, random);

      if (modifier.attribute instanceof CuriosHelper.SlotAttributeWrapper) {
        CuriosHelper.SlotAttributeWrapper wrapper =
            (CuriosHelper.SlotAttributeWrapper) modifier.attribute;
        CuriosApi.getCuriosHelper().addSlotModifier(stack, wrapper.identifier, modifier.name, uuid,
            modifier.amount.generateFloat(random), modifier.operation, slot);
      } else {
        CuriosApi.getCuriosHelper().addModifier(stack, modifier.attribute, modifier.name, uuid,
            modifier.amount.generateFloat(random), modifier.operation, slot);
      }
    }
    return stack;
  }

  static class Modifier {
    final String name;
    final Attribute attribute;
    final AttributeModifier.Operation operation;
    final RandomValueRange amount;
    @Nullable
    final UUID id;
    final String[] slots;

    Modifier(String name, Attribute attribute, AttributeModifier.Operation operation,
             RandomValueRange amount, String[] slots, @Nullable UUID uuid) {
      this.name = name;
      this.attribute = attribute;
      this.operation = operation;
      this.amount = amount;
      this.id = uuid;
      this.slots = slots;
    }

    public JsonObject serialize(JsonSerializationContext context) {
      JsonObject jsonobject = new JsonObject();
      jsonobject.addProperty("name", this.name);
      ResourceLocation rl;

      if (this.attribute instanceof CuriosHelper.SlotAttributeWrapper) {
        CuriosHelper.SlotAttributeWrapper wrapper =
            (CuriosHelper.SlotAttributeWrapper) this.attribute;
        rl = new ResourceLocation(CuriosApi.MODID, wrapper.identifier);
      } else {
        rl = ForgeRegistries.ATTRIBUTES.getKey(this.attribute);
      }

      if (rl != null) {
        jsonobject.addProperty("attribute", rl.toString());
      }
      jsonobject.addProperty("operation", operationToString(this.operation));
      jsonobject.add("amount", context.serialize(this.amount));

      if (this.id != null) {
        jsonobject.addProperty("id", this.id.toString());
      }

      if (this.slots.length == 1) {
        jsonobject.addProperty("slot", this.slots[0]);
      } else {
        JsonArray jsonarray = new JsonArray();

        for (String slot : this.slots) {
          jsonarray.add(new JsonPrimitive(slot));
        }
        jsonobject.add("slot", jsonarray);
      }
      return jsonobject;
    }

    public static Modifier deserialize(JsonObject object, JsonDeserializationContext context) {
      String s = JSONUtils.getString(object, "name");
      ResourceLocation resourcelocation =
          new ResourceLocation(JSONUtils.getString(object, "attribute"));
      Attribute attribute;

      if (resourcelocation.getNamespace().equals("curios")) {
        String identifier = resourcelocation.getPath();

        if (!CuriosApi.getSlotHelper().getSlotType(identifier).isPresent()) {
          throw new JsonSyntaxException("Unknown curios slot type: " + identifier);
        }
        attribute = CuriosHelper.getOrCreateSlotAttribute(identifier);
      } else {
        attribute = ForgeRegistries.ATTRIBUTES.getValue(resourcelocation);
      }

      if (attribute == null) {
        throw new JsonSyntaxException("Unknown attribute: " + resourcelocation);
      } else {
        AttributeModifier.Operation operation =
            operationFromString(JSONUtils.getString(object, "operation"));
        RandomValueRange numberprovider =
            JSONUtils.deserializeClass(object, "amount", context, RandomValueRange.class);
        UUID uuid = null;
        String[] slots;

        if (JSONUtils.isString(object, "slot")) {
          slots = new String[] {JSONUtils.getString(object, "slot")};
        } else {

          if (!JSONUtils.isJsonArray(object, "slot")) {
            throw new JsonSyntaxException(
                "Invalid or missing attribute modifier slot; must be either string or array of strings.");
          }
          JsonArray jsonarray = JSONUtils.getJsonArray(object, "slot");
          slots = new String[jsonarray.size()];
          int i = 0;

          for (JsonElement jsonelement : jsonarray) {
            slots[i++] = JSONUtils.getString(jsonelement, "slot");
          }

          if (slots.length == 0) {
            throw new JsonSyntaxException(
                "Invalid attribute modifier slot; must contain at least one entry.");
          }
        }

        if (object.has("id")) {
          String s1 = JSONUtils.getString(object, "id");

          try {
            uuid = UUID.fromString(s1);
          } catch (IllegalArgumentException illegalargumentexception) {
            throw new JsonSyntaxException(
                "Invalid attribute modifier id '" + s1 + "' (must be UUID format, with dashes)");
          }
        }
        return new Modifier(s, attribute, operation, numberprovider, slots, uuid);
      }
    }

    private static String operationToString(AttributeModifier.Operation operation) {
      switch (operation) {
        case ADDITION:
          return "addition";
        case MULTIPLY_BASE:
          return "multiply_base";
        case MULTIPLY_TOTAL:
          return "multiply_total";
        default:
          throw new IllegalArgumentException();
      }
    }

    private static AttributeModifier.Operation operationFromString(String operation) {
      switch (operation) {
        case "addition":
          return AttributeModifier.Operation.ADDITION;
        case "multiply_base":
          return AttributeModifier.Operation.MULTIPLY_BASE;
        case "multiply_total":
          return AttributeModifier.Operation.MULTIPLY_TOTAL;
        default:
          throw new JsonSyntaxException(
              "Unknown attribute modifier operation " + operation);
      }
    }
  }

  public static class Serializer extends LootFunction.Serializer<SetCurioAttributes> {

    public void serialize(@Nonnull JsonObject object,
                          @Nonnull SetCurioAttributes function,
                          @Nonnull JsonSerializationContext context) {
      super.serialize(object, function, context);
      JsonArray jsonarray = new JsonArray();

      for (Modifier modifier : function.modifiers) {
        jsonarray.add(modifier.serialize(context));
      }
      object.add("modifiers", jsonarray);
    }

    @Nonnull
    public SetCurioAttributes deserialize(@Nonnull JsonObject object,
                                          @Nonnull JsonDeserializationContext context,
                                          @Nonnull ILootCondition[] conditions) {
      JsonArray jsonarray = JSONUtils.getJsonArray(object, "modifiers");
      List<Modifier> list = Lists.newArrayListWithExpectedSize(jsonarray.size());

      for (JsonElement jsonelement : jsonarray) {
        list.add(
            Modifier.deserialize(JSONUtils.getJsonObject(jsonelement, "modifier"), context));
      }

      if (list.isEmpty()) {
        throw new JsonSyntaxException("Invalid attribute modifiers array; cannot be empty");
      } else {
        return new SetCurioAttributes(conditions, list);
      }
    }
  }
}