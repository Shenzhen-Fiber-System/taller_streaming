package com.ourshop.streaming.streams.service;

import com.ourshop.streaming.streams.model.StreamMeta;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class StreamMetaSpecifications {

    private StreamMetaSpecifications() {
    }

    public static Predicate<StreamMeta> search(String search, List<String> fields) {
        if (search == null || search.isBlank()) {
            return meta -> true;
        }

        String needle = search.toLowerCase(Locale.ROOT);
        Set<String> normalizedFields = (fields == null ? List.<String>of() : fields).stream()
                .filter(f -> f != null && !f.isBlank())
                .map(f -> f.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        boolean searchTitle = normalizedFields.isEmpty() || normalizedFields.contains("title");
        boolean searchDescription = normalizedFields.isEmpty() || normalizedFields.contains("description");
        boolean searchStreamKey = normalizedFields.contains("streamkey") || normalizedFields.contains("stream_key") || normalizedFields.contains("key");

        return meta -> {
            if (meta == null) {
                return false;
            }

            if (searchTitle && containsIgnoreCase(meta.title(), needle)) {
                return true;
            }
            if (searchDescription && containsIgnoreCase(meta.description(), needle)) {
                return true;
            }
            if (searchStreamKey && containsIgnoreCase(meta.streamKey(), needle)) {
                return true;
            }

            return false;
        };
    }

    private static boolean containsIgnoreCase(String haystack, String needleLower) {
        if (haystack == null || haystack.isBlank()) {
            return false;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }
}
