/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.out;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.apache.commons.text.WordUtils;

/**
 * Generates a file from Spring Boot configuration metadata. This class reads the
 * spring-configuration-metadata.json file and converts it to a YAML format with comments containing
 * descriptions, types, and environment variable names.
 *
 * <p>Used by the dist module to generate `dist/src/main/config/defaults.yaml`
 */
@SuppressWarnings("JvmTaintAnalysis")
public class GenerateDefaults {

  private static final Pattern HTML_LINK = Pattern.compile("<a\\s+href=\"([^\"]+)\">[^<]+</a>");
  private static final Pattern HTML_TAG = Pattern.compile("<[^<]+?>");
  private static final Pattern JAVADOC_CODE = Pattern.compile("\\{@code\\s+([^}]+)}");
  private static final int MAX_LINE_WIDTH = 100;

  public static void main(final String[] args) {
    try {
      final var config = parseArguments(args);
      validateInputFile(config.inputPath());

      if (config.verifyMode()) {
        verify(config.inputPath().toFile(), config.outputPath().toFile());
      } else {
        generate(config.inputPath().toFile(), config.outputPath().toFile());
      }
    } catch (final Exception e) {
      err.println("Error: " + e);
      exit(1);
    }
  }

  private static CommandConfig parseArguments(final String[] args) {
    if (args.length < 2) {
      throw new IllegalArgumentException(
          "Usage: GenerateDefaults <input-json-path> <output-yaml-path> [--verify]");
    }

    return new CommandConfig(
        Path.of(args[0]), Path.of(args[1]), args.length > 2 && "--verify".equals(args[2]));
  }

  private static void validateInputFile(final Path inputPath) {
    if (!Files.exists(inputPath)) {
      throw new IllegalArgumentException("Input file not found: " + inputPath);
    }
  }

  public static void verify(final File inputFile, final File outputFile) throws IOException {
    if (!outputFile.exists()) {
      throw new IllegalStateException(
          """
              Output file does not exist: %s
              Please run 'mvn generate-resources' to generate the file."""
              .formatted(outputFile.getAbsolutePath()));
    }

    final var metadata = readMetadata(inputFile);
    final var expected = generateYamlContent(metadata);
    final var actual = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);

    if (!expected.equals(actual)) {
      throw new IllegalStateException(
          """
              Verification failed: %s is outdated!

              The generated defaults.yaml does not match the current configuration metadata.
              Please run 'mvn exec:java@generate-default-config -pl dist/' to update it."""
              .formatted(outputFile.getAbsolutePath()));
    }

    out.println("✓ Verification successful: " + outputFile.getAbsolutePath() + " is up to date.");
  }

  public static void generate(final File inputFile, final File outputFile) throws IOException {
    final var metadata = readMetadata(inputFile);
    final var yamlContent = generateYamlContent(metadata);
    Files.createDirectories(outputFile.toPath().getParent());
    Files.writeString(outputFile.toPath(), yamlContent, StandardCharsets.UTF_8);

    out.println("Successfully generated " + outputFile.getAbsolutePath());
  }

  private static ConfigurationMetadata readMetadata(final File inputFile) throws IOException {
    return new ObjectMapper().readValue(inputFile, ConfigurationMetadata.class);
  }

  private static String generateYamlContent(final ConfigurationMetadata metadata) {
    final var yamlData = buildYamlData(metadata);
    return renderYaml(yamlData);
  }

  private static YamlContent buildYamlData(final ConfigurationMetadata metadata) {
    final Map<String, PropertyMetadata> metadataMap = new TreeMap<>();

    // Build metadata map from groups and properties
    for (final var g : metadata.groups()) {
      metadataMap.put(g.name(), new PropertyMetadata(g.type(), g.description(), null));
    }

    for (final var p : metadata.properties()) {
      if (!p.deprecated()) {
        metadataMap.put(
            p.name(), new PropertyMetadata(p.type(), p.description(), toEnvVarName(p.name())));
      }
    }

    // Build nested structure from properties
    final TreeMap<String, Object> structure = new TreeMap<>();
    for (final var property : metadata.properties()) {
      if (!property.deprecated()) {
        addToNestedMap(structure, property.name(), property.defaultValue());
      }
    }

    return new YamlContent(structure, metadataMap);
  }

  private static void addToNestedMap(
      final Map<String, Object> map, final String path, final Object value) {
    final var parts = path.split("\\.");
    var current = map;

    for (var i = 0; i < parts.length - 1; i++) {
      final var part = parts[i];
      //noinspection unchecked
      current = (Map<String, Object>) current.computeIfAbsent(part, k -> new TreeMap<>());
    }

    current.put(parts[parts.length - 1], value);
  }

  private static String toEnvVarName(final String propertyName) {
    return propertyName.replace(".", "_").replace("-", "").toUpperCase();
  }

  private static String renderYaml(final YamlContent yamlData) {
    return """
        # This file is AUTO-GENERATED from Spring Boot configuration metadata.
        #
        # To update this file:
        #   mvn exec:java@generate-default-config -pl dist/
        #
        # To verify this file is up to date:
        #   mvn exec:java@verify-default-config -pl dist/
        #
        # DO NOT edit this file manually. Changes will be overwritten.
        """
        + renderYamlMap(yamlData.structure(), "", yamlData.metadata());
  }

  private static String renderYamlMap(
      final Map<String, Object> entries,
      final String path,
      final Map<String, PropertyMetadata> metadata) {

    final StringBuilder sb = new StringBuilder();
    for (final var entry : entries.entrySet()) {
      sb.append("\n");
      sb.append(renderYamlEntry(entry.getKey(), entry.getValue(), path, metadata));
    }
    return sb.toString();
  }

  private static String renderYamlEntry(
      final String key,
      final Object value,
      final String path,
      final Map<String, PropertyMetadata> metadataMap) {
    final var currentPath = buildPropertyPath(path, key);
    final var metadata = metadataMap.get(currentPath);

    //noinspection unchecked,rawtypes
    return value instanceof final Map map
        ? renderNestedValue(key, map, metadata, currentPath, metadataMap)
        : renderLeafValue(key, value, metadata);
  }

  private static String buildPropertyPath(final String parentPath, final String key) {
    return parentPath.isEmpty() ? key : parentPath + "." + key;
  }

  private static String renderDescriptionComment(final PropertyMetadata metadata) {
    if (metadata == null || metadata.description() == null || metadata.description().isBlank()) {
      return "";
    }
    return renderComment(cleanHtml(metadata.description()));
  }

  private static String renderNestedValue(
      final String key,
      final Map<String, Object> map,
      final PropertyMetadata metadata,
      final String currentPath,
      final Map<String, PropertyMetadata> metadataMap) {
    return "%s%s: # Type: %s%s"
        .formatted(
            renderDescriptionComment(metadata),
            key,
            metadata != null ? formatType(metadata.type()) : "",
            renderYamlMap(map, currentPath, metadataMap).indent(2));
  }

  private static String renderLeafValue(
      final String key, final Object value, final PropertyMetadata metadata) {

    return "%s%s: %s # Type: %s, Env: %s"
        .formatted(
            renderDescriptionComment(metadata),
            key,
            formatValue(value),
            formatType(metadata.type()),
            metadata.envVar());
  }

  private static String formatValue(final Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String) {
      return "\"" + value + "\"";
    }
    return value.toString();
  }

  private static String formatType(final String type) {
    return type.replace("java.util.", "").replace("java.lang.", "").replace("java.time.", "");
  }

  private static String cleanHtml(final String text) {
    return stripHtmlTags(replaceParagraphTags(replaceHtmlLinks(replaceJavadocCode(text))));
  }

  private static String replaceJavadocCode(final String text) {
    return JAVADOC_CODE.matcher(text).replaceAll("`$1`");
  }

  private static String replaceHtmlLinks(final String text) {
    return HTML_LINK.matcher(text).replaceAll("$1");
  }

  private static String replaceParagraphTags(final String text) {
    return text.replace("<p>", "\n").replace("</p>", "\n");
  }

  private static String stripHtmlTags(final String text) {
    return HTML_TAG.matcher(text).replaceAll("");
  }

  private static String renderComment(final String text) {
    final var prefix = "# ";
    final var result = new StringBuilder();
    for (final var line : text.lines().toList()) {
      final var wrappedLine = WordUtils.wrap(line.strip(), MAX_LINE_WIDTH, "\n" + prefix, true);
      result.append(prefix).append(wrappedLine).append("\n");
    }
    return result.toString();
  }

  // Command line configuration
  private record CommandConfig(Path inputPath, Path outputPath, boolean verifyMode) {}

  // POJOs for Spring Boot configuration metadata structure
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ConfigurationMetadata(List<PropertyGroup> groups, List<PropertyItem> properties) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PropertyGroup(String name, String type, String description) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PropertyItem(
      String name, String type, String description, Object defaultValue, boolean deprecated) {}

  // Internal representation for YAML generation
  private record PropertyMetadata(String type, String description, String envVar) {}

  private record YamlContent(
      Map<String, Object> structure, Map<String, PropertyMetadata> metadata) {}
}
