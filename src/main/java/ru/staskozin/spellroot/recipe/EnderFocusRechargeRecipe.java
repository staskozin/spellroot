package ru.staskozin.spellroot.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import ru.staskozin.spellroot.component.EnderFocusState;
import ru.staskozin.spellroot.config.SpellrootConfig;
import ru.staskozin.spellroot.item.EnderFocusItem;
import ru.staskozin.spellroot.registry.ModDataComponents;
import ru.staskozin.spellroot.registry.ModItems;
import ru.staskozin.spellroot.registry.ModRecipeSerializers;

@NullMarked
public final class EnderFocusRechargeRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack focus = findFocus(input);
        if (focus.isEmpty()) {
            return false;
        }
        EnderFocusState state = EnderFocusItem.normalizeState(focus);
        return state.charges() < state.maxCharges();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack focus = findFocus(input);
        if (focus.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = focus.copy();
        result.setCount(1);
        int maxCharges = SpellrootConfig.maxCharges();
        result.set(ModDataComponents.ENDER_FOCUS_STATE.get(), new EnderFocusState(
                maxCharges,
                maxCharges
        ));
        return result;
    }

    private static ItemStack findFocus(CraftingInput input) {
        if (input.ingredientCount() != 2) {
            return ItemStack.EMPTY;
        }

        ItemStack focus = ItemStack.EMPTY;
        boolean foundPearl = false;
        for (int index = 0; index < input.size(); index++) {
            ItemStack stack = input.getItem(index);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.ENDER_FOCUS.get()) && focus.isEmpty()) {
                focus = stack;
            } else if (stack.is(Items.ENDER_PEARL) && !foundPearl) {
                foundPearl = true;
            } else {
                return ItemStack.EMPTY;
            }
        }
        return foundPearl ? focus : ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<EnderFocusRechargeRecipe> getSerializer() {
        return ModRecipeSerializers.ENDER_FOCUS_RECHARGE.get();
    }
}
