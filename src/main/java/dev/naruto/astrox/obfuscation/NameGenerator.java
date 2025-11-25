package dev.naruto.astrox.obfuscation;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/**
 * Generates random but valid Java identifiers
 * Ensures no collisions within a single injection
 */
public class NameGenerator {
    private final SecureRandom random;
    private final Set<String> usedNames;
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public NameGenerator() {
        this.random = new SecureRandom();
        this.usedNames = new HashSet<>();
    }

    /**
     * Generate random class name
     */
    public String generateClassName() {
        String name;
        do {
            name = generateName(2, 4, true);
        } while (usedNames.contains(name) || isReservedKeyword(name));

        usedNames.add(name);
        return name;
    }

    /**
     * Generate random method name
     */
    public String generateMethodName() {
        String name;
        do {
            name = generateName(1, 3, false);
        } while (usedNames.contains(name) || isReservedKeyword(name));

        usedNames.add(name);
        return name;
    }

    /**
     * Generate random field name
     */
    public String generateFieldName() {
        return generateMethodName(); // Same rules
    }

    /**
     * Generate random identifier
     * @param minLength Minimum length
     * @param maxLength Maximum length
     * @param capitalizeFirst Whether to capitalize first letter
     */
    private String generateName(int minLength, int maxLength, boolean capitalizeFirst) {
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            char c = ALPHABET.charAt(random.nextInt(ALPHABET.length()));

            if (i == 0 && capitalizeFirst) {
                c = Character.toUpperCase(c);
            } else if (i == 0) {
                c = Character.toLowerCase(c);
            }

            sb.append(c);
        }

        return sb.toString();
    }

    /**
     * Check if name conflicts with Java keywords
     */
    private boolean isReservedKeyword(String name) {
        String lower = name.toLowerCase();
        String[] keywords = {
                "abstract", "assert", "boolean", "break", "byte", "case", "catch",
                "char", "class", "const", "continue", "default", "do", "double",
                "else", "enum", "extends", "final", "finally", "float", "for",
                "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "package", "private",
                "protected", "public", "return", "short", "static", "strictfp",
                "super", "switch", "synchronized", "this", "throw", "throws",
                "transient", "try", "void", "volatile", "while"
        };

        for (String keyword : keywords) {
            if (keyword.equals(lower)) return true;
        }

        return false;
    }
}
