///usr/bin/env java --enable-preview --source 21 "$0" "$@" ; exit $?

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.stream.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * Test File Inventory Script
 *
 * Inventories all test files in the Camunda monorepo, mapping each to its codeowner and test type.
 *
 * Usage:
 *   java scripts/TestFileInventory.java [--verify] [--help]
 *
 * Options:
 *   --verify    Cross-check classification against Maven's actual plugin resolution
 *   --help      Show this help message
 *
 * Output format (plain text, one line per test file):
 *   <relative-path> | <owner(s)> | <test-type>
 *
 * Test Types:
 *   - acceptance: Tests under qa/acceptance-tests/
 *   - architecture: Tests under qa/archunit-tests/
 *   - load-test: Tests under load-tests/ or zeebe/load-tests/ or zeebe/benchmarks/
 *   - benchmark: Tests under microbenchmarks/
 *   - e2e: Tests under e2e-playwright or e2e directories
 *   - integration: IT.java files or tests in qa, zeebe/qa, operate/qa, or tasklist/qa modules
 *   - unit: Standard *Test.java files in non-qa modules
 *   - frontend-unit: *.test.ts, *.test.js, *.test.tsx
 *   - frontend-spec: *.spec.ts, *.spec.js
 *   - other: Fallback for unclassified test files
 *
 * Dependencies:
 *   - codeowners-cli from codeowners-plus (https://github.com/multimediallc/codeowners-plus)
 *     Install: npm install -g codeowners-plus
 */
public class TestFileInventory {

    private static final Path REPO_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final String CODEOWNERS_FILE = ".codeowners";

    // Patterns for Java test files (Maven surefire/failsafe defaults)
    private static final Pattern SUREFIRE_PATTERN = Pattern.compile(".*(?:Test|Tests|TestCase)\\.java$|^Test.*\\.java$");
    private static final Pattern FAILSAFE_PATTERN = Pattern.compile(".*(?:IT|ITCase)\\.java$|^IT.*\\.java$");

    // Patterns for frontend test files
    private static final Pattern FRONTEND_UNIT_PATTERN = Pattern.compile(".*\\.test\\.(ts|js|tsx)$");
    private static final Pattern FRONTEND_SPEC_PATTERN = Pattern.compile(".*\\.spec\\.(ts|js)$");

    // Excluded directories
    private static final Set<String> EXCLUDED_DIRS = Set.of(
        "target", "node_modules", ".git", "dist", "build", ".next"
    );

    public static void main(String[] args) throws Exception {
        boolean verifyMode = Arrays.asList(args).contains("--verify");
        boolean helpMode = Arrays.asList(args).contains("--help");

        if (helpMode) {
            printHelp();
            return;
        }

        // Check for codeowners-cli availability
        if (!isCodeownersCliAvailable()) {
            System.err.println("ERROR: codeowners-cli not found in PATH");
            System.err.println("Install with: npm install -g codeowners-plus");
            System.err.println("See: https://github.com/multimediallc/codeowners-plus");
            System.exit(1);
        }

        // Discover all test files
        List<Path> testFiles = discoverTestFiles(REPO_ROOT);

        System.err.println("Found " + testFiles.size() + " test files");

        if (verifyMode) {
            System.err.println("\n=== VERIFY MODE ===");
            System.err.println("Cross-checking classification against Maven...");
            verifyClassification(testFiles);
        } else {
            // Output inventory
            for (Path testFile : testFiles) {
                String relativePath = REPO_ROOT.relativize(testFile).toString();
                String testType = classifyTestFile(testFile);
                String owners = getCodeowners(relativePath);

                System.out.println(relativePath + " | " + owners + " | " + testType);
            }
        }
    }

    private static void printHelp() {
        System.out.println("Test File Inventory Script");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java scripts/TestFileInventory.java [--verify] [--help]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --verify    Cross-check classification against Maven's actual plugin resolution");
        System.out.println("  --help      Show this help message");
        System.out.println();
        System.out.println("Output format:");
        System.out.println("  <relative-path> | <owner(s)> | <test-type>");
        System.out.println();
        System.out.println("Test types:");
        System.out.println("  acceptance     Tests under qa/acceptance-tests/");
        System.out.println("  architecture   Tests under qa/archunit-tests/");
        System.out.println("  load-test      Tests under load-tests/ or zeebe/load-tests/ or zeebe/benchmarks/");
        System.out.println("  benchmark      Tests under microbenchmarks/");
        System.out.println("  e2e            Tests under */e2e-playwright/ or */e2e/");
        System.out.println("  integration    *IT.java files or tests in qa/*/zeebe/qa/*/operate/qa/*/tasklist/qa/*");
        System.out.println("  unit           Standard *Test.java files in non-qa modules");
        System.out.println("  frontend-unit  *.test.ts, *.test.js, *.test.tsx");
        System.out.println("  frontend-spec  *.spec.ts, *.spec.js");
        System.out.println("  other          Fallback for unclassified test files");
    }

    private static boolean isCodeownersCliAvailable() {
        try {
            Process process = new ProcessBuilder("codeowners-cli", "--version")
                .redirectErrorStream(true)
                .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<Path> discoverTestFiles(Path root) throws IOException {
        List<Path> testFiles = new ArrayList<>();

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName().toString();
                if (EXCLUDED_DIRS.contains(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isTestFile(file)) {
                    testFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // Sort for consistent output
        testFiles.sort(Comparator.naturalOrder());

        return testFiles;
    }

    private static boolean isTestFile(Path file) {
        String fileName = file.getFileName().toString();
        String pathStr = file.toString();

        // Skip abstract base classes
        if (fileName.startsWith("Abstract")) {
            return false;
        }

        // Java test files must be under src/test/java/ or known test directories
        if (fileName.endsWith(".java")) {
            return (pathStr.contains("/src/test/java/") ||
                    pathStr.contains("/qa/") ||
                    pathStr.contains("/load-tests/") ||
                    pathStr.contains("/benchmarks/")) &&
                   (SUREFIRE_PATTERN.matcher(fileName).matches() ||
                    FAILSAFE_PATTERN.matcher(fileName).matches());
        }

        // Frontend test files
        return FRONTEND_UNIT_PATTERN.matcher(fileName).matches() ||
               FRONTEND_SPEC_PATTERN.matcher(fileName).matches();
    }

    /**
     * Classify test file based on location and naming convention.
     * Follows the decision tree from the issue description.
     */
    private static String classifyTestFile(Path file) {
        String pathStr = REPO_ROOT.relativize(file).toString();
        String fileName = file.getFileName().toString();

        // 1. LOCATION-BASED OVERRIDES (highest priority)
        if (pathStr.startsWith("qa/acceptance-tests/")) {
            return "acceptance";
        }
        if (pathStr.startsWith("qa/archunit-tests/")) {
            return "architecture";
        }
        if (pathStr.contains("/load-tests/") ||
            pathStr.startsWith("zeebe/load-tests/") ||
            pathStr.startsWith("zeebe/benchmarks/")) {
            return "load-test";
        }
        if (pathStr.contains("/microbenchmarks/")) {
            return "benchmark";
        }
        if (pathStr.contains("/e2e-playwright/") || pathStr.contains("/e2e/")) {
            return "e2e";
        }

        // 2. QA MODULE DETECTION (surefire skipped, failsafe runs everything)
        // Check if file is under qa/ modules where surefire is skipped
        if (isInQaModule(pathStr)) {
            return "integration";
        }

        // 3. SUFFIX-BASED (standard Maven defaults for non-qa modules)
        if (fileName.endsWith(".java")) {
            if (FAILSAFE_PATTERN.matcher(fileName).matches()) {
                return "integration";
            }
            if (SUREFIRE_PATTERN.matcher(fileName).matches()) {
                return "unit";
            }
        }

        // 4. FRONTEND TEST FILES
        if (FRONTEND_UNIT_PATTERN.matcher(fileName).matches()) {
            return "frontend-unit";
        }
        if (FRONTEND_SPEC_PATTERN.matcher(fileName).matches()) {
            return "frontend-spec";
        }

        // 5. FALLBACK
        return "other";
    }

    /**
     * Check if a path is in a QA module where surefire is skipped.
     */
    private static boolean isInQaModule(String pathStr) {
        // Root qa/ modules (excluding acceptance-tests and archunit-tests which are handled above)
        if (pathStr.startsWith("qa/") &&
            !pathStr.startsWith("qa/acceptance-tests/") &&
            !pathStr.startsWith("qa/archunit-tests/")) {
            return true;
        }

        // zeebe/qa/ modules
        if (pathStr.startsWith("zeebe/qa/")) {
            return true;
        }

        // operate/qa/ modules
        if (pathStr.startsWith("operate/qa/")) {
            return true;
        }

        // tasklist/qa/ modules
        if (pathStr.startsWith("tasklist/qa/")) {
            return true;
        }

        return false;
    }

    /**
     * Get codeowners for a file by delegating to codeowners-cli.
     */
    private static String getCodeowners(String relativePath) {
        try {
            Process process = new ProcessBuilder(
                "codeowners-cli", "owner",
                "-f", "one-line",
                "-r", REPO_ROOT.toString(),
                relativePath
            )
            .directory(REPO_ROOT.toFile())
            .redirectErrorStream(true)
            .start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return "[error: codeowners-cli failed]";
            }

            // Parse output - in one-line format, output is just: owner1 owner2 ...
            // or error message if no owners found
            String trimmed = output.trim();
            if (trimmed.isEmpty() || trimmed.contains("no owners") || trimmed.contains("Error:")) {
                return "[no-owner]";
            }

            // Output is space-separated list of owners (e.g., "@camunda/identity @camunda/qa-engineering")
            return Arrays.stream(trimmed.split("\\s+"))
                .filter(s -> !s.isEmpty() && s.startsWith("@"))
                .collect(Collectors.joining(", "));

        } catch (Exception e) {
            return "[error: " + e.getMessage() + "]";
        }
    }

    /**
     * Verify mode: cross-check heuristic classification against Maven's actual resolution.
     */
    private static void verifyClassification(List<Path> testFiles) throws Exception {
        System.err.println("\nVerify mode is not yet fully implemented.");
        System.err.println("This would run Maven with -X to capture plugin resolution and compare.");
        System.err.println("\nFor now, showing classification for sample files:\n");

        // Show sample classifications
        int count = 0;
        for (Path testFile : testFiles) {
            if (count++ >= 20) break;

            String relativePath = REPO_ROOT.relativize(testFile).toString();
            String testType = classifyTestFile(testFile);
            System.err.println(relativePath + " -> " + testType);
        }

        System.err.println("\n... (" + (testFiles.size() - 20) + " more files)");

        // TODO: Implement Maven verification
        // 1. Discover all Maven modules from pom.xml
        // 2. For each module with test sources, run:
        //    mvn -pl <module> surefire:test failsafe:integration-test -X -DskipTests
        // 3. Parse debug output for [DEBUG] Selecting test class: <fqcn>
        // 4. Compare surefire-selected vs failsafe-selected vs our classification
        // 5. Report mismatches
    }
}
