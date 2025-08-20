package net.player005.vegandelightfabric.labels;

import com.google.common.base.Stopwatch;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.*;
import net.player005.vegandelightfabric.VeganDelightMod;
import net.player005.vegandelightfabric.recipe_manipulation.RecipeModification;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vectorwing.farmersdelight.client.recipe.CookingPotRecipeDisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabelUtils {

    private static final Map<Item, VeganStatus> veganFromRecipes = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger("VeganDelight");
    public static VeganStatus isVegan(ItemStack itemStack) {
        var component = itemStack.getComponents().get(VeganDataComponents.vegan.value());
        if (component != null) return VeganStatus.fromBoolean(component);

        if (itemStack.is(VeganTags.vegan)) return VeganStatus.VEGAN;
        if (itemStack.is(VeganTags.not_vegan)) return VeganStatus.NOT_VEGAN;

        return veganFromRecipes.getOrDefault(itemStack.getItem(), VeganStatus.UNKNOWN);
    }

    @ApiStatus.Internal
    public static void init() {
        var timer = Stopwatch.createStarted();
        for (Item item : BuiltInRegistries.ITEM) {
            scanRecipesRecursively(item, new ArrayList<>(BuiltInRegistries.ITEM.size()));
        }
        LOGGER.info("Scanned {} items for vegan recipes in {}", BuiltInRegistries.ITEM.size(), timer);
    }

    private static VeganStatus scanRecipesRecursively(final Item item, final List<Item> alreadyTraversed) {
        alreadyTraversed.add(item);
        final var recipes = RecipeModification.getRecipesByResult(item);

        var hadVeganRecipes = false;
        var hadNonVeganRecipes = false;

        for (RecipeHolder<?> recipeHolder : recipes) {
            if (recipeNotVegan(alreadyTraversed, recipeHolder)) hadNonVeganRecipes = true;
            else hadVeganRecipes = true;
        }

        var result = VeganStatus.VEGAN;
        if (hadNonVeganRecipes) result = VeganStatus.NOT_VEGAN;
        if (hadVeganRecipes == hadNonVeganRecipes) result = VeganStatus.UNKNOWN;

        veganFromRecipes.put(item, result);
        return result;
    }

    private static boolean recipeNotVegan(List<Item> alreadyTraversed, RecipeHolder<?> recipe) {
        for (var ingredient : RecipeModification.getIngredients(recipe)) {
            for (var itemHolder : ingredient.items().toList()) {
                var vegan = isVegan(itemHolder.value().getDefaultInstance());

                if (vegan == VeganStatus.UNKNOWN) {
                    var unknownItem = itemHolder.value();
                    if (!alreadyTraversed.contains(unknownItem))
                        vegan = scanRecipesRecursively(unknownItem, alreadyTraversed);
                }

                if (vegan == VeganStatus.NOT_VEGAN) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shouldRenderTooltip(ItemStack itemStack) {
        return itemStack.has(VeganDataComponents.vegan.value()) && !itemStack.is(VeganTags.vegan) &&
            itemStack.getComponents().has(VeganDataComponents.containsSubstitutes.value());
    }

    public static void addTooltipLines(ItemStack itemStack, List<Component> tooltip) {
        if (!shouldRenderTooltip(itemStack)) return;
        tooltip.add(1, Component.literal("Vegan").setStyle(
            Style.EMPTY
                .withColor(0x008c44)
                .withItalic(true)
                .withBold(true)
        ));
    }

    public static void addTooltipLinesDebug(ItemStack itemStack, List<Component> tooltip) {
        tooltip.add(1, Component.literal("Vegan: " + isVegan(itemStack).name()).setStyle(
            Style.EMPTY
                .withColor(0x008c44)
                .withItalic(true)
                .withBold(true)
        ));
        tooltip.add(2, Component.literal("Show label: " + shouldRenderTooltip(itemStack)));
    }

    public enum VeganStatus {
        VEGAN, NOT_VEGAN, UNKNOWN;

        @Nullable
        public Boolean toOptionalBoolean() {
            return switch (this) {
                case VEGAN -> true;
                case NOT_VEGAN -> false;
                case UNKNOWN -> null;
            };
        }

        public static VeganStatus fromBoolean(@Nullable Boolean bool) {
            if (bool == null) return UNKNOWN;
            if (bool) return VEGAN;
            else return NOT_VEGAN;
        }
    }

    @ApiStatus.Internal
    public static void modifyRecipeResult(RecipeInput recipeInput, ItemStack result) {
        for (var i = 0; i < recipeInput.size(); i++) {
            var item = recipeInput.getItem(i);
            if (isVegan(item) == VeganStatus.NOT_VEGAN) {
                result.applyComponents(VeganDataComponents.setIsNotVegan.get());
            }
            if (item.is(VeganTags.vegan_alternative)) {
                result.applyComponents(VeganDataComponents.setContainsSubstitutes.get());
            }
        }
        result.applyComponents(VeganDataComponents.setIsVegan.get());
    }
}
