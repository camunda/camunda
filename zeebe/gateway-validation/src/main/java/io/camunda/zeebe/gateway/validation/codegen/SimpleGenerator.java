/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.validation.codegen;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Temporary simple generator: finds the first schema component that uses oneOf and emits a
 * GeneratedGroupDescriptorProvider class under target/generated-sources/domain-oneof mirroring the
 * runtime placeholder. This proves the end-to-end generation pipeline before implementing the full
 * DomainSpecAnalyzer.
 */
public final class SimpleGenerator {

  public static void main(final String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Expected args: <domainSpecPath> <outputDir>");
    }
    final Path spec = Path.of(args[0]);
    final Path outDir = Path.of(args[1]);
    final OpenAPI api = new OpenAPIV3Parser().read(spec.toString());
    if (api == null || api.getComponents() == null || api.getComponents().getSchemas() == null) {
      System.out.println("[generator] No schemas found in spec; skipping generation.");
      return;
    }

    final var groups =
        api.getComponents().getSchemas().entrySet().stream()
            .filter(
                e ->
                    e.getValue() instanceof ComposedSchema cs
                        && cs.getOneOf() != null
                        && !cs.getOneOf().isEmpty())
            .toList();
    if (groups.isEmpty()) {
      System.out.println("[generator] No oneOf groups found; skipping generation.");
      return;
    }
    final String specHash = hash(Files.readAllBytes(spec));
    final Path pkgDir = outDir.resolve("io/camunda/zeebe/gateway/validation/generated");
    Files.createDirectories(pkgDir);
    final Path file = pkgDir.resolve("DomainGeneratedGroupDescriptorProvider.java");
    try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
      pw.println("package io.camunda.zeebe.gateway.validation.generated;");
      pw.println();
      pw.println("import io.camunda.zeebe.gateway.validation.model.BranchDescriptor;");
      pw.println("import io.camunda.zeebe.gateway.validation.model.GroupDescriptor;");
      pw.println("import io.camunda.zeebe.gateway.validation.spi.GroupDescriptorProvider;");
  pw.println("import io.camunda.zeebe.gateway.validation.model.EnumLiteral;");
  pw.println("import io.camunda.zeebe.gateway.validation.model.PatternDescriptor;");
  pw.println("import java.util.regex.Pattern;");
      pw.println();
      pw.println(
          "/** Generated from rest-api.domain.yaml (hash: "
              + specHash
              + ") by SimpleGenerator. */");
      pw.println(
          "public final class DomainGeneratedGroupDescriptorProvider implements GroupDescriptorProvider {");
      // Pre-generate descriptors
      for (var e : groups) {
        final var name = e.getKey();
        final var cs = (ComposedSchema) e.getValue();
    pw.println(
      "  private static final GroupDescriptor GD_"
        + sanitize(name)
        + " = new GroupDescriptor(\""
        + name
        + "\", new BranchDescriptor[] {" + buildBranches(cs) + "});");
      }
      pw.println();
      pw.println("  @Override public GroupDescriptor find(String groupId) {");
      for (var e : groups) {
        final var name = e.getKey();
        pw.println("    if (\"" + name + "\".equals(groupId)) return GD_" + sanitize(name) + ";");
      }
      pw.println("    return null;");
      pw.println("  }");
      pw.println("}");
    }
    System.out.println("[generator] Wrote provider with " + groups.size() + " groups to " + file);
  }

  private static String hash(final byte[] content) throws NoSuchAlgorithmException {
    final MessageDigest md = MessageDigest.getInstance("SHA-256");
    final byte[] d = md.digest(content);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(d).substring(0, 22);
  }

  private static String buildBranches(final ComposedSchema cs) {
    if (cs.getOneOf() == null || cs.getOneOf().isEmpty()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    int idx = 0;
    for (Schema<?> branchSchema : cs.getOneOf()) {
      if (idx > 0) sb.append(',');
      // Resolve $ref inline if present
      branchSchema = resolveRef(branchSchema);
      final java.util.Set<String> required = new java.util.LinkedHashSet<>();
      if (branchSchema.getRequired() != null) {
        required.addAll(branchSchema.getRequired());
      }
      final java.util.Set<String> allProps = new java.util.LinkedHashSet<>();
      if (branchSchema.getProperties() != null) {
        allProps.addAll(branchSchema.getProperties().keySet());
      }
      final java.util.List<String> optional = new java.util.ArrayList<>();
      for (String p : allProps) {
        if (!required.contains(p)) optional.add(p);
      }
      final int specificity = required.size();
      // Build enum literals per property (required first then optional)
      final java.util.List<String> propertiesOrdered = new java.util.ArrayList<>(required);
      propertiesOrdered.addAll(optional);
      final String enumMatrix = buildEnumMatrix(branchSchema, propertiesOrdered);
      final String patternsArray = buildPatternsArray(branchSchema, propertiesOrdered);
      sb.append(" new BranchDescriptor(")
          .append(idx)
          .append(',')
          .append(specificity)
          .append(',')
          .append(stringArrayLiteral(required))
          .append(',')
          .append(stringArrayLiteral(optional))
          .append(',')
          .append(enumMatrix)
          .append(',')
          .append(patternsArray)
          .append(')');
      idx++;
    }
    return sb.toString();
  }

  private static String buildEnumMatrix(
      final Schema<?> branchSchema, final java.util.List<String> propertiesOrdered) {
    if (branchSchema.getProperties() == null || propertiesOrdered.isEmpty()) {
      return "new EnumLiteral[0][]";
    }
    final StringBuilder sb = new StringBuilder("new EnumLiteral[][]{");
    boolean firstProp = true;
    for (String prop : propertiesOrdered) {
      @SuppressWarnings("unchecked")
      final Schema<Object> ps = (Schema<Object>) branchSchema.getProperties().get(prop);
      if (!firstProp) sb.append(',');
      if (ps != null && ps.getEnum() != null && !ps.getEnum().isEmpty()) {
        sb.append("new EnumLiteral[]{");
        boolean firstEnum = true;
        for (Object ev : ps.getEnum()) {
          if (!firstEnum) sb.append(',');
            sb.append("new EnumLiteral(\"").append(escape(ev.toString())).append("\")");
          firstEnum = false;
        }
        sb.append('}');
      } else {
        sb.append("new EnumLiteral[0]");
      }
      firstProp = false;
    }
    sb.append('}');
    return sb.toString();
  }

  private static String buildPatternsArray(
      final Schema<?> branchSchema, final java.util.List<String> propertiesOrdered) {
    if (branchSchema.getProperties() == null || propertiesOrdered.isEmpty()) {
      return "new PatternDescriptor[0]";
    }
    final java.util.List<String> patternEntries = new java.util.ArrayList<>();
    for (String prop : propertiesOrdered) {
      @SuppressWarnings("unchecked")
      final Schema<Object> ps = (Schema<Object>) branchSchema.getProperties().get(prop);
      if (ps != null && ps.getPattern() != null && !ps.getPattern().isEmpty()) {
        patternEntries.add(
            "new PatternDescriptor(\"" + escape(prop) + "\", Pattern.compile(\"" + escapePattern(ps.getPattern()) + "\"))");
      }
    }
    if (patternEntries.isEmpty()) {
      return "new PatternDescriptor[0]";
    }
    final StringBuilder sb = new StringBuilder("new PatternDescriptor[]{");
    for (int i = 0; i < patternEntries.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append(patternEntries.get(i));
    }
    sb.append('}');
    return sb.toString();
  }

  private static Schema<?> resolveRef(final Schema<?> schema) {
    // SimpleGenerator scope: swagger parser already resolves refs into components map entries
    // when using OpenAPIV3Parser.read, so we just return the schema as-is.
    return schema;
  }

  private static String stringArrayLiteral(java.util.Collection<String> values) {
    if (values.isEmpty()) return "new String[0]";
    final StringBuilder sb = new StringBuilder("new String[]{");
    boolean first = true;
    for (String v : values) {
      if (!first) sb.append(',');
      sb.append('\"').append(escape(v)).append('\"');
      first = false;
    }
    sb.append('}');
    return sb.toString();
  }

  private static String escape(String v) {
    return v.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String escapePattern(String v) {
    // Keep backslashes for regex semantics while escaping for Java string literal
    return escape(v);
  }

  private static String sanitize(String name) {
    return name.replaceAll("[^A-Za-z0-9_]", "_");
  }
}
