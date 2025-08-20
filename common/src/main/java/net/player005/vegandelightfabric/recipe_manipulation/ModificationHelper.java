package net.player005.vegandelightfabric.recipe_manipulation;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.player005.vegandelightfabric.holder.OrHolderSet;
import net.player005.vegandelightfabric.holder.ReducedHolderSet;

/**
 * A helper class for modifying recipes easily.
 *
 * @see RecipeModification#registerModifier(RecipeModifier)
 * @see RecipeModifier
 */
public class ModificationHelper {

    private final RecipeHolder<?> recipeHolder;

    /**
     * Constructs a new recipe modification helper.
     */
    public ModificationHelper(RecipeHolder<?> recipe) {
        this.recipeHolder = recipe;
    }

    /**
     * Tries to remove the given ingredient from the recipe.
     * Doesn't work with all recipe types (like cooking/smelting and stonecutting)
     */
    //public void removeIngredient(Ingredient ingredient) {
    //    recipeHolder.value().getIngredients().remove(ingredient);
    //}

    /**
     * Tries to add another ingredient to the recipe.
     * Doesn't work with all recipe types (like cooking/smelting and stonecutting)
     */
    //public void addIngredient(Ingredient ingredient) {
    //    recipeHolder.value().getIngredients().add(ingredient);
    //}

    /**
     * Add an alternative to matching items.
     *
     * @param original    the item that can be substituted
     * @param alternative the substitute
     */
    public void addAlternative(Item original, HolderSet<Item> alternative) {
        for (Ingredient ingredient : RecipeModification.getIngredients(getRecipeHolder())) {
            for (Holder<Item> item : ingredient.items().toList()) {
                if (item.value() == original) addIngredientValue(ingredient, alternative);
            }
        }
    }

    /**
     * Add an alternative to matching items.
     *
     * @param original    the item that can be substituted
     * @param alternative the substitute
     */
    public void addAlternative(Item original, Item alternative) {
        addAlternative(original, HolderSet.direct(BuiltInRegistries.ITEM.get(BuiltInRegistries.ITEM.getKey(alternative)).orElseThrow()));
    }

    /**
     * Add an alternative to matching items.
     *
     * @param original    the item that can be substituted
     * @param alternative the substitute
     */
    //public void addAlternative(Item original, TagKey<Item> alternative) {
    //    addAlternative(original, new Ingredient.TagValue(alternative));
    //}

    /**
     * Completely replaces the {@link Ingredient#values} of the ingredient with the given one
     * (effectively replacing the entire ingredient)
     */
    public void replaceIngredientValues(Ingredient ingredient, HolderSet<Item> ingredientValues) {
        if (ingredient.values == ingredientValues) return;
        ingredient.values = ingredientValues;
    }

    /**
     * Replaces the given old ingredient with another ingredient.
     * Under the hood, this just calls {@link #replaceIngredientValues(Ingredient, Ingredient.Value[])}
     * to copy the data from the new ingredient to the existing one.
     */
    public void replaceIngredient(Ingredient old, Ingredient newIngredient) {
        replaceIngredientValues(old, newIngredient.values);
    }

    /**
     * Removes a given alternative from the ingredient so that it can't be used for the ingredient/recipe anymore.
     * If the given value is the only one in the Ingredient, the recipe might become impossible to make.
     */
    public void removeIngredientValue(Ingredient ingredient, HolderSet<Item> toRemove) {
        ingredient.values = new ReducedHolderSet<>(ingredient.values, toRemove);
    }

    /**
     * Adds a {@link Ingredient.Value} to the given ingredient, providing an
     * alternative item to use for the ingredient/recipe.
     */
    public void addIngredientValue(Ingredient ingredient, HolderSet<Item> addedValue) {
        ingredient.values = new OrHolderSet<>(ingredient.values, addedValue);
    }


    public RecipeHolder<?> getRecipeHolder() {
        return recipeHolder;
    }
}
