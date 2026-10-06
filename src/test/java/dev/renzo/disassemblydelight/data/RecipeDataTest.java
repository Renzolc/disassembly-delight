package dev.renzo.disassemblydelight.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The shipped recipe data: the backpack breakdown chain, the Disassembly Table Upgrade recipe and its breakdown,
 * no breakdown for the Disassembly Table itself, and no full_uncraft result from a mod the recipe does not require.
 */
class RecipeDataTest {
    private static final String FULL_UNCRAFT = "data/disassembly_delight/recipe/full_uncraft";

    private static Path resources() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("src/main/resources");
            if (Files.isDirectory(candidate.resolve(FULL_UNCRAFT))) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("src/main/resources not found from " + Path.of("").toAbsolutePath());
    }

    private static JsonObject read(String path) throws IOException {
        try (Reader reader = Files.newBufferedReader(resources().resolve(path))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /** id -> count, in file order. */
    private static Map<String, Integer> results(JsonObject recipe) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (JsonElement e : recipe.getAsJsonArray("results")) {
            JsonObject r = e.getAsJsonObject();
            out.merge(r.get("id").getAsString(), r.has("count") ? r.get("count").getAsInt() : 1, Integer::sum);
        }
        return out;
    }

    private static Set<String> requiredMods(JsonObject recipe) {
        Set<String> mods = new HashSet<>();
        if (recipe.has("neoforge:conditions")) {
            for (JsonElement e : recipe.getAsJsonArray("neoforge:conditions")) {
                collectMods(e.getAsJsonObject(), mods);
            }
        }
        return mods;
    }

    /** mod_loaded conditions, also inside neoforge:and. */
    private static void collectMods(JsonObject condition, Set<String> into) {
        String type = condition.get("type").getAsString();
        if ("neoforge:mod_loaded".equals(type)) {
            into.add(condition.get("modid").getAsString());
        } else if ("neoforge:and".equals(type)) {
            for (JsonElement e : condition.getAsJsonArray("values")) {
                collectMods(e.getAsJsonObject(), into);
            }
        }
    }

    private static void assertStep(String file, String input, Map<String, Integer> expected) throws IOException {
        JsonObject recipe = read(FULL_UNCRAFT + "/" + file);
        assertEquals(input, recipe.getAsJsonObject("input").get("item").getAsString());
        assertEquals(1, recipe.has("consume") ? recipe.get("consume").getAsInt() : 1);
        assertEquals(expected, results(recipe), file);
    }

    @Test
    void backpackChainIsExact() throws IOException {
        String sb = "sophisticatedbackpacks:";
        assertStep("sophisticated_emerald_upgrade/emerald_backpack.json", "sophisticated_emerald_upgrade:emerald_backpack",
                Map.of(sb + "netherite_backpack", 1, "minecraft:emerald_block", 1, "sophisticated_emerald_upgrade:emerald_upgrade_template", 1));
        assertStep("sophisticatedbackpacks/netherite_backpack.json", sb + "netherite_backpack",
                Map.of(sb + "diamond_backpack", 1, "minecraft:netherite_ingot", 1));
        assertStep("sophisticatedbackpacks/diamond_backpack.json", sb + "diamond_backpack",
                Map.of(sb + "gold_backpack", 1, "minecraft:gold_ingot", 8));
        assertStep("sophisticatedbackpacks/gold_backpack.json", sb + "gold_backpack",
                Map.of(sb + "iron_backpack", 1, "minecraft:gold_ingot", 8));
        assertStep("sophisticatedbackpacks/iron_backpack.json", sb + "iron_backpack",
                Map.of(sb + "copper_backpack", 1, "minecraft:iron_ingot", 4));
        assertStep("sophisticatedbackpacks/copper_backpack.json", sb + "copper_backpack",
                Map.of(sb + "backpack", 1, "minecraft:copper_ingot", 8));
        assertStep("sophisticatedbackpacks/backpack.json", sb + "backpack",
                Map.of("minecraft:chest", 1, "minecraft:leather", 4, "minecraft:string", 4));
        assertTrue(requiredMods(read(FULL_UNCRAFT + "/sophisticated_emerald_upgrade/emerald_backpack.json")).contains("sophisticated_emerald_upgrade"));
    }

    @Test
    void upgradeRecipeIsTheHopperPattern() throws IOException {
        JsonObject recipe = read("data/disassembly_delight/recipe/disassembler_upgrade.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals(List.of(" T ", "HUH", "RRR"), List.of(pattern.get(0).getAsString(), pattern.get(1).getAsString(), pattern.get(2).getAsString()));
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("disassembly_delight:disassembler", key.getAsJsonObject("T").get("item").getAsString());
        assertEquals("minecraft:hopper", key.getAsJsonObject("H").get("item").getAsString());
        assertEquals("sophisticatedbackpacks:upgrade_base", key.getAsJsonObject("U").get("item").getAsString());
        assertEquals("minecraft:redstone", key.getAsJsonObject("R").get("item").getAsString());
        assertEquals(4, key.size(), "no leather or other leftover keys");
        assertEquals("disassembly_delight:disassembler_upgrade", recipe.getAsJsonObject("result").get("id").getAsString());
        assertTrue(requiredMods(recipe).contains("sophisticatedbackpacks"));
    }

    @Test
    void upgradeBreaksDownIntoItsRecipe() throws IOException {
        assertStep("disassembler_upgrade.json", "disassembly_delight:disassembler_upgrade",
                Map.of("disassembly_delight:disassembler", 1, "minecraft:hopper", 2, "sophisticatedbackpacks:upgrade_base", 1, "minecraft:redstone", 3));
    }

    @Test
    void noBreakdownForTheTableAndNoAbsentModResults() throws IOException {
        List<String> problems = new ArrayList<>();
        Path root = resources().resolve(FULL_UNCRAFT);
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject recipe;
                try (Reader reader = Files.newBufferedReader(file)) {
                    recipe = JsonParser.parseReader(reader).getAsJsonObject();
                }
                String name = root.relativize(file).toString();
                JsonObject input = recipe.getAsJsonObject("input");
                if (input != null && input.has("item") && "disassembly_delight:disassembler".equals(input.get("item").getAsString())) {
                    problems.add(name + ": the Disassembly Table must not have a breakdown");
                }
                Set<String> allowed = new HashSet<>(requiredMods(recipe));
                allowed.add("minecraft");
                allowed.add("disassembly_delight");
                allowed.add("farmersdelight"); // a required dependency
                for (String id : results(recipe).keySet()) {
                    String namespace = id.substring(0, id.indexOf(':'));
                    if (!allowed.contains(namespace)) {
                        problems.add(name + ": result " + id + " needs mod " + namespace + " but the recipe does not require it");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        assertFalse(Files.exists(root.resolve("disassembler.json")));
    }
}
