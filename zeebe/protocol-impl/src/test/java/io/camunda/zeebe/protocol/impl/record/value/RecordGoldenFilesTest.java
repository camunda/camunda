/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class RecordGoldenFilesTest {

  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(RecordGoldenFilesTest.class);

  private static final Path RECORDS_FOLDER =
      Paths.get("src", "main", "java", "io", "camunda", "zeebe", "protocol", "impl", "record");

  private static final Path GOLDEN_FILES_FOLDER =
      Paths.get("src", "test", "resources", "protocol", "records", "golden");

  // Templates for patterns that require the record/field name to be injected
  private static final String RECORD_COMPONENTS_TEMPLATE = "record\\s+%s\\s*\\(([^)]*)\\)";
  private static final String NULL_CHECK_TEMPLATE = "if\\s*\\(\\s*%s\\s*==\\s*null\\s*\\)";

  // FIELD_DECLARATION_PATTERN matches declarations such as:
  //     private final List<String> names = new ArrayList<>();
  //     private final int count;
  //     private final SomeType<?>[] values = new SomeType[0];
  //     private final @Nullable Map<String, Integer> map;
  private static final Pattern FIELD_DECLARATION_PATTERN =
      Pattern.compile(
          "private\\s+final\\s+([\\w<>,?\\s.\\[\\]@]+?)\\s+(\\w+)\\s*(=\\s*[^;]+)?;",
          Pattern.DOTALL);

  // PROPERTY_CTOR_PATTERN matches constructor expressions such as:
  //     new StringProperty("name")
  //     new LongProperty(initialValues, 0)
  private static final Pattern PROPERTY_CTOR_PATTERN =
      Pattern.compile("new\\s+([\\w.]+Property)(?:\\s*<[^>]*>)?\\s*\\((.*?)\\)", Pattern.DOTALL);

  // Pattern to match individual top-level constructor arguments. It matches quoted strings,
  // simple angle-bracket generics, single-level parenthesis groups, or other tokens, each
  // followed by an optional comma or end of input. This allows counting top-level args.
  private static final Pattern CTOR_ARG_PATTERN =
      Pattern.compile(
          "\\s*(?:\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'|<[^<>]*>|\\([^()]*\\)|[^,'\"()<>]+)\\s*(?:,|$)",
          Pattern.DOTALL);

  // --------------------------------------------------------------------------
  // Parameterized Test
  // --------------------------------------------------------------------------

  @ParameterizedTest
  @MethodSource("recordSources")
  void recordsShouldMatchGoldenFiles(final RegisteredRecord record) throws IOException {
    final Path goldenFile = GOLDEN_FILES_FOLDER.resolve(record.simpleName() + ".golden");

    assertGoldenFileExists(record, goldenFile);

    final String goldenContent = Files.readString(goldenFile);

    final String sourceContent = Files.readString(record.sourcePath());
    final Map<String, Property> goldenProps = extractProperties(goldenContent, record.simpleName());
    final Map<String, Property> sourceProps = extractProperties(sourceContent, record.simpleName());

    validateGoldenProperties(goldenProps, sourceProps);
    validateNewProperties(record, goldenProps, sourceProps, goldenFile);
  }

  // --------------------------------------------------------------------------
  // Test Data Sources
  // --------------------------------------------------------------------------

  private static Stream<RegisteredRecord> recordSources() throws IOException {
    if (!Files.exists(RECORDS_FOLDER)) {
      LOG.warn("Expected record sources at {}", RECORDS_FOLDER.toAbsolutePath());
      return Stream.empty();
    }

    try (final var files = Files.walk(RECORDS_FOLDER)) {
      return files
          .filter(path -> path.toString().endsWith(".java"))
          .map(path -> new RegisteredRecord(path, stripExtension(path.getFileName().toString())))
          .sorted(Comparator.comparing(RegisteredRecord::simpleName))
          .toList()
          .stream();
    }
  }

  // --------------------------------------------------------------------------
  // Assertions
  // --------------------------------------------------------------------------

  private static void assertGoldenFileExists(final RegisteredRecord record, final Path goldenFile) {
    if (!Files.exists(goldenFile)) {
      LOG.error("Golden file for {} does not exist.", record.simpleName());
      warnAboutGoldenFileChanges();
      printGoldenFileCommand(record.sourcePath(), goldenFile);
    }
    assertThat(goldenFile.toFile())
        .describedAs("Expected golden file for %s", record.simpleName())
        .exists();
  }

  private static void validateGoldenProperties(
      final Map<String, Property> goldenProps, final Map<String, Property> sourceProps) {

    for (final var golden : goldenProps.values()) {
      if (!sourceProps.containsKey(golden.name())) {
        warnAboutGoldenFileChanges();
        fail("Expected property %s from golden file to exist in source", golden.name());
      }
      final var source = sourceProps.get(golden.name());
      if (!source.type().equals(golden.type())) {
        warnAboutGoldenFileChanges();
        fail(
            "Property %s type changed (golden: %s, source: %s)",
            golden.name(), golden.type(), source.type());
      }
    }
  }

  private static void validateNewProperties(
      final RegisteredRecord record,
      final Map<String, Property> goldenProps,
      final Map<String, Property> sourceProps,
      final Path goldenFile) {

    for (final var source : sourceProps.values()) {
      if (!goldenProps.containsKey(source.name())) {
        LOG.error("New property {} on {} detected.", source.name(), record.simpleName());
        warnAboutGoldenFileChanges();
        printGoldenFileCommand(record.sourcePath(), goldenFile);

        if (!source.hasDefault) {
          fail(
              "New property %s on %s does not have a default value. To maintain backward compatibility, either add a default value or remove the property."
                  .formatted(source.name(), record.simpleName()));
        }
        fail(
            "New property %s on %s detected. Update the golden file %s to reflect this change."
                .formatted(source.name(), record.simpleName(), goldenFile));
      }
    }
  }

  // --------------------------------------------------------------------------
  // Property Extraction
  // --------------------------------------------------------------------------

  private static Map<String, Property> extractProperties(
      final String javaSource, final String recordName) {
    final var properties = new LinkedHashMap<String, Property>();
    properties.putAll(parseRecordComponents(javaSource, recordName));
    properties.putAll(parseFieldDeclarations(javaSource, properties));
    return Collections.unmodifiableMap(properties);
  }

  private static Map<String, Property> parseRecordComponents(final String src, final String name) {
    final var props = new LinkedHashMap<String, Property>();
    final var pattern =
        Pattern.compile(
            String.format(RECORD_COMPONENTS_TEMPLATE, Pattern.quote(name)), Pattern.DOTALL);
    final var matcher = pattern.matcher(src);

    if (matcher.find()) {
      for (final String part : matcher.group(1).split(",")) {
        final var tokens = part.trim().split("\\s+");
        if (tokens.length >= 2) {
          final var fieldName = tokens[tokens.length - 1];
          final var fieldType = String.join(" ", Arrays.copyOf(tokens, tokens.length - 1));
          final var hasDefault =
              hasDefaultFor(src, fieldName) || containsFieldInitializer(src, fieldName);
          props.put(fieldName, new Property(fieldName, fieldType, hasDefault));
        }
      }
    }
    return props;
  }

  private static Map<String, Property> parseFieldDeclarations(
      final String src, final Map<String, Property> existing) {

    final var props = new LinkedHashMap<String, Property>();
    final var matcher = FIELD_DECLARATION_PATTERN.matcher(src);
    while (matcher.find()) {
      final var type = matcher.group(1).trim();
      final var name = matcher.group(2).trim();
      final var initGroup = matcher.group(3);

      final var hasInit = initGroup != null;
      final var hasDefault =
          (hasInit && initializerImpliesDefault(initGroup)) || hasDefaultFor(src, name);

      final var existingProp = existing.get(name);
      props.put(
          name,
          new Property(
              name,
              type.isEmpty() && existingProp != null ? existingProp.type() : type,
              (existingProp != null && existingProp.hasDefault()) || hasDefault));
    }
    return props;
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private static boolean initializerImpliesDefault(final String initGroup) {
    final var rhs = initGroup.replaceFirst("^=\\s*", "").trim();
    final var matcher = PROPERTY_CTOR_PATTERN.matcher(rhs);

    if (matcher.matches()) {
      final var args = matcher.group(2);
      if (args == null || args.trim().isEmpty()) {
        return false;
      }

      final var argMatcher = CTOR_ARG_PATTERN.matcher(args);
      int count = 0;
      while (argMatcher.find()) {
        final var token = argMatcher.group().trim();
        if (!token.isEmpty()) {
          count++;
          if (count >= 2) {
            return true;
          }
        }
      }
      return false;
    }
    return true; // Fallback: non-Property initializers imply default
  }

  private static boolean hasDefaultFor(final String src, final String name) {
    final var assignPattern =
        Pattern.compile("this\\." + Pattern.quote(name) + "\\s*=\\s*([^;]+);", Pattern.DOTALL);
    final var matcher = assignPattern.matcher(src);
    while (matcher.find()) {
      if (!matcher.group(1).trim().equals(name)) {
        return true;
      }
    }

    if (Pattern.compile(String.format(NULL_CHECK_TEMPLATE, Pattern.quote(name)), Pattern.DOTALL)
        .matcher(src)
        .find()) {
      return true;
    }

    return src.contains("Objects.requireNonNullElse(") || src.contains("Optional.ofNullable(");
  }

  private static boolean containsFieldInitializer(final String src, final String name) {
    return Pattern.compile("\\b" + Pattern.quote(name) + "\\s*=\\s*[^;]+;", Pattern.DOTALL)
        .matcher(src)
        .find();
  }

  private static void warnAboutGoldenFileChanges() {
    LOG.warn(
        """
        ====================================================================
        Record classes are part of the protocol and must remain compatible across versions.
        Golden files exist to make changes explicit and to ensure backward compatibility is
        carefully reviewed before being accepted.

        Incompatible changes can break replay, interoperability between cluster
        members, or upgrades. Golden files help reviewers detect unintended protocol
        modifications and force deliberate updates.

        If this test fails, consider the following scenarios:

        1) You added a new property:
           - Add a safe default value so older versions can continue to operate.

        2) You removed or changed a property:
           - Removing or changing properties is a breaking protocol change and must not be done
           without an explicit compatibility plan. Properties in records may only be modified
           if they have not yet been released. For backward compatibility, always review carefully
           to ensure the property does not exist in any released version before making changes.

        3) Golden file is missing for a new record:
           - Create the golden file after careful review.

        To update the golden file, use the command printed by the test logs (see
        printGoldenFileCommand) to copy or create the golden file. Always double-check
        the change and add appropriate justification in the PR.
        """);
  }

  private static void printGoldenFileCommand(final Path sourcePath, final Path goldenFilePath) {
    if (Files.exists(sourcePath)) {
      LOG.info(
          "To update golden file:\ncp {} {}",
          sourcePath.toAbsolutePath(),
          goldenFilePath.toAbsolutePath());
    } else {
      LOG.info("To create empty golden file:\necho -n > {}", goldenFilePath.toAbsolutePath());
    }
  }

  private static String stripExtension(final String filename) {
    return filename.replaceAll("\\.java$", "");
  }

  // --------------------------------------------------------------------------
  // Internal Data Models
  // --------------------------------------------------------------------------

  private record Property(String name, String type, boolean hasDefault) {}

  private record RegisteredRecord(Path sourcePath, String simpleName) {}
}
