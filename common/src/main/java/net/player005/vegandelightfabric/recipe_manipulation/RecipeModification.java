package net.player005.vegandelightfabric.recipe_manipulation;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.crafting.DoughRecipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The main class for recipe modifications, containing some utility methods.
 *
 * @see #registerModifier(RecipeModifier)
 * @see #removeRecipe(RecipeHolder)
 * @see #forAllRecipes(Consumer)
 */
public abstract class RecipeModification {

    private static final Logger logger = LoggerFactory.getLogger(RecipeModification.class);

    private static final NonNullList<Consumer<RecipeManager>> recipeManagerCallbacks = NonNullList.create();
    private static final NonNullList<Consumer<RecipeHolder<?>>> recipeIterationCallbacks = NonNullList.create();
    private static final Map<RecipeFilter, Consumer<RecipeHolder<?>>> filteredRecipeCallbacks = new HashMap<>();

    private static final NonNullList<ResourceKey<Recipe<?>>> toRemove = NonNullList.create();
    private static NonNullList<RecipeModifier> modifiers = NonNullList.create();

    private static @UnknownNullability ImmutableMultimap<Item, RecipeHolder<?>> recipesByResult;

    private static @UnknownNullability RecipeManager recipeManager;

    /**
     * This method can be used to have some code be executed when the server is starting, right before
     * we apply recipe modifiers. It is also an easy way to access the {@link RecipeManager}.
     * <p>
     * The given consumer will be executed on every datapack reload on dedicated servers,
     * or everytime a singleplayer world is loaded on the Client.
     * </p>
     */
    public static void onRecipeInit(Consumer<RecipeManager> consumer) {
        recipeManagerCallbacks.add(consumer);
    }

    /**
     * The given lambda will be called once for EVERY loaded recipe.
     * Using this method is cheaper than looping through all recipes yourself since
     * this library already iterates all recipes anyway.
     */
    public static void forAllRecipes(Consumer<RecipeHolder<?>> recipeConsumer) {
        recipeIterationCallbacks.add(recipeConsumer);
    }

    /**
     * The given lambda will be called once for every loaded recipe matching the given filter.
     * Using this method is cheaper than looping through all recipes yourself since
     * this library already iterates all recipes anyway.
     */
    public static void forAllRecipes(Consumer<RecipeHolder<?>> recipeConsumer, RecipeFilter filter) {
        filteredRecipeCallbacks.put(filter, recipeConsumer);
    }

    /**
     * Removes the given recipe from the game
     *
     * @param recipeHolder The recipe to remove
     */
    public static void removeRecipe(RecipeHolder<?> recipeHolder) {
        toRemove.add(recipeHolder.id());
    }

    /**
     * Removes the given recipe from the game
     *
     * @param id The ResourceLocation of the recipe to remove
     */
    public static void removeRecipe(ResourceKey<Recipe<?>> id) {
        toRemove.add(id);
    }

    /**
     * Registers a {@link RecipeModifier} to be applied when loading recipes.
     */
    public static void registerModifier(RecipeModifier recipeModifier) {
        modifiers.add(recipeModifier);
    }

    /**
     * Internal method that should be called on every datapack reload.
     * Initialises all registered {@link RecipeModifier}s, calls all {@link #onRecipeInit(Consumer)}
     * callbacks and removes recipes registered for removal using {@link #removeRecipe(RecipeHolder)}
     */
    @ApiStatus.Internal
    public static void init(RecipeManager recipeManager) {
        RecipeModification.recipeManager = recipeManager;
        var timer = Stopwatch.createStarted();

        var byResultBuilder = ImmutableMultimap.<Item, RecipeHolder<?>>builder();
        for (RecipeHolder<?> recipeHolder : recipeManager.getRecipes()) {
            ItemStack result = getResult(recipeHolder);
            if (result != null) {
                byResultBuilder.put(result.getItem(), recipeHolder);
            }
        }

        recipesByResult = byResultBuilder.build();
        logger.debug("Built recipe by result map for {} recipes in {}", recipeManager.getRecipes().size(), timer);
        timer.reset().start();

        for (Consumer<RecipeManager> recipeManagerCallback : recipeManagerCallbacks) {
            recipeManagerCallback.accept(recipeManager);
        }
        logger.debug("Executed {} recipe callbacks in {}", recipeManagerCallbacks.size(), timer);

        timer.reset().start();
        var modified = 0;

        for (RecipeHolder<?> recipeHolder : recipeManager.getRecipes()) {
            var registryAccess = recipeManager.registries;

            // call registered callbacks
            for (Consumer<RecipeHolder<?>> recipeIterationCallback : recipeIterationCallbacks) {
                recipeIterationCallback.accept(recipeHolder);
            }

            for (var entry : filteredRecipeCallbacks.entrySet()) {
                if (entry.getKey().shouldApply(recipeHolder, registryAccess)) entry.getValue().accept(recipeHolder);
            }

            // apply recipe modifiers
            for (RecipeModifier modifier : modifiers) {
                if (!modifier.getFilter().shouldApply(recipeHolder, registryAccess)) continue;
                var helper = new ModificationHelper(recipeHolder);
                modifier.apply(recipeHolder.value(), helper);
                modified++;
            }
            recipeManager.recipes = RecipeMap.create(recipeManager.recipes.values().stream().filter(holder -> !toRemove.contains(holder.id())).toList());
        }
        logger.info("Modified {} recipes in {}", modified, timer);
    }

    @ApiStatus.Internal
    static void updateModifiers(NonNullList<RecipeModifier> modifiers) {
        RecipeModification.modifiers = modifiers;
    }

    /**
     * Returns the Minecraft server's {@link RecipeManager} saved by this class - might be {@code null} in some cases
     * (when the game is not fully initialised yet). <p>Safe to call after the {@link #onRecipeInit(Consumer)} callbacks
     * were called
     */
    public static @UnknownNullability RecipeManager getRecipeManager() {
        return recipeManager;
    }

    /**
     * Returns the {@link RecipeManager}s registry access (a {@link HolderLookup.Provider}) -
     * might be {@code null} in some cases (see {@link #getRecipeManager()} docs)
     */
    public static HolderLookup.@UnknownNullability Provider getRegistryAccess() {
        return recipeManager.registries;
    }

    /**
     * Get an (immutable) multimap from the result item to the recipes creating that item.
     * Can only be called after recipe initialisation (i.e. after {@link #onRecipeInit(Consumer)}
     * callbacks were called).
     *
     * @see #getRecipesByResult(Item)
     */
    public static ImmutableMultimap<Item, RecipeHolder<?>> getRecipesByResult() {
        return recipesByResult;
    }

    /**
     * Returns all recipes that create the given result item.
     * Can only be called after recipe initialisation (i.e. after {@link #onRecipeInit(Consumer)}
     * callbacks were called).
     *
     * @see #getRecipesByResult()
     */
    public static ImmutableCollection<RecipeHolder<?>> getRecipesByResult(Item resultItem) {
        return recipesByResult.get(resultItem);
    }

    @Nullable
    public static ItemStack getResult(RecipeHolder<?> recipeHolder) {

        ItemStack result = null;
        Recipe<?> r = recipeHolder.value();
        try {
            result = r.assemble(null, getRegistryAccess());
        } catch (NullPointerException npe) {
        }
        if (result != null) {
            return result;
        }

        DataResult<JsonElement> recipeJson = Recipe.CODEC.encodeStart(JsonOps.INSTANCE, r);
        if (recipeJson.isError()) {
            logger.info("couldn't encode recipe {} of type {}, because {}", recipeHolder.id(), r.getClass(), recipeJson.error().get().message());
        }

        JsonElement json = recipeJson.getOrThrow();
        if (json instanceof JsonObject jsonObject) {
            List<String> outputNames = List.of("result", "output");
            for (String outputName : outputNames) {
                if (jsonObject.has(outputName)) {
                    JsonElement outputJson = jsonObject.get(outputName);
                    DataResult<Pair<ItemStack, JsonElement>> single = ItemStack.CODEC.decode(JsonOps.INSTANCE, outputJson);
                    if (single.isSuccess()) {
                        return single.getOrThrow().getFirst();
                    }
                }
            }
        }
        logger.info("can't find result for recipe {} with type {}", recipeHolder.id(), r.getClass());
        return null;
    }

    public static List<Ingredient> getIngredients(RecipeHolder<?> recipeHolder) {
        Recipe<?> r = recipeHolder.value();

        PlacementInfo placementInfo = r.placementInfo();
        if (placementInfo != PlacementInfo.NOT_PLACEABLE) {
            return placementInfo.ingredients();
        }
        if (r instanceof CuttingBoardRecipe recipe) {
            return List.of(recipe.getInput());
        }
        if (r instanceof DoughRecipe) {
            return List.of(Ingredient.of(Items.WHEAT), Ingredient.of(Items.WATER_BUCKET));
        }
        DataResult<JsonElement> recipeJson = Recipe.CODEC.encodeStart(JsonOps.INSTANCE, r);
        if (recipeJson.isError()) {
            logger.info("couldn't encode recipe {} of type {}, because {}", recipeHolder.id(), r.getClass(), recipeJson.error().get().message());
        }

        JsonElement json = recipeJson.getOrThrow();
        if (json instanceof JsonObject jsonObject) {
            List<String> inputNames = List.of("input", "ingredient", "ingredients");
            for (String inputName : inputNames) {
                if (jsonObject.has(inputName)) {
                    JsonElement inputJson = jsonObject.get(inputName);
                    DataResult<Pair<Ingredient, JsonElement>> single = Ingredient.CODEC.decode(JsonOps.INSTANCE, inputJson);
                    if (single.isSuccess()) {
                        return List.of(single.getOrThrow().getFirst());
                    }
                    DataResult<Pair<List<Ingredient>, JsonElement>> list = Ingredient.CODEC.listOf().decode(JsonOps.INSTANCE, inputJson);
                    if (list.isSuccess()) {
                        return list.getOrThrow().getFirst();
                    }
                }
            }
        }
        logger.info("can't find ingredients for recipe {} with type {}", recipeHolder.id(), r.getClass());
        return List.of();
    }
}
