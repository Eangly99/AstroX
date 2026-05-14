package dev.naruto.astrox.payload.c2;

import java.util.List;

/**
 * C2 protocol data structures.
 * Manual JSON parsing — no Jackson dependency (payload-side code).
 */
public class C2Protocol {

    /**
     * A command received from the C2 server.
     */
    public record C2Command(String id, String module, List<String> args) {

        /**
         * Parse a single command from a JSON object string.
         * Expected format: {"id":"uuid","module":"rce","args":["cmd"]}
         */
        public static C2Command fromJson(String json) {
            String id = extractField(json, "id");
            String module = extractField(json, "module");
            List<String> args = extractArray(json, "args");
            return new C2Command(id, module, args);
        }
    }

    /**
     * A response sent back to the C2 server.
     */
    public record C2Response(String commandId, String status, String output) {

        /**
         * Serialize to JSON string.
         */
        public String toJson() {
            return String.format(
                    "{\"commandId\":\"%s\",\"status\":\"%s\",\"output\":\"%s\"}",
                    escapeJson(commandId),
                    escapeJson(status),
                    escapeJson(output)
            );
        }
    }

    /**
     * Parse a JSON array of commands from the C2 poll response.
     * Expected format: [{"id":"...","module":"...","args":["..."]}, ...]
     */
    public static List<C2Command> parseCommandQueue(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return List.of();
        }

        // Simple JSON array parser — split by },{
        java.util.List<C2Command> commands = new java.util.ArrayList<>();

        // Remove outer brackets
        String inner = json.strip();
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);

        if (inner.isBlank()) return List.of();

        // Split objects (handle nested arrays in args)
        int depth = 0;
        int start = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    String objStr = inner.substring(start, i + 1).strip();
                    if (!objStr.isEmpty()) {
                        commands.add(C2Command.fromJson(objStr));
                    }
                    start = i + 1;
                    // Skip comma
                    while (start < inner.length() && (inner.charAt(start) == ',' || inner.charAt(start) == ' ')) {
                        start++;
                    }
                }
            }
        }

        return commands;
    }

    // ==================== Simple JSON utilities (no external deps) ====================

    static String extractField(String json, String fieldName) {
        String search = "\"" + fieldName + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return "";

        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx < 0) return "";

        // Find the value start
        int valStart = colonIdx + 1;
        while (valStart < json.length() && json.charAt(valStart) == ' ') valStart++;

        if (valStart >= json.length()) return "";

        if (json.charAt(valStart) == '"') {
            // String value
            int valEnd = json.indexOf('"', valStart + 1);
            if (valEnd < 0) return "";
            return json.substring(valStart + 1, valEnd);
        }

        // Non-string value (number, boolean)
        int valEnd = valStart;
        while (valEnd < json.length() && json.charAt(valEnd) != ',' && json.charAt(valEnd) != '}') {
            valEnd++;
        }
        return json.substring(valStart, valEnd).strip();
    }

    static List<String> extractArray(String json, String fieldName) {
        String search = "\"" + fieldName + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return List.of();

        int bracketStart = json.indexOf('[', idx);
        if (bracketStart < 0) return List.of();

        int bracketEnd = json.indexOf(']', bracketStart);
        if (bracketEnd < 0) return List.of();

        String arrayContent = json.substring(bracketStart + 1, bracketEnd).strip();
        if (arrayContent.isEmpty()) return List.of();

        java.util.List<String> result = new java.util.ArrayList<>();
        for (String item : arrayContent.split(",")) {
            String trimmed = item.strip();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            result.add(trimmed);
        }
        return result;
    }

    static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
