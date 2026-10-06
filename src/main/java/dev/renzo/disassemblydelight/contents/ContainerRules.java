package dev.renzo.disassemblydelight.contents;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Rule C (container emptying) data, read from {@code /disassembly_delight/container_rules.json} on the classpath.
 * The same file is checked by {@code scripts/check_container_coverage.py}.
 */
public final class ContainerRules {
    public static final String RESOURCE = "/disassembly_delight/container_rules.json";

    /** Reader kinds the Java side implements. The check script and tests fail on anything else. */
    public static final Set<String> KNOWN_READER_KINDS = Set.of(
            "item_container", "bundle", "charged_projectiles", "item_handler", "single_stack", "stack_list", "item_id_list");

    private static volatile ContainerRules instance;

    public final Map<String, String> readers;
    public final Map<String, String> customDataReaders;
    public final Set<String> ghostComponents;
    public final Set<String> passThroughIfPresent;
    public final Set<String> passThroughItems;
    public final List<String> contentsOnlyTags;
    public final Map<String, String> items;

    private ContainerRules(JsonObject json) {
        readers = stringMap(json, "readers");
        customDataReaders = stringMap(json, "custom_data_readers");
        ghostComponents = stringSet(json, "ghost_components");
        passThroughIfPresent = stringSet(json, "pass_through_if_present");
        passThroughItems = stringSet(json, "pass_through_items");
        contentsOnlyTags = List.copyOf(stringSet(json, "contents_only_tags"));
        items = stringMap(json, "items");
    }

    public static ContainerRules get() {
        ContainerRules rules = instance;
        if (rules == null) {
            synchronized (ContainerRules.class) {
                rules = instance;
                if (rules == null) {
                    rules = load();
                    instance = rules;
                }
            }
        }
        return rules;
    }

    static ContainerRules load() {
        try (InputStream in = ContainerRules.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read " + RESOURCE, e);
        }
    }

    public static ContainerRules parse(JsonObject json) {
        ContainerRules rules = new ContainerRules(json);
        List<String> problems = rules.problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Bad " + RESOURCE + ": " + String.join("; ", problems));
        }
        return rules;
    }

    /** Reader kind without its argument: {@code stack_list:items} gives {@code stack_list}. */
    public static String kindOf(String reader) {
        int colon = reader.indexOf(':');
        return colon < 0 ? reader : reader.substring(0, colon);
    }

    /** Reader argument: {@code stack_list:items} gives {@code items}, no argument gives null. */
    public static String argOf(String reader) {
        int colon = reader.indexOf(':');
        return colon < 0 ? null : reader.substring(colon + 1);
    }

    /** Consistency problems: unknown reader kinds, ids in two lists at once. */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : readers.entrySet()) {
            if (!KNOWN_READER_KINDS.contains(kindOf(entry.getValue()))) {
                out.add("unknown reader kind " + entry.getValue() + " for " + entry.getKey());
            }
            if (ghostComponents.contains(entry.getKey()) || passThroughIfPresent.contains(entry.getKey())) {
                out.add(entry.getKey() + " is both read and ignored/passed");
            }
        }
        for (String ghost : ghostComponents) {
            if (passThroughIfPresent.contains(ghost)) {
                out.add(ghost + " is both ghost and pass-through");
            }
        }
        for (String id : concat(readers.keySet(), customDataReaders.keySet(), ghostComponents, passThroughIfPresent, passThroughItems)) {
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                out.add("bad id " + id);
            }
        }
        return out;
    }

    @SafeVarargs
    private static List<String> concat(java.util.Collection<String>... parts) {
        List<String> all = new ArrayList<>();
        for (java.util.Collection<String> part : parts) {
            all.addAll(part);
        }
        return all;
    }

    private static Map<String, String> stringMap(JsonObject json, String key) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json.has(key) && json.get(key).isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject(key).entrySet()) {
                out.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Set<String> stringSet(JsonObject json, String key) {
        Set<String> out = new LinkedHashSet<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray array = json.getAsJsonArray(key);
            for (JsonElement element : array) {
                out.add(element.getAsString());
            }
        }
        return Collections.unmodifiableSet(out);
    }
}
