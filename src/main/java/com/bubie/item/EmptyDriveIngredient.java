package com.bubie.item;

import com.bubie.Bubie;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;

import javax.annotation.Nullable;

/**
 * Matches only an empty Hard Drive (no items stored on it), so crafting a Bubie never swallows cargo.
 */
public final class EmptyDriveIngredient extends AbstractIngredient {
    public static final EmptyDriveIngredient INSTANCE = new EmptyDriveIngredient();
    public static final IIngredientSerializer<EmptyDriveIngredient> SERIALIZER = new IIngredientSerializer<>() {
        @Override
        public EmptyDriveIngredient parse(FriendlyByteBuf buffer) {
            return INSTANCE;
        }

        @Override
        public EmptyDriveIngredient parse(JsonObject json) {
            return INSTANCE;
        }

        @Override
        public void write(FriendlyByteBuf buffer, EmptyDriveIngredient ingredient) {
        }
    };

    private EmptyDriveIngredient() {
        super();
    }

    @Override
    public ItemStack[] getItems() {
        return new ItemStack[]{new ItemStack(Bubie.HARD_DRIVE.get())};
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        return stack != null && stack.is(Bubie.HARD_DRIVE.get()) && !DriveItem.hasContents(stack);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", CraftingHelper.getID(SERIALIZER).toString());
        return json;
    }
}
