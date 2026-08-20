/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.protocol.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaInterfaceSource;
import org.yaml.snakeyaml.Yaml;

/**
 * Post-processes openapi-generator output for any schema marked with {@code x-polymorphic-schema:
 * true}. For each such schema the generator produces a fat concrete class merging all oneOf child
 * fields; this tool rewrites:
 *
 * <ol>
 *   <li>The parent class file → a Java interface with Jackson discriminator annotations.
 *   <li>Each child class file → adds {@code implements <ParentInterface>} and the import.
 * </ol>
 *
 * <p>Usage: {@code DiscriminatorModelPostProcessor <specDir> <genDir>}
 *
 * <p>{@code specDir} — path to the YAML spec directory (zeebe/gateway-protocol/src/main/proto/v2).
 * {@code genDir} — path to the generated REST package directory (io/camunda/client/protocol/rest).
 */
public final class DiscriminatorModelPostProcessor {

  private static final String JSON_IGNORE_PROPS =
      "com.fasterxml.jackson.annotation.JsonIgnoreProperties";
  private static final String JSON_TYPE_INFO =
      "com.fasterxml.jackson.annotation.JsonTypeInfo";
  private static final String JSON_SUB_TYPES =
      "com.fasterxml.jackson.annotation.JsonSubTypes";

  public static void main(final String[] args) throws IOException {
    if (args.length != 2) {
      throw new IllegalArgumentException(
          "Usage: DiscriminatorModelPostProcessor <specDir> <genDir>");
    }
    final Path specDir = Path.of(args[0]);
    final Path genDir = Path.of(args[1]);
    new DiscriminatorModelPostProcessor().run(specDir, genDir);
  }

  @SuppressWarnings("unchecked")
  void run(final Path specDir, final Path genDir) throws IOException {
    final Yaml yaml = new Yaml();
    final Map<String, Map<String, Object>> allSchemas = new LinkedHashMap<>();

    try (final Stream<Path> files = Files.list(specDir)) {
      files
          .filter(p -> p.toString().endsWith(".yaml"))
          .sorted()
          .forEach(
              p -> {
                try {
                  final Map<String, Object> doc = yaml.load(Files.readString(p));
                  if (doc == null) {
                    return;
                  }
                  final Map<String, Object> components =
                      (Map<String, Object>) doc.getOrDefault("components", Map.of());
                  final Map<String, Object> schemas =
                      (Map<String, Object>) components.getOrDefault("schemas", Map.of());
                  schemas.forEach(
                      (name, schema) -> {
                        if (schema instanceof Map) {
                          allSchemas.put(name, (Map<String, Object>) schema);
                        }
                      });
                } catch (final IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }

    int processed = 0;
    for (final Map.Entry<String, Map<String, Object>> entry : allSchemas.entrySet()) {
      final String schemaName = entry.getKey();
      final Map<String, Object> schema = entry.getValue();

      if (!Boolean.TRUE.equals(schema.get("x-polymorphic-schema"))) {
        continue;
      }

      final Object discriminatorObj = schema.get("discriminator");
      if (!(discriminatorObj instanceof Map<?, ?> discriminator)) {
        System.err.printf(
            "[DiscriminatorModelPostProcessor] WARNING: %s has x-polymorphic-schema but no discriminator, skipping%n",
            schemaName);
        continue;
      }

      final String propName = (String) discriminator.get("propertyName");
      final Object mappingObj = discriminator.get("mapping");
      if (!(mappingObj instanceof Map<?, ?> rawMapping)) {
        System.err.printf(
            "[DiscriminatorModelPostProcessor] WARNING: %s discriminator has no mapping, skipping%n",
            schemaName);
        continue;
      }
      final Map<String, String> mapping = new LinkedHashMap<>();
      rawMapping.forEach((k, v) -> mapping.put((String) k, (String) v));

      final boolean rewrote = rewriteParentAsInterface(schemaName, propName, mapping, genDir);
      if (rewrote) {
        for (final String ref : mapping.values()) {
          final String childName = refName(ref);
          addImplementsToChild(childName, schemaName, genDir);
        }
        processed++;
      }
    }

    System.out.printf(
        "[DiscriminatorModelPostProcessor] processed %d polymorphic schema(s) in %s%n",
        processed, genDir);
  }

  private boolean rewriteParentAsInterface(
      final String parentName,
      final String propName,
      final Map<String, String> mapping,
      final Path genDir)
      throws IOException {
    final Path parentFile = genDir.resolve(parentName + ".java");
    if (!Files.exists(parentFile)) {
      // The openapi generator may have mapped this schema to another type via typeMappings;
      // skip silently rather than failing.
      System.out.printf(
          "[DiscriminatorModelPostProcessor] skipping %s (generated file not found, likely handled by typeMappings)%n",
          parentName);
      return false;
    }

    final String original = Files.readString(parentFile);

    // Idempotency: if already an interface (from a prior run), skip.
    if (original.contains("public interface " + parentName)) {
      System.out.printf(
          "[DiscriminatorModelPostProcessor] %s is already an interface, skipping%n", parentName);
      return false;
    }

    final String licenseHeader = extractLicenseHeader(original);

    // Derive package from the existing file
    final JavaClassSource existing = Roaster.parse(JavaClassSource.class, original);
    final String pkg = existing.getPackage();

    final JavaInterfaceSource iface =
        Roaster.create(JavaInterfaceSource.class).setPackage(pkg).setName(parentName);

    iface.addImport("com.fasterxml.jackson.annotation.JsonIgnoreProperties");
    iface.addImport("com.fasterxml.jackson.annotation.JsonSubTypes");
    iface.addImport("com.fasterxml.jackson.annotation.JsonTypeInfo");

    iface
        .addAnnotation(JSON_IGNORE_PROPS)
        .setStringValue("value", propName)
        .setLiteralValue("allowSetters", "true");

    iface
        .addAnnotation(JSON_TYPE_INFO)
        .setLiteralValue("use", "JsonTypeInfo.Id.NAME")
        .setLiteralValue("include", "JsonTypeInfo.As.PROPERTY")
        .setStringValue("property", propName)
        .setLiteralValue("visible", "true");

    final StringBuilder subTypes = new StringBuilder("{");
    int i = 0;
    for (final Map.Entry<String, String> e : mapping.entrySet()) {
      if (i++ > 0) {
        subTypes.append(", ");
      }
      final String childClass = refName(e.getValue());
      subTypes
          .append("@JsonSubTypes.Type(value = ")
          .append(childClass)
          .append(".class, name = \"")
          .append(escapeJava(e.getKey()))
          .append("\")");
    }
    subTypes.append("}");
    iface.addAnnotation(JSON_SUB_TYPES).setLiteralValue(subTypes.toString());

    iface
        .addMethod()
        .setPublic()
        .setReturnType(String.class)
        .setName("get" + capitalize(propName))
        .setBody(null);

    final String content = licenseHeader + iface;
    Files.writeString(parentFile, content);
    System.out.printf(
        "[DiscriminatorModelPostProcessor] rewrote %s as interface%n", parentName);
    return true;
  }

  private void addImplementsToChild(
      final String childName, final String parentName, final Path genDir) throws IOException {
    final Path childFile = genDir.resolve(childName + ".java");
    if (!Files.exists(childFile)) {
      System.err.printf(
          "[DiscriminatorModelPostProcessor] WARNING: child file not found: %s, skipping%n",
          childFile);
      return;
    }

    final String original = Files.readString(childFile);

    // Idempotency: skip if child already implements the interface.
    if (original.contains("implements " + parentName)) {
      System.out.printf(
          "[DiscriminatorModelPostProcessor] %s already implements %s, skipping%n",
          childName, parentName);
      return;
    }

    final String licenseHeader = extractLicenseHeader(original);

    final JavaClassSource cls = Roaster.parse(JavaClassSource.class, original);
    final String pkg = cls.getPackage();

    cls.addInterface(pkg + "." + parentName);

    final String content = licenseHeader + cls;
    Files.writeString(childFile, content);
    System.out.printf(
        "[DiscriminatorModelPostProcessor] added implements %s to %s%n", parentName, childName);
  }

  /**
   * Extracts the leading block comment (license header) from the generated file verbatim. The
   * generated files start with a {@code /* ... *\/} block — we preserve it rather than injecting
   * the Apache-2 or Camunda header, because the generated header text differs.
   */
  private static String extractLicenseHeader(final String source) {
    final String trimmed = source.stripLeading();
    if (!trimmed.startsWith("/*")) {
      return "";
    }
    final int end = trimmed.indexOf("*/");
    if (end < 0) {
      return "";
    }
    return trimmed.substring(0, end + 2) + "\n\n";
  }

  private static String refName(final String ref) {
    if (ref == null) {
      return null;
    }
    final int idx = ref.lastIndexOf('/');
    return idx >= 0 ? ref.substring(idx + 1) : ref;
  }

  private static String capitalize(final String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String escapeJava(final String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
