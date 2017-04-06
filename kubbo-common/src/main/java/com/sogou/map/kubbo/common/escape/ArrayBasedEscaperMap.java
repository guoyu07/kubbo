package com.sogou.map.kubbo.common.escape;

import java.util.Collections;
import java.util.Map;

/**
 * An implementation-specific parameter class suitable for initializing
 * {@link ArrayBasedCharEscaper} instances.
 * This class should be used when more than one escaper is created using the
 * same character replacement mapping to allow the underlying (implementation
 * specific) data structures to be shared.
 *
 * <p>
 * The size of the data structure used by ArrayBasedCharEscaper and
 * ArrayBasedUnicodeEscaper is proportional to the highest valued character that
 * has a replacement. For example a replacement map containing the single
 * character '{@literal \}u1000' will require approximately 16K of memory. As
 * such sharing this data structure between escaper instances is the primary
 * goal of this class.
 */
public final class ArrayBasedEscaperMap {
    // Immutable empty array for when there are no replacements.
    private static final char[][] EMPTY_REPLACEMENT_ARRAY = new char[0][0];
    
    // The underlying replacement array we can share between multiple escaper instances.
    private final char[][] replacementArray;

    public static ArrayBasedEscaperMap create(Map<Character, String> replacements) {
        return new ArrayBasedEscaperMap(createReplacementArray(replacements));
    }


    private ArrayBasedEscaperMap(char[][] replacementArray) {
        this.replacementArray = replacementArray;
    }

    // Returns the non-null array of replacements for fast lookup.
    char[][] getReplacementArray() {
        return replacementArray;
    }

    // Creates a replacement array from the given map. The returned array is a
    // linear lookup table of replacement character sequences indexed by the
    // original character value.
    static char[][] createReplacementArray(Map<Character, String> map) {
        if (map == null) {
            throw new IllegalArgumentException("map == NULL");
        }
        if (map.isEmpty()) {
            return EMPTY_REPLACEMENT_ARRAY;
        }
        char max = Collections.max(map.keySet());
        char[][] replacements = new char[max + 1][];
        for (char c : map.keySet()) {
            replacements[c] = map.get(c).toCharArray();
        }
        return replacements;
    }


}
