package dev.renzo.disassemblydelight.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The copper Disassembly Table model: four facings, every texture present, no textures borrowed from other mods. */
class BlockAssetTest {
    private static final String ASSETS = "assets/disassembly_delight";

    private static Path resources() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("src/main/resources");
            if (Files.isDirectory(candidate.resolve(ASSETS))) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("src/main/resources not found");
    }

    private static JsonObject read(String path) throws IOException {
        try (Reader reader = Files.newBufferedReader(resources().resolve(path))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void blockstateRotatesTheModelForEachFacing() throws IOException {
        JsonObject variants = read(ASSETS + "/blockstates/disassembler.json").getAsJsonObject("variants");
        Map<String, Integer> expected = Map.of("facing=north", 0, "facing=east", 90, "facing=south", 180, "facing=west", 270);
        assertEquals(expected.keySet(), variants.keySet());
        expected.forEach((key, y) -> {
            JsonObject v = variants.getAsJsonObject(key);
            assertEquals("disassembly_delight:block/disassembler", v.get("model").getAsString());
            assertEquals(y, v.has("y") ? v.get("y").getAsInt() : 0, key);
        });
    }

    @Test
    void modelTexturesExistAndComeOnlyFromThisModOrVanilla() throws IOException {
        JsonObject model = read(ASSETS + "/models/block/disassembler.json");
        assertEquals("minecraft:cutout", model.get("render_type").getAsString());
        JsonObject textures = model.getAsJsonObject("textures");
        for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
            String ref = e.getValue().getAsString();
            String ns = ref.substring(0, ref.indexOf(':'));
            assertTrue(Set.of("minecraft", "disassembly_delight").contains(ns), "texture from another mod: " + ref);
            if (ns.equals("disassembly_delight")) {
                Path png = resources().resolve(ASSETS + "/textures/" + ref.substring(ns.length() + 1) + ".png");
                assertTrue(Files.isRegularFile(png), "missing texture " + png);
            }
        }
        for (JsonElement el : model.getAsJsonArray("elements")) {
            for (Map.Entry<String, JsonElement> face : el.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                String tex = face.getValue().getAsJsonObject().get("texture").getAsString();
                assertTrue(textures.has(tex.substring(1)), "undefined texture variable " + tex);
            }
        }
        assertFalse(Files.exists(resources().resolve(ASSETS + "/textures/block/disassembler_side.png")), "old textures removed");
    }
}
