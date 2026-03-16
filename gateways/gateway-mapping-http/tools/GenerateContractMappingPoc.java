/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Java source-file generator for contract mapping POC.
 *
 * <p>Run via: java tools/GenerateContractMappingPoc.java
 */
public class GenerateContractMappingPoc {

  private static final Path ROOT = Path.of("").toAbsolutePath();
  private static final Path SPEC_DIR =
      ROOT.resolve("../../zeebe/gateway-protocol/src/main/proto/v2").normalize();
  private static final Path OUT_BASE = ROOT.resolve("src/main/java");
  private static final String TARGET_PACKAGE = "io.camunda.gateway.mapping.http.search.contract.generated";
  private static final String PROTOCOL_PACKAGE = "io.camunda.gateway.protocol.model";
  private static final Path PROTOCOL_MODEL_SOURCE_DIR =
      ROOT.resolve("../gateway-model/target/generated-sources/openapi/src/main/io/camunda/gateway/protocol/model")
          .normalize();
  private static final Set<String> AVAILABLE_PROTOCOL_TYPES = discoverProtocolModelTypes();

  public static void main(String[] args) throws Exception {
    if (!Files.isDirectory(SPEC_DIR)) {
      throw new IllegalStateException("OpenAPI spec directory does not exist: " + SPEC_DIR);
    }

    final var allSchemas = loadSchemas(SPEC_DIR);
    final var responseOnlySchemas = discoverResponseOnlySchemas(SPEC_DIR, allSchemas);
    final var contractSchemas =
        allSchemas.values().stream()
            .filter(GenerateContractMappingPoc::isContractSchema)
            .sorted(Comparator.comparing(SchemaDef::schemaName))
            .toList();

    if (contractSchemas.isEmpty()) {
      throw new IllegalStateException("No contract schemas found in " + SPEC_DIR);
    }

    final var packagePath = OUT_BASE.resolve(TARGET_PACKAGE.replace('.', '/'));
    Files.createDirectories(packagePath);
    cleanupPreviouslyGeneratedFiles(packagePath);

    // Phase 1: compute fields for all schemas and determine which get mappers.
    record SchemaGenPlan(
        SchemaDef schema,
        String dtoClass,
        String mapperClass,
        List<ContractField> fields,
        boolean generateMapper) {}

    final var mappableSchemaNames = new LinkedHashSet<String>();
    final var plans = new ArrayList<SchemaGenPlan>();

    // First pass: identify schemas that are mapper candidates on name/protocol type alone.
    for (var schema : contractSchemas) {
      final var fields = toContractFields(schema, allSchemas);
      final var dtoClass = dtoClassName(schema.schemaName());
      final var mapperClass = mapperClassName(schema.schemaName());
      final boolean candidate = shouldGenerateMapper(schema.schemaName(), responseOnlySchemas);
      plans.add(new SchemaGenPlan(schema, dtoClass, mapperClass, fields, candidate));
      if (candidate) {
        mappableSchemaNames.add(schema.schemaName());
      }
    }

    // Build a lookup from schema name to its plan for expansion.
    final var planByName = new LinkedHashMap<String, SchemaGenPlan>();
    for (var plan : plans) {
      planByName.put(plan.schema().schemaName(), plan);
    }

    // Expand mapper candidates: include nested types transitively referenced by candidates.
    // This ensures schemas like BrokerInfo, StatusMetric, etc. also get mappers.
    {
      boolean expanded = true;
      while (expanded) {
        expanded = false;
        for (var name : List.copyOf(mappableSchemaNames)) {
          final var plan = planByName.get(name);
          if (plan == null) {
            continue;
          }
          for (var f : plan.fields()) {
            final var nestedName = extractNestedSchemaName(f);
            if (nestedName != null
                && !mappableSchemaNames.contains(nestedName)
                && AVAILABLE_PROTOCOL_TYPES.contains(nestedName)
                && planByName.containsKey(nestedName)) {
              mappableSchemaNames.add(nestedName);
              expanded = true;
            }
          }
        }
      }
    }

    // Second pass: iteratively exclude schemas with type-incompatible fields.
    // Repeat until convergence because removing a nested schema may invalidate its parent.
    final var validMappableSchemas = new LinkedHashSet<>(mappableSchemaNames);
    boolean changed = true;
    while (changed) {
      changed = false;
      for (var plan : plans) {
        if (!validMappableSchemas.contains(plan.schema().schemaName())) {
          continue;
        }
        if (hasMapperIncompatibility(plan.fields(), validMappableSchemas)) {
          validMappableSchemas.remove(plan.schema().schemaName());
          changed = true;
        }
      }
    }

    // Phase 2: write files.
    for (var plan : plans) {
      final var dtoFile = packagePath.resolve(plan.dtoClass() + ".java");
      Files.writeString(
          dtoFile,
          renderStrictDto(
              plan.schema().fileName(),
              plan.schema().schemaName(),
              plan.dtoClass(),
              plan.fields()),
          StandardCharsets.UTF_8);
      System.out.println("generated: " + ROOT.relativize(dtoFile));
    }
  }

  /**
   * Detects fields whose strict contract types won't match the protocol model's setter types.
   *
   * <p>Known incompatibilities:
   * <ul>
   *   <li>Nested object/list/map fields referencing types without generated mappers
   * </ul>
   */
  private static boolean hasMapperIncompatibility(
      List<ContractField> fields, Set<String> mappableSchemas) {
    for (final var f : fields) {
      // Field-level type incompatibility (referenced enum, URI format, dotted enum name)
      if (f.hasMapperFieldIncompatibility()) {
        return true;
      }
      // Nested strict object type — check if nested mapper will exist
      if (f.hasStrictObjectType()) {
        final var nestedSchemaName =
            extractSchemaNameFromStrictClass(f.typeInfo().strictDtoClass());
        if (!mappableSchemas.contains(nestedSchemaName)) {
          return true;
        }
      }
      // List of strict objects — check if element mapper will exist
      if (f.hasStrictListType()) {
        final var elemType = f.typeInfo().elementType();
        if (elemType != null && elemType.strictDtoClass() != null) {
          final var nestedSchemaName = extractSchemaNameFromStrictClass(elemType.strictDtoClass());
          if (!mappableSchemas.contains(nestedSchemaName)) {
            return true;
          }
        }
      }
      // Map with strict object values — check if value mapper will exist
      if (f.hasStrictMapValueType()) {
        final var valType = f.typeInfo().elementType();
        final var nestedSchemaName = extractSchemaNameFromStrictClass(valType.strictDtoClass());
        if (!mappableSchemas.contains(nestedSchemaName)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean shouldGenerateMapper(String schemaName, Set<String> responseOnlySchemas) {
    if (!AVAILABLE_PROTOCOL_TYPES.contains(schemaName)) {
      return false;
    }
    return responseOnlySchemas.contains(schemaName);
  }

  /**
   * Discovers schemas that appear only in response paths (not in request paths) by tracing $ref
   * chains from the {@code paths:} block in each OpenAPI YAML file.
   *
   * <p>Schemas referenced (directly or transitively) from {@code requestBody:} blocks are excluded.
   * This ensures mappers are only generated for response-side schemas, preventing accidental mapper
   * generation for shared schemas like enums, keys, and filter types.
   */
  private static Set<String> discoverResponseOnlySchemas(
      Path specDir, Map<SchemaKey, SchemaDef> allSchemas) throws IOException {
    final var requestRefs = new LinkedHashSet<SchemaKey>();
    final var responseRefs = new LinkedHashSet<SchemaKey>();

    try (var stream = Files.list(specDir)) {
      final var yamlFiles =
          stream
              .filter(path -> path.getFileName().toString().endsWith(".yaml"))
              .sorted(Comparator.comparing(path -> path.getFileName().toString()))
              .toList();
      for (var file : yamlFiles) {
        collectPathRefs(file, requestRefs, responseRefs);
      }
    }

    // Expand both sets transitively through schema $ref and allOf chains.
    final var requestExpanded = expandSchemaRefs(requestRefs, allSchemas);
    final var responseExpanded = expandSchemaRefs(responseRefs, allSchemas);

    // Response-only = response schemas minus any that also appear in request paths.
    responseExpanded.removeAll(requestExpanded);
    return responseExpanded;
  }

  /**
   * Scans a YAML file's paths block and collects $ref targets from requestBody vs responses
   * sections. Uses indentation tracking to determine which section each $ref belongs to.
   */
  private static void collectPathRefs(
      Path file, Set<SchemaKey> requestRefs, Set<SchemaKey> responseRefs) throws IOException {
    final var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    final var fileName = file.getFileName().toString();

    // State machine: track the innermost relevant section we're in.
    // pathsIndent: indent of "paths:", -1 if not inside paths block.
    int pathsIndent = -1;
    // sectionKind: "request" or "response" when inside requestBody/responses block.
    String sectionKind = null;
    int sectionIndent = -1;

    for (int i = 0; i < lines.size(); i++) {
      final var line = lines.get(i);
      if (isIgnorable(line)) {
        continue;
      }

      final int ind = indent(line);
      final var trimmed = trimmed(line);

      // Detect "paths:" at top level.
      if (ind == 0 && trimmed.equals("paths:")) {
        pathsIndent = 0;
        sectionKind = null;
        continue;
      }

      // Detect "components:" or other top-level block ending paths.
      if (ind == 0 && pathsIndent == 0) {
        pathsIndent = -1;
        sectionKind = null;
        continue;
      }

      if (pathsIndent < 0) {
        continue;
      }

      // If we're inside a section, check if we've left it (indent <= sectionIndent).
      if (sectionKind != null && ind <= sectionIndent) {
        sectionKind = null;
        sectionIndent = -1;
      }

      // Detect requestBody: or responses: sections.
      if (trimmed.startsWith("requestBody:")) {
        sectionKind = "request";
        sectionIndent = ind;
        continue;
      }
      if (trimmed.startsWith("responses:")) {
        sectionKind = "response";
        sectionIndent = ind;
        continue;
      }

      // Collect $ref values within the current section.
      if (sectionKind != null && ind > sectionIndent) {
        final var refValue = extractRef(trimmed);
        if (refValue != null) {
          final var key = toSchemaKey(refValue, fileName);
          if (!key.schemaName().isEmpty()) {
            if ("request".equals(sectionKind)) {
              requestRefs.add(key);
            } else {
              responseRefs.add(key);
            }
          }
        }
      }
    }
  }

  /** Extracts a $ref value from a trimmed YAML line, or returns null if not a $ref line. */
  private static String extractRef(String trimmed) {
    // Handles: $ref: 'foo.yaml#/...'  or  $ref: "foo.yaml#/..."  or  - $ref: ...
    final var normalized = trimmed.startsWith("- ") ? trimmed.substring(2) : trimmed;
    if (normalized.startsWith("$ref:")) {
      return unquote(normalized.substring("$ref:".length()).trim());
    }
    return null;
  }

  /**
   * Expands a set of schema keys transitively by following $ref, allOf, items, properties, and
   * additionalProperties chains in schema definitions.
   */
  private static Set<String> expandSchemaRefs(
      Set<SchemaKey> initialRefs, Map<SchemaKey, SchemaDef> allSchemas) {
    final var expanded = new LinkedHashSet<String>();
    final var queue = new ArrayDeque<SchemaKey>(initialRefs);
    final var visited = new LinkedHashSet<SchemaKey>(initialRefs);

    while (!queue.isEmpty()) {
      final var key = queue.poll();
      expanded.add(key.schemaName());

      final var schema = allSchemas.get(key);
      if (schema == null) {
        continue;
      }

      // Collect all nested refs from this schema's node.
      final var nestedRefs = new ArrayList<SchemaKey>();
      collectNodeRefs(schema.node(), schema.fileName(), nestedRefs);

      for (var nested : nestedRefs) {
        if (visited.add(nested)) {
          queue.add(nested);
        }
      }
    }
    return expanded;
  }

  /** Recursively collects all schema key references from a Node. */
  private static void collectNodeRefs(Node node, String currentFile, List<SchemaKey> refs) {
    if (node == null) {
      return;
    }
    if (node.ref() != null) {
      final var key = toSchemaKey(node.ref(), currentFile);
      if (!key.schemaName().isEmpty()) {
        refs.add(key);
      }
    }
    for (var allOfRef : node.allOfRefs()) {
      final var key = toSchemaKey(allOfRef, currentFile);
      if (!key.schemaName().isEmpty()) {
        refs.add(key);
      }
    }
    if (node.items() != null) {
      collectNodeRefs(node.items(), currentFile, refs);
    }
    if (node.additionalProperties() != null) {
      collectNodeRefs(node.additionalProperties(), currentFile, refs);
    }
    for (var prop : node.properties().values()) {
      collectNodeRefs(prop, currentFile, refs);
    }
  }

  private static boolean isContractSchema(SchemaDef schema) {
    return !schema.node().properties().isEmpty();
  }

  private static boolean isUniqueItemsArray(
      Node node, String currentFile, Map<SchemaKey, SchemaDef> allSchemas) {
    if ("array".equals(node.type()) && node.uniqueItems()) {
      return true;
    }
    if (node.ref() != null) {
      final var key = toSchemaKey(node.ref(), currentFile);
      final var schema = allSchemas.get(key);
      if (schema != null) {
        return "array".equals(schema.node().type()) && schema.node().uniqueItems();
      }
    }
    return false;
  }

  private static String dtoClassName(String schemaName) {
    final var baseName = schemaName.endsWith("Result") ? schemaName.substring(0, schemaName.length() - 6) : schemaName;
    return "Generated" + baseName + "StrictContract";
  }

  private static String mapperClassName(String schemaName) {
    return "Generated" + schemaName + "Mapper";
  }

  private static void cleanupPreviouslyGeneratedFiles(Path packagePath) throws IOException {
    try (var stream = Files.list(packagePath)) {
      for (var path : stream.filter(path -> path.getFileName().toString().endsWith(".java")).toList()) {
        Files.delete(path);
      }
    }
  }

  private static List<ContractField> toContractFields(
      SchemaDef schema, Map<SchemaKey, SchemaDef> allSchemas) {
    final var fields = new ArrayList<ContractField>();
    final var usedIdentifiers = new HashMap<String, Integer>();

    // Flatten allOf: merge base schema properties before overlaying child properties.
    final var mergedProperties = new LinkedHashMap<String, PropertyWithContext>();
    final var mergedRequired = new LinkedHashSet<String>();
    collectAllOfProperties(schema, allSchemas, mergedProperties, mergedRequired, new LinkedHashSet<>());
    for (var entry : schema.node().properties().entrySet()) {
      mergedProperties.put(entry.getKey(), new PropertyWithContext(entry.getValue(), schema.fileName()));
    }
    mergedRequired.addAll(schema.node().required());

    for (var entry : mergedProperties.entrySet()) {
      final var propertyName = entry.getKey();
      final var propCtx = entry.getValue();
      final var node = propCtx.node();
      final var contextFile = propCtx.fileName();
      final var isRequired = mergedRequired.contains(propertyName);
      final var isNullable = node.nullable() || !isRequired;
        final var typeInfo = resolveTypeInfo(node, contextFile, allSchemas, new ArrayDeque<>());
        final var javaType = typeInfo.javaType();
      final var isLongKeyCoercion =
          "String".equals(javaType)
              && isLongKeySemantic(node, contextFile, allSchemas, new ArrayDeque<>());
      final var hasInlineEnum =
          "String".equals(javaType)
              && (!node.enumValues().isEmpty()
                  || ("string".equals(node.type()) && !node.allOfRefs().isEmpty()));
      final var hasUniqueItems =
          isUniqueItemsArray(node, contextFile, allSchemas);
      // Detect field-level mapper incompatibilities:
      // 1. URI format: protocol generates java.net.URI, we have String
      // 2. Dotted property names with inline enum: produces invalid enum class name
      final var hasMapperFieldIncompatibility =
          isUriFormat(node, contextFile, allSchemas)
              || (hasInlineEnum && propertyName.contains("."));
      final var identifier = uniqueIdentifier(toJavaIdentifier(propertyName), usedIdentifiers);
      final var mapperMethod = toJavaMethodName(propertyName);
      fields.add(
          new ContractField(
              propertyName,
              identifier,
              mapperMethod,
              javaType,
              isRequired,
              isNullable,
              isLongKeyCoercion,
              hasInlineEnum,
              hasUniqueItems,
              hasMapperFieldIncompatibility,
              typeInfo));
    }
    return fields;
  }

  /**
   * Recursively collects properties and required fields from allOf base schemas. Base properties are
   * added first so child properties can override them.
   */
  private static void collectAllOfProperties(
      SchemaDef schema,
      Map<SchemaKey, SchemaDef> allSchemas,
      Map<String, PropertyWithContext> properties,
      Set<String> required,
      Set<SchemaKey> visited) {
    for (var ref : schema.node().allOfRefs()) {
      final var key = toSchemaKey(ref, schema.fileName());
      if (!visited.add(key)) {
        continue;
      }
      final var baseSchemaDef = allSchemas.get(key);
      if (baseSchemaDef == null) {
        continue;
      }
      collectAllOfProperties(baseSchemaDef, allSchemas, properties, required, visited);
      for (var entry : baseSchemaDef.node().properties().entrySet()) {
        properties.put(
            entry.getKey(),
            new PropertyWithContext(entry.getValue(), baseSchemaDef.fileName()));
      }
      required.addAll(baseSchemaDef.node().required());
    }
  }

  private static String renderStrictDto(
      String sourceFile, String schemaName, String dtoClass, List<ContractField> fields) {
    final boolean hasNullable = fields.stream().anyMatch(ContractField::nullable);
    final boolean hasRequiredNonNullable = fields.stream().anyMatch(f -> f.required() && !f.nullable());
    final var coercionFields = fields.stream().filter(ContractField::requiresCoercion).toList();
    final boolean hasLongKeyCoercion = !coercionFields.isEmpty();
    final boolean hasListCoercion = fields.stream().anyMatch(ContractField::hasStrictListType);
    final String imports =
      "import com.fasterxml.jackson.annotation.JsonInclude;\n"
        + "import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;\n"
        + (hasLongKeyCoercion ? "import io.camunda.gateway.mapping.http.util.KeyUtil;\n" : "")
        + "import jakarta.annotation.Generated;\n"
        + (hasListCoercion ? "import java.util.ArrayList;\n" : "")
        + (hasRequiredNonNullable ? "import java.util.Objects;\n" : "")
        + "import org.jspecify.annotations.NullMarked;\n"
        + (hasNullable ? "import org.jspecify.annotations.Nullable;\n" : "");

    final String renderedFields =
        fields.stream()
            .map(
                f ->
                    "    "
              + (f.nullable() ? annotateNullable(f.javaType()) : f.javaType())
                        + " "
                        + f.identifier())
            .collect(Collectors.joining(",\n"));

    final String constructorBody;
    if (hasRequiredNonNullable) {
      constructorBody =
          fields.stream()
              .filter(f -> f.required() && !f.nullable())
              .map(
                  f ->
                      "    Objects.requireNonNull("
                      + f.identifier()
                          + ", \""
                      + f.name()
                          + " is required and must not be null\");")
              .collect(Collectors.joining("\n"));
    } else {
      constructorBody = "";
    }

    final String fromProtocolFactory =
      renderFromProtocolFactory(schemaName, dtoClass, fields);
    final String fieldReferences = renderFieldReferences(schemaName, fields);

    final String coercionHelpers =
        coercionFields.stream()
        .map(
          field ->
            field.longKeyCoercion()
              ? renderLongKeyCoercionHelper(field)
              : renderStructuralCoercionHelper(field))
            .collect(Collectors.joining("\n\n"));
    final String builderCode = renderBuilder(schemaName, dtoClass, fields);

    final String recordBody;
    if (constructorBody.isBlank()
      && fieldReferences.isBlank()
        && coercionHelpers.isBlank()
        && fromProtocolFactory.isBlank()
        && builderCode.isBlank()) {
      recordBody = "";
    } else {
      final var sections = new ArrayList<String>();
      if (!constructorBody.isBlank()) {
        sections.add("  public " + dtoClass + " {\n" + constructorBody + "\n  }");
      }
      if (!coercionHelpers.isBlank()) {
        sections.add(coercionHelpers);
      }
      if (!fromProtocolFactory.isBlank()) {
        sections.add(fromProtocolFactory);
      }
      if (!builderCode.isBlank()) {
        sections.add(builderCode);
      }
      if (!fieldReferences.isBlank()) {
        sections.add(fieldReferences);
      }
      recordBody = "\n" + String.join("\n\n", sections) + "\n";
    }

    return """
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/%s#/components/schemas/%s
 */
package %s;

%s

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record %s(
%s
) {
%s
}
"""
    .formatted(
      sourceFile,
      schemaName,
      TARGET_PACKAGE,
      imports,
      dtoClass,
        renderedFields,
      recordBody);
  }

  private static String renderFieldReferences(
      final String schemaName, final List<ContractField> fields) {
    if (fields.isEmpty()) {
      return "";
    }

    final var constants =
        fields.stream()
            .map(
                field ->
                    "    public static final ContractPolicy.FieldRef "
                        + toConstantName(field.identifier())
                        + " = ContractPolicy.field(\""
                        + schemaName
                        + "\", \""
                        + field.name()
                        + "\");")
            .collect(Collectors.joining("\n"));

    return """
  public static final class Fields {
%s

    private Fields() {}
  }
"""
        .formatted(constants);
  }

  private static String renderLongKeyCoercionHelper(final ContractField field) {
    final var methodName = "coerce" + capitalizeIdentifier(field.identifier());
    return """
  public static String %s(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        \"%s must be a String or Number, but was \" + value.getClass().getName());
  }
"""
        .formatted(methodName, field.name());
  }

  private static String renderStructuralCoercionHelper(final ContractField field) {
    final var methodName = "coerce" + capitalizeIdentifier(field.identifier());
    if (field.hasStrictObjectType()) {
      final var strictType = field.typeInfo().strictDtoClass();
      final var protocolType = field.typeInfo().protocolJavaType();
      final var protocolConvertible = field.typeInfo().protocolConvertible();
      final var protocolBranch =
          protocolConvertible
              ? """
    if (value instanceof %s protocolValue) {
      return %s.fromProtocol(protocolValue);
    }
"""
                  .formatted(protocolType, strictType)
              : "";
      final var protocolPart =
          protocolConvertible ? " or " + protocolType : "";
      return """
  public static %s %s(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof %s strictValue) {
      return strictValue;
    }
%s
    throw new IllegalArgumentException(
        \"%s must be a %s%s, but was \" + value.getClass().getName());
  }
"""
          .formatted(
              strictType,
              methodName,
              strictType,
              protocolBranch,
              field.name(),
              strictType,
              protocolPart);
    }

    if (field.hasStrictListType()) {
      final var elementInfo = field.typeInfo().elementType();
      final var strictType = elementInfo.strictDtoClass();
      final var protocolType = elementInfo.protocolJavaType();
      final var protocolConvertible = elementInfo.protocolConvertible();
      final var protocolItemBranch =
          protocolConvertible
              ? """
      } else if (item instanceof %s protocolItem) {
        result.add(%s.fromProtocol(protocolItem));
"""
                  .formatted(protocolType, strictType)
              : "";
      final var listTypeDescription =
          protocolConvertible
              ? strictType + " or " + protocolType
              : strictType;
      return """
  public static java.util.List<%s> %s(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          \"%s must be a List of %s, but was \" + value.getClass().getName());
    }

    final var result = new ArrayList<%s>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof %s strictItem) {
        result.add(strictItem);
%s
      } else {
        throw new IllegalArgumentException(
            \"%s must contain only %s items, but got \"
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }
"""
          .formatted(
              strictType,
              methodName,
              field.name(),
              listTypeDescription,
              strictType,
              strictType,
              protocolItemBranch,
              field.name(),
              listTypeDescription);
    }

    return "";
  }

  private static String renderFromProtocolFactory(
      final String schemaName, final String dtoClass, final List<ContractField> fields) {
    if (!supportsFromProtocolFactory(schemaName)) {
      return "";
    }
    final var protocolType = PROTOCOL_PACKAGE + "." + schemaName;
    final var args =
        fields.stream()
            .map(
                field -> {
                  final var sourceAccess =
                      "source." + protocolGetterName(field.mapperMethod()) + "()";
                  if (field.longKeyCoercion()) {
                    return "coerce"
                        + capitalizeIdentifier(field.identifier())
                        + "("
                        + sourceAccess
                        + ")";
                  }
                  if (field.hasStrictObjectType() || field.hasStrictListType()) {
                    return "coerce"
                        + capitalizeIdentifier(field.identifier())
                        + "("
                        + sourceAccess
                        + ")";
                  }
                  return sourceAccess;
                })
            .collect(Collectors.joining(",\n        "));
    return """
  public static %s fromProtocol(final %s source) {
    if (source == null) {
      return null;
    }
    return new %s(
        %s);
  }
"""
          .formatted(dtoClass, protocolType, dtoClass, args);
  }

  private static String protocolGetterName(final String propertyName) {
    final var capitalized = capitalizeIdentifier(propertyName);
    return "get" + capitalized;
  }

  private static boolean supportsFromProtocolFactory(final String schemaName) {
    return false;
  }

  private static Set<String> discoverProtocolModelTypes() {
    if (!Files.isDirectory(PROTOCOL_MODEL_SOURCE_DIR)) {
      return Set.of();
    }

    try (final var stream = Files.list(PROTOCOL_MODEL_SOURCE_DIR)) {
      return stream
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .map(path -> path.getFileName().toString())
          .map(name -> name.substring(0, name.length() - ".java".length()))
          .collect(Collectors.toCollection(LinkedHashSet::new));
    } catch (final IOException e) {
      throw new IllegalStateException(
          "Failed to discover protocol model types in " + PROTOCOL_MODEL_SOURCE_DIR, e);
    }
  }

  private static String renderBuilder(
      final String schemaName, final String dtoClass, final List<ContractField> fields) {
    if (fields.isEmpty()) {
      return "";
    }

    final boolean policyAwareRequired = supportsPolicyAwareBuilder(schemaName);
    final var requiredFields =
        fields.stream().filter(f -> policyAwareRequired && f.required() && !f.nullable()).toList();
    final var optionalFields =
        fields.stream().filter(f -> !policyAwareRequired || !f.required() || f.nullable()).toList();
    final String optionalStepName = "OptionalStep";

    final var stepInterfaces = new ArrayList<String>();
    for (int i = 0; i < requiredFields.size(); i++) {
      final var current = requiredFields.get(i);
      final var nextStepType = i + 1 < requiredFields.size() ? stepInterfaceName(requiredFields.get(i + 1)) : optionalStepName;
      final var valueType = current.requiresCoercion() ? "Object" : current.javaType();
      stepInterfaces.add(
          "  public interface "
              + stepInterfaceName(current)
              + " {\n"
              + "    "
              + nextStepType
              + " "
              + current.identifier()
              + "(final "
              + valueType
              + " "
              + current.identifier()
              + ");\n"
              + "  }");
    }

    final var optionalMethods = new ArrayList<String>();
    for (final var optionalField : optionalFields) {
      optionalMethods.add(renderOptionalStepMethod(optionalField, optionalStepName, policyAwareRequired));
    }
    optionalMethods.add("    " + dtoClass + " build();");
    final var optionalStepInterface =
        "  public interface "
            + optionalStepName
            + " {\n"
            + String.join("\n\n", optionalMethods)
            + "\n  }";

    final var builderInterfaceTypes = new ArrayList<String>();
    for (final var requiredField : requiredFields) {
      builderInterfaceTypes.add(stepInterfaceName(requiredField));
    }
    builderInterfaceTypes.add(optionalStepName);
    final var builderImplements = " implements " + String.join(", ", builderInterfaceTypes);

    final var requiredSetterImplementations = new ArrayList<String>();
    for (int i = 0; i < requiredFields.size(); i++) {
      final var current = requiredFields.get(i);
      final var nextStepType = i + 1 < requiredFields.size() ? stepInterfaceName(requiredFields.get(i + 1)) : optionalStepName;
      final var valueType = current.requiresCoercion() ? "Object" : current.javaType();
      requiredSetterImplementations.add(
          "    @Override\n"
              + "    public "
              + nextStepType
              + " "
              + current.identifier()
              + "(final "
              + valueType
              + " "
              + current.identifier()
              + ") {\n"
              + "      this."
              + current.identifier()
              + " = "
              + current.identifier()
              + ";\n"
              + "      return this;\n"
              + "    }");
    }

    final var builderFields =
        fields.stream()
        .map(
          f ->
            "    private "
              + (f.requiresCoercion() ? "Object" : f.javaType())
              + " "
              + f.identifier()
              + ";")
            .collect(Collectors.joining("\n"));

    final var builderSetters =
      String.join(
        "\n\n",
        Stream.concat(
            requiredSetterImplementations.stream(),
            optionalFields.stream()
              .map(f -> renderOptionalBuilderSetterImplementation(f, optionalStepName, policyAwareRequired)))
          .toList());

    final var buildArgs =
        fields.stream()
            .map(
                f ->
                    renderBuildArg(f, policyAwareRequired))
            .collect(Collectors.joining(",\n          "));

    final var policyHelpers = "";

    final String builderFactoryType =
        requiredFields.isEmpty() ? optionalStepName : stepInterfaceName(requiredFields.get(0));

    final var interfaces = new ArrayList<String>();
    interfaces.addAll(stepInterfaces);
    interfaces.add(optionalStepInterface);
    final String interfacesSection = String.join("\n\n", interfaces);

    return """
%s
  public static %s builder() {
    return new Builder();
  }

  public static final class Builder%s {
%s

    private Builder() {}

%s
    @Override
    public %s build() {
      return new %s(
          %s);
    }
  }

%s
"""
        .formatted(
            policyHelpers,
            builderFactoryType,
            builderImplements,
            builderFields,
            builderSetters,
            dtoClass,
            dtoClass,
            buildArgs,
            interfacesSection);
  }

  private static String renderOptionalBuilderSetterImplementation(
      final ContractField field, final String optionalStepName, final boolean policyAwareRequired) {
    final var nullableAnnotation = field.nullable() ? "@Nullable " : "";
    if (!field.requiresCoercion()) {
      final var optionalPolicyOverload =
          policyAwareRequired && field.nullable()
              ? """

    @Override
    public %s %s(final %s %s, final ContractPolicy.FieldPolicy<%s> policy) {
      this.%s = policy.apply(%s, Fields.%s, null);
      return this;
    }
"""
                  .formatted(
                      optionalStepName,
                      field.identifier(),
                      annotateNullable(field.javaType()),
                      field.identifier(),
                      field.javaType(),
                      field.identifier(),
                      field.identifier(),
                      toConstantName(field.identifier()))
              : "";

      return ("""
    @Override
    public %s %s(final %s %s) {
      this.%s = %s;
      return this;
    }
"""
          .formatted(
              optionalStepName,
              field.identifier(),
              field.nullable() ? annotateNullable(field.javaType()) : field.javaType(),
              field.identifier(),
              field.identifier(),
              field.identifier()))
          + optionalPolicyOverload;
    }

    final var optionalStringPolicyOverload =
        policyAwareRequired && field.nullable()
            ? """

    public Builder %s(final %s %s, final ContractPolicy.FieldPolicy<%s> policy) {
      this.%s = policy.apply(%s, Fields.%s, null);
      return this;
    }
"""
                .formatted(
                    field.identifier(),
                    annotateNullable(field.javaType()),
                    field.identifier(),
                    field.javaType(),
                    field.identifier(),
                    field.identifier(),
                    toConstantName(field.identifier()))
            : "";

    final var optionalObjectPolicyOverload =
        policyAwareRequired && field.nullable()
            ? """

    @Override
    public %s %s(final @Nullable Object %s, final ContractPolicy.FieldPolicy<Object> policy) {
      this.%s = policy.apply(%s, Fields.%s, null);
      return this;
    }
"""
                .formatted(
            optionalStepName,
                    field.identifier(),
                    field.identifier(),
                    field.identifier(),
                    field.identifier(),
                    toConstantName(field.identifier()))
            : "";

    return ("""
    @Override
    public %s %s(final %s %s) {
      this.%s = %s;
      return this;
    }

    @Override
    public %s %s(final %s %s) {
      this.%s = %s;
      return this;
    }
"""
        .formatted(
        optionalStepName,
            field.identifier(),
            field.nullable() ? annotateNullable(field.javaType()) : field.javaType(),
            field.identifier(),
            field.identifier(),
            field.identifier(),
        optionalStepName,
            field.identifier(),
            field.nullable() ? "@Nullable Object" : "Object",
            field.identifier(),
            field.identifier(),
            field.identifier()))
        + optionalStringPolicyOverload
        + optionalObjectPolicyOverload;
  }

    private static String renderOptionalStepMethod(
      final ContractField field, final String optionalStepName, final boolean policyAwareRequired) {
    if (!field.requiresCoercion()) {
      final var optionalPolicyOverload =
        policyAwareRequired && field.nullable()
          ? """

    %s %s(final %s %s, final ContractPolicy.FieldPolicy<%s> policy);
  """
            .formatted(
              optionalStepName,
              field.identifier(),
              annotateNullable(field.javaType()),
              field.identifier(),
              field.javaType())
          : "";

      return ("""
    %s %s(final %s %s);
  """
        .formatted(optionalStepName, field.identifier(), field.nullable() ? annotateNullable(field.javaType()) : field.javaType(), field.identifier()))
        + optionalPolicyOverload;
    }

    final var optionalStringPolicyOverload =
      policyAwareRequired && field.nullable()
        ? """

    %s %s(final %s %s, final ContractPolicy.FieldPolicy<%s> policy);
  """
          .formatted(
            optionalStepName,
            field.identifier(),
            annotateNullable(field.javaType()),
            field.identifier(),
            field.javaType())
        : "";

    final var optionalObjectPolicyOverload =
      policyAwareRequired && field.nullable()
        ? """

    %s %s(final @Nullable Object %s, final ContractPolicy.FieldPolicy<Object> policy);
  """
          .formatted(optionalStepName, field.identifier(), field.identifier())
        : "";

    return ("""
    %s %s(final %s %s);

    %s %s(final %s %s);
  """
      .formatted(
        optionalStepName,
        field.identifier(),
        field.nullable() ? annotateNullable(field.javaType()) : field.javaType(),
        field.identifier(),
        optionalStepName,
        field.identifier(),
        field.nullable() ? "@Nullable Object" : "Object",
        field.identifier()))
      + optionalStringPolicyOverload
      + optionalObjectPolicyOverload;
    }

    private static String stepInterfaceName(final ContractField field) {
    return capitalizeIdentifier(field.identifier()) + "Step";
    }

  private static String renderBuildArg(
      final ContractField field, final boolean policyAwareRequired) {
    final var fieldValue = "this." + field.identifier();
    return field.requiresCoercion()
        ? "coerce" + capitalizeIdentifier(field.identifier()) + "(" + fieldValue + ")"
        : fieldValue;
  }

  private static boolean supportsPolicyAwareBuilder(final String schemaName) {
    return true;
  }

  private static String capitalizeIdentifier(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return "Field";
    }
    return identifier.substring(0, 1).toUpperCase() + identifier.substring(1);
  }

  private static String toConstantName(final String identifier) {
    final var result = new StringBuilder();
    for (int i = 0; i < identifier.length(); i++) {
      final var ch = identifier.charAt(i);
      if (Character.isUpperCase(ch) && i > 0) {
        result.append('_');
      }
      result.append(Character.toUpperCase(ch));
    }
    return result.toString();
  }

  /** Positions {@code @Nullable} correctly for TYPE_USE on qualified type names. */
  private static String annotateNullable(final String type) {
    final int genericStart = type.indexOf('<');
    final String baseType = genericStart >= 0 ? type.substring(0, genericStart) : type;
    final String genericSuffix = genericStart >= 0 ? type.substring(genericStart) : "";
    final int lastDot = baseType.lastIndexOf('.');
    if (lastDot < 0) {
      return "@Nullable " + type;
    }
    return baseType.substring(0, lastDot + 1)
        + "@Nullable "
        + baseType.substring(lastDot + 1)
        + genericSuffix;
  }

  private static String renderMapper(
    String sourceFile,
    String schemaName,
    String dtoClass,
    String mapperClass,
    List<ContractField> fields) {
  final String protocolClass = PROTOCOL_PACKAGE + "." + schemaName;
  final String protocolSimpleName = schemaName;

    // Collect all imports, sort them for Checkstyle compliance
    final var allImports = new LinkedHashSet<String>();
    allImports.add("jakarta.annotation.Generated");
    allImports.add(protocolClass);
    for (final var f : fields) {
      if (f.hasStrictObjectType()) {
        allImports.add(f.typeInfo().protocolJavaType());
      } else if (f.hasStrictListType()) {
        allImports.add("java.util.Collections");
        final var elemType = f.typeInfo().elementType();
        if (elemType != null && elemType.protocolJavaType() != null) {
          allImports.add(elemType.protocolJavaType());
        }
      } else if (f.hasStrictMapValueType()) {
        final var valType = f.typeInfo().elementType();
        if (valType != null && valType.protocolJavaType() != null) {
          allImports.add(valType.protocolJavaType());
        }
      }
    }

    final String mappingLines =
    fields.stream()
        .map(f -> renderMapperFieldLine(f, schemaName))
            .collect(Collectors.joining("\n"));

    final String importBlock = allImports.stream()
        .sorted()
        .map(imp -> "import " + imp + ";")
        .collect(Collectors.joining("\n"))
        + "\n";

    return """
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/%s#/components/schemas/%s
 */
package %s;

%s
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class %s {

  private %s() {}

  public static %s toProtocol(final %s source) {
    return new %s()
%s;
  }
}
"""
        .formatted(
            sourceFile,
            schemaName,
            TARGET_PACKAGE,
            importBlock,
            mapperClass,
            mapperClass,
            protocolSimpleName,
            dtoClass,
            protocolSimpleName,
            mappingLines);
  }

  private static String renderMapperFieldLine(ContractField f, String schemaName) {
    final var accessor = "source." + f.identifier() + "()";
    if (f.hasInlineEnum()) {
      final var enumClass = schemaName + "." + capitalizeIdentifier(f.name()) + "Enum";
      return "        ." + f.mapperMethod() + "(" + accessor + " == null ? null : " + enumClass + ".fromValue(" + accessor + "))";
    }
    if (f.hasStrictObjectType()) {
      final var nestedMapper = mapperClassName(extractSchemaNameFromStrictClass(f.typeInfo().strictDtoClass()));
      if (f.nullable()) {
        return "        ." + f.mapperMethod() + "(" + accessor + " == null ? null : " + nestedMapper + ".toProtocol(" + accessor + "))";
      } else {
        return "        ." + f.mapperMethod() + "(" + nestedMapper + ".toProtocol(" + accessor + "))";
      }
    } else if (f.hasStrictListType()) {
      final var elemType = f.typeInfo().elementType();
      if (elemType != null && elemType.strictDtoClass() != null) {
        final var nestedMapper = mapperClassName(extractSchemaNameFromStrictClass(elemType.strictDtoClass()));
        final var collector = f.javaType().startsWith("java.util.Set<")
            ? ".collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))"
            : ".toList()";
        return "        ." + f.mapperMethod() + "(" + accessor + " == null ? null : " + accessor + ".stream().map(" + nestedMapper + "::toProtocol)" + collector + ")";
      }
    } else if (f.hasStrictMapValueType()) {
      final var valType = f.typeInfo().elementType();
      final var nestedMapper = mapperClassName(extractSchemaNameFromStrictClass(valType.strictDtoClass()));
      return "        ." + f.mapperMethod() + "(" + accessor + " == null ? null : "
          + accessor + ".entrySet().stream().collect(java.util.stream.Collectors.toMap("
          + "java.util.Map.Entry::getKey, e -> " + nestedMapper + ".toProtocol(e.getValue()))))";
    }
    return "        ." + f.mapperMethod() + "(" + accessor + ")";
  }

  private static String extractSchemaNameFromStrictClass(String strictDtoClass) {
    // "GeneratedFooStrictContract" → "Foo"
    var name = strictDtoClass;
    if (name.startsWith("Generated")) {
      name = name.substring("Generated".length());
    }
    if (name.endsWith("StrictContract")) {
      name = name.substring(0, name.length() - "StrictContract".length());
    }
    // The mapper class name uses the schema name, which for Result schemas has "Result" suffix
    // but the DTO class name strips it. We need the original schema name.
    // Look up in the mapper: dtoClassName strips "Result" suffix, so we need to check if
    // adding "Result" back gives us a valid protocol type.
    if (AVAILABLE_PROTOCOL_TYPES.contains(name + "Result")) {
      return name + "Result";
    }
    return name;
  }

  private static String extractNestedSchemaName(ContractField f) {
    if (f.hasStrictObjectType()) {
      return extractSchemaNameFromStrictClass(f.typeInfo().strictDtoClass());
    }
    if (f.hasStrictListType()) {
      final var et = f.typeInfo().elementType();
      if (et != null && et.strictDtoClass() != null) {
        return extractSchemaNameFromStrictClass(et.strictDtoClass());
      }
    }
    if (f.hasStrictMapValueType()) {
      return extractSchemaNameFromStrictClass(f.typeInfo().elementType().strictDtoClass());
    }
    return null;
  }

  private static Map<SchemaKey, SchemaDef> loadSchemas(Path specDir) throws IOException {
    final var schemas = new LinkedHashMap<SchemaKey, SchemaDef>();
    try (var stream = Files.list(specDir)) {
      final var yamlFiles =
          stream
              .filter(path -> path.getFileName().toString().endsWith(".yaml"))
              .sorted(Comparator.comparing(path -> path.getFileName().toString()))
              .toList();
      for (var file : yamlFiles) {
        parseSchemasFromFile(file, schemas);
      }
    }
    return schemas;
  }

  private static void parseSchemasFromFile(Path file, Map<SchemaKey, SchemaDef> schemas)
      throws IOException {
    final var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    final var fileName = file.getFileName().toString();

    for (int i = 0; i < lines.size(); i++) {
      final var line = lines.get(i);
      if (trimmed(line).equals("components:")) {
        final int componentsIndent = indent(line);
        i = parseComponentsBlock(lines, i + 1, componentsIndent, fileName, schemas);
      }
    }
  }

  private static int parseComponentsBlock(
      List<String> lines,
      int start,
      int parentIndent,
      String fileName,
      Map<SchemaKey, SchemaDef> schemas) {
    for (int i = start; i < lines.size(); i++) {
      final var line = lines.get(i);
      if (isIgnorable(line)) {
        continue;
      }

      final int ind = indent(line);
      if (ind <= parentIndent) {
        return i - 1;
      }

      if (ind == parentIndent + 2 && trimmed(line).equals("schemas:")) {
        return parseSchemasBlock(lines, i + 1, ind, fileName, schemas);
      }
    }
    return lines.size() - 1;
  }

  private static int parseSchemasBlock(
      List<String> lines,
      int start,
      int parentIndent,
      String fileName,
      Map<SchemaKey, SchemaDef> schemas) {
    for (int i = start; i < lines.size(); i++) {
      final var line = lines.get(i);
      if (isIgnorable(line)) {
        continue;
      }

      final int ind = indent(line);
      if (ind <= parentIndent) {
        return i - 1;
      }

      if (ind == parentIndent + 2 && line.stripTrailing().endsWith(":")) {
        final var schemaName = trimmed(line).substring(0, trimmed(line).length() - 1);
        final int blockStart = i + 1;
        int blockEnd = lines.size();
        for (int j = blockStart; j < lines.size(); j++) {
          final var next = lines.get(j);
          if (isIgnorable(next)) {
            continue;
          }
          if (indent(next) <= ind) {
            blockEnd = j;
            break;
          }
        }

        final var node = parseNode(lines, blockStart, blockEnd, ind + 2);
        schemas.put(new SchemaKey(fileName, schemaName), new SchemaDef(fileName, schemaName, node));
        i = blockEnd - 1;
      }
    }
    return lines.size() - 1;
  }

  private static Node parseNode(List<String> lines, int start, int end, int baseIndent) {
    String type = null;
    String format = null;
    boolean nullable = false;
    String ref = null;
    Node items = null;
    boolean uniqueItems = false;
    Node additionalProperties = null;
    final var required = new LinkedHashSet<String>();
    final var enumValues = new ArrayList<String>();
    final var allOfRefs = new ArrayList<String>();
    final var properties = new LinkedHashMap<String, Node>();

    for (int i = start; i < end; i++) {
      final var line = lines.get(i);
      if (isIgnorable(line)) {
        continue;
      }

      final int ind = indent(line);
      if (ind < baseIndent) {
        break;
      }
      if (ind != baseIndent) {
        continue;
      }

      final var trimmed = trimmed(line);
      if (!trimmed.contains(":")) {
        continue;
      }

      final int colon = trimmed.indexOf(':');
      final var key = trimmed.substring(0, colon).trim();
      final var value = trimmed.substring(colon + 1).trim();

      switch (key) {
        case "type" -> type = unquote(value);
        case "format" -> format = unquote(value);
        case "nullable" -> nullable = "true".equalsIgnoreCase(value);
        case "uniqueItems" -> uniqueItems = "true".equalsIgnoreCase(value);
        case "$ref" -> ref = unquote(value);
        case "required" -> {
          final var list = parseSimpleList(lines, i + 1, end, baseIndent + 2);
          required.addAll(list.items());
          i = list.lastIndex();
        }
        case "enum" -> {
          if (value.startsWith("[") && value.endsWith("]")) {
            // Inline array: enum: [ VAL1, VAL2 ]
            final var inner = value.substring(1, value.length() - 1);
            for (var part : inner.split(",")) {
              final var item = unquote(part.trim());
              if (!item.isEmpty()) {
                enumValues.add(item);
              }
            }
          } else {
            final var list = parseSimpleList(lines, i + 1, end, baseIndent + 2);
            enumValues.addAll(list.items());
            i = list.lastIndex();
          }
        }
        case "allOf" -> {
          final var list = parseRefList(lines, i + 1, end, baseIndent + 2);
          allOfRefs.addAll(list.refs());
          i = list.lastIndex();
        }
        case "items" -> {
          final int blockEnd = findNestedBlockEnd(lines, i + 1, end, baseIndent + 2);
          items = parseNode(lines, i + 1, blockEnd, baseIndent + 2);
          i = blockEnd - 1;
        }
        case "properties" -> {
          final var result = parseProperties(lines, i + 1, end, baseIndent + 2);
          properties.putAll(result.properties());
          i = result.lastIndex();
        }
        case "additionalProperties" -> {
          if (!"true".equals(value) && !"false".equals(value)) {
            final int blockEnd = findNestedBlockEnd(lines, i + 1, end, baseIndent + 2);
            additionalProperties = parseNode(lines, i + 1, blockEnd, baseIndent + 2);
            i = blockEnd - 1;
          }
        }
        default -> {
          // Ignore unsupported YAML keys for this generator.
        }
      }
    }

    return new Node(type, format, nullable, ref, items, required, enumValues, allOfRefs, properties, uniqueItems, additionalProperties);
  }

  private static PropertiesParseResult parseProperties(
      List<String> lines, int start, int end, int propertiesIndent) {
    final var properties = new LinkedHashMap<String, Node>();
    int i = start;
    for (; i < end; i++) {
      final var line = lines.get(i);
      if (isIgnorable(line)) {
        continue;
      }

      final int ind = indent(line);
      if (ind < propertiesIndent) {
        break;
      }
      if (ind != propertiesIndent || !line.stripTrailing().endsWith(":")) {
        continue;
      }

      final var propertyName = trimmed(line).substring(0, trimmed(line).length() - 1);
      final int blockStart = i + 1;
      final int blockEnd = findNestedBlockEnd(lines, blockStart, end, propertiesIndent + 2);
      properties.put(propertyName, parseNode(lines, blockStart, blockEnd, propertiesIndent + 2));
      i = blockEnd - 1;
    }
    return new PropertiesParseResult(properties, i - 1);
  }

  private static int findNestedBlockEnd(
      List<String> lines, int start, int end, int minIndentForNestedContent) {
    for (int i = start; i < end; i++) {
      final var line = lines.get(i);
      if (isIgnorable(line)) {
        continue;
      }
      if (indent(line) < minIndentForNestedContent) {
        return i;
      }
    }
    return end;
  }

  private static ListParseResult parseSimpleList(
      List<String> lines, int start, int end, int itemIndent) {
    final var items = new ArrayList<String>();
    int i = start;
    for (; i < end; i++) {
      final var line = lines.get(i);
      if (isIgnorable(line)) {
        continue;
      }

      final int ind = indent(line);
      if (ind < itemIndent) {
        break;
      }
      if (ind == itemIndent && trimmed(line).startsWith("- ")) {
        items.add(unquote(trimmed(line).substring(2).trim()));
      }
    }
    return new ListParseResult(items, i - 1);
  }

  private static RefListParseResult parseRefList(
      List<String> lines, int start, int end, int itemIndent) {
    final var refs = new ArrayList<String>();
    int i = start;
    for (; i < end; i++) {
      final var line = lines.get(i);
      if (isIgnorable(line)) {
        continue;
      }

      final int ind = indent(line);
      if (ind < itemIndent) {
        break;
      }

      final var t = trimmed(line);
      if (ind == itemIndent && t.startsWith("- $ref:")) {
        refs.add(unquote(t.substring("- $ref:".length()).trim()));
      } else if (ind == itemIndent && t.equals("-")) {
        for (int j = i + 1; j < end; j++) {
          final var nested = lines.get(j);
          if (isIgnorable(nested)) {
            continue;
          }
          final int nestedIndent = indent(nested);
          if (nestedIndent <= ind) {
            i = j - 1;
            break;
          }
          final var nestedTrimmed = trimmed(nested);
          if (nestedTrimmed.startsWith("$ref:")) {
            refs.add(unquote(nestedTrimmed.substring("$ref:".length()).trim()));
          }
          i = j;
        }
      }
    }
    return new RefListParseResult(refs, i - 1);
  }

  private static String resolveJavaType(
      Node node,
      String currentFile,
      Map<SchemaKey, SchemaDef> allSchemas,
      ArrayDeque<SchemaKey> resolvingStack) {
    return resolveTypeInfo(node, currentFile, allSchemas, resolvingStack).javaType();
  }

  private static TypeInfo resolveTypeInfo(
      Node node,
      String currentFile,
      Map<SchemaKey, SchemaDef> allSchemas,
      ArrayDeque<SchemaKey> resolvingStack) {
    if (node.ref() != null) {
      return resolveRefTypeInfo(node.ref(), currentFile, allSchemas, resolvingStack);
    }

    if (!node.allOfRefs().isEmpty()
        && (node.type() == null || "object".equals(node.type()))
        && node.properties().isEmpty()) {
      if (node.allOfRefs().size() == 1) {
        return resolveRefTypeInfo(node.allOfRefs().getFirst(), currentFile, allSchemas, resolvingStack);
      }
      return TypeInfo.scalar("Object");
    }

    if ("array".equals(node.type())) {
      final var itemType =
          node.items() == null
              ? TypeInfo.scalar("Object")
              : resolveTypeInfo(node.items(), currentFile, allSchemas, resolvingStack);
      if (node.uniqueItems()) {
        return TypeInfo.setOf(itemType);
      }
      return TypeInfo.listOf(itemType);
    }

    if ("string".equals(node.type())) {
      return TypeInfo.scalar("String");
    }

    if ("integer".equals(node.type())) {
      return TypeInfo.scalar("int64".equals(node.format()) ? "Long" : "Integer");
    }

    if ("number".equals(node.type())) {
      return TypeInfo.scalar("Double");
    }

    if ("boolean".equals(node.type())) {
      return TypeInfo.scalar("Boolean");
    }

    if ("object".equals(node.type())) {
      if (node.additionalProperties() != null) {
        final var valueType = resolveTypeInfo(node.additionalProperties(), currentFile, allSchemas, resolvingStack);
        return TypeInfo.mapOf(valueType);
      }
      return TypeInfo.scalar("java.util.Map<String, Object>");
    }

    return TypeInfo.scalar("Object");
  }

  private static TypeInfo resolveRefTypeInfo(
      String ref,
      String currentFile,
      Map<SchemaKey, SchemaDef> allSchemas,
      ArrayDeque<SchemaKey> resolvingStack) {
    final var key = toSchemaKey(ref, currentFile);
    final var schema = allSchemas.get(key);
    if (schema == null) {
      return TypeInfo.scalar("Object");
    }

    final var protocolType = PROTOCOL_PACKAGE + "." + key.schemaName();
    if (resolvingStack.contains(key)) {
      if (hasStrictContractType(schema)) {
        final var strictType = dtoClassName(key.schemaName());
        return TypeInfo.strictObject(strictType, protocolType, supportsFromProtocolFactory(key.schemaName()));
      }
      return TypeInfo.scalar(protocolType);
    }

    if (hasStrictContractType(schema)) {
      final var strictType = dtoClassName(key.schemaName());
      return TypeInfo.strictObject(strictType, protocolType, supportsFromProtocolFactory(key.schemaName()));
    }

    if (!schema.node().enumValues().isEmpty()) {
      return TypeInfo.scalar(protocolType);
    }

    resolvingStack.addLast(key);
    try {
      return resolveTypeInfo(schema.node(), key.fileName(), allSchemas, resolvingStack);
    } finally {
      resolvingStack.removeLast();
    }
  }

  private static boolean hasStrictContractType(final SchemaDef schema) {
    return !schema.node().properties().isEmpty();
  }

  private static String resolveRefJavaType(
      String ref,
      String currentFile,
      Map<SchemaKey, SchemaDef> allSchemas,
      ArrayDeque<SchemaKey> resolvingStack) {
    return resolveRefTypeInfo(ref, currentFile, allSchemas, resolvingStack).javaType();
  }

  /**
   * Checks if a node (or its resolved $ref target) has format: uri or format: uri-reference. The
   * protocol model generates java.net.URI for these, but the strict contract uses String.
   */
  private static boolean isUriFormat(
      Node node, String currentFile, Map<SchemaKey, SchemaDef> allSchemas) {
    final var format = node.format();
    if ("uri".equals(format) || "uri-reference".equals(format)) {
      return true;
    }
    if (node.ref() != null) {
      final var key = toSchemaKey(node.ref(), currentFile);
      final var target = allSchemas.get(key);
      if (target != null) {
        final var targetFormat = target.node().format();
        return "uri".equals(targetFormat) || "uri-reference".equals(targetFormat);
      }
    }
    return false;
  }

  private static boolean isLongKeySemantic(
      Node node,
      String currentFile,
      Map<SchemaKey, SchemaDef> allSchemas,
      ArrayDeque<SchemaKey> resolvingStack) {
    if (node.ref() != null) {
      return isLongKeyRef(node.ref(), currentFile, allSchemas, resolvingStack);
    }

    if (!node.allOfRefs().isEmpty()) {
      for (var ref : node.allOfRefs()) {
        if (isLongKeyRef(ref, currentFile, allSchemas, resolvingStack)) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean isLongKeyRef(
      String ref,
      String currentFile,
      Map<SchemaKey, SchemaDef> allSchemas,
      ArrayDeque<SchemaKey> resolvingStack) {
    final var key = toSchemaKey(ref, currentFile);
    if ("LongKey".equals(key.schemaName())) {
      return true;
    }

    final var schema = allSchemas.get(key);
    if (schema == null || resolvingStack.contains(key)) {
      return false;
    }

    resolvingStack.addLast(key);
    try {
      if (schema.node().ref() != null
          && isLongKeyRef(schema.node().ref(), key.fileName(), allSchemas, resolvingStack)) {
        return true;
      }

      for (var allOfRef : schema.node().allOfRefs()) {
        if (isLongKeyRef(allOfRef, key.fileName(), allSchemas, resolvingStack)) {
          return true;
        }
      }

      return false;
    } finally {
      resolvingStack.removeLast();
    }
  }

  private static SchemaKey toSchemaKey(String ref, String currentFile) {
    final var cleanRef = unquote(ref);
    final String file;
    final String fragment;

    if (cleanRef.startsWith("#/")) {
      file = currentFile;
      fragment = cleanRef.substring(1);
    } else {
      final int hash = cleanRef.indexOf('#');
      file = hash >= 0 ? cleanRef.substring(0, hash) : cleanRef;
      fragment = hash >= 0 ? cleanRef.substring(hash + 1) : "";
    }

    final var marker = "/components/schemas/";
    final int markerPos = fragment.indexOf(marker);
    if (markerPos < 0) {
      return new SchemaKey(file, "");
    }

    final var schemaName = fragment.substring(markerPos + marker.length()).replace("~1", "/");
    return new SchemaKey(file, schemaName);
  }

  private static boolean isIgnorable(String line) {
    final var t = trimmed(line);
    return t.isEmpty() || t.startsWith("#");
  }

  private static int indent(String line) {
    int count = 0;
    while (count < line.length() && line.charAt(count) == ' ') {
      count++;
    }
    return count;
  }

  private static String trimmed(String line) {
    return line == null ? "" : line.trim();
  }

  private static String unquote(String value) {
    if (value == null) {
      return null;
    }
    final var trimmed = value.trim();
    if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
        || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }

  private static String uniqueIdentifier(String base, Map<String, Integer> used) {
    final var count = used.getOrDefault(base, 0);
    used.put(base, count + 1);
    return count == 0 ? base : base + count;
  }

  private static String toJavaIdentifier(String propertyName) {
    if (propertyName == null || propertyName.isBlank()) {
      return "field";
    }

    final var parts = propertyName.split("[^A-Za-z0-9]");
    final var builder = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      final var part = parts[i];
      if (part.isBlank()) {
        continue;
      }

      if (builder.isEmpty()) {
        builder.append(part.substring(0, 1).toLowerCase()).append(part.substring(1));
      } else {
        builder.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
      }
    }

    if (builder.isEmpty()) {
      builder.append("field");
    }

    if (!Character.isJavaIdentifierStart(builder.charAt(0))) {
      builder.insert(0, "field");
      builder.setCharAt(0, 'f');
    }

    for (int i = 1; i < builder.length(); i++) {
      if (!Character.isJavaIdentifierPart(builder.charAt(i))) {
        builder.setCharAt(i, '_');
      }
    }

    return builder.toString();
  }

  private static String toJavaMethodName(String propertyName) {
    return toJavaIdentifier(propertyName);
  }

  private record SchemaKey(String fileName, String schemaName) {}

  private record SchemaDef(String fileName, String schemaName, Node node) {}

  private record Node(
      String type,
      String format,
      boolean nullable,
      String ref,
      Node items,
      Set<String> required,
      List<String> enumValues,
      List<String> allOfRefs,
      Map<String, Node> properties,
      boolean uniqueItems,
      Node additionalProperties) {}

  private record ContractField(
      String name,
      String identifier,
      String mapperMethod,
      String javaType,
      boolean required,
      boolean nullable,
      boolean longKeyCoercion,
      boolean hasInlineEnum,
      boolean hasUniqueItems,
      boolean hasMapperFieldIncompatibility,
      TypeInfo typeInfo) {

    boolean hasStrictObjectType() {
      return typeInfo != null
          && typeInfo.strictObjectType()
          && typeInfo.strictDtoClass() != null
          && typeInfo.protocolJavaType() != null;
    }

    boolean hasStrictListType() {
      return typeInfo != null && typeInfo.strictListType();
    }

    boolean hasStrictMapValueType() {
      return typeInfo != null && typeInfo.strictMapValueType();
    }

    boolean requiresCoercion() {
      return longKeyCoercion || hasStrictObjectType() || hasStrictListType();
    }
  }

  private record TypeInfo(
      String javaType,
      boolean strictObjectType,
      String strictDtoClass,
      String protocolJavaType,
      boolean protocolConvertible,
      TypeInfo elementType) {

    static TypeInfo scalar(final String javaType) {
      return new TypeInfo(javaType, false, null, null, false, null);
    }

    static TypeInfo strictObject(
        final String strictDtoClass,
        final String protocolJavaType,
        final boolean protocolConvertible) {
      return new TypeInfo(
          strictDtoClass, true, strictDtoClass, protocolJavaType, protocolConvertible, null);
    }

    static TypeInfo listOf(final TypeInfo elementType) {
      return new TypeInfo(
          "java.util.List<" + elementType.javaType() + ">",
          false,
          null,
          null,
          false,
          elementType);
    }

    static TypeInfo setOf(final TypeInfo elementType) {
      return new TypeInfo(
          "java.util.Set<" + elementType.javaType() + ">",
          false,
          null,
          null,
          false,
          elementType);
    }

    static TypeInfo mapOf(final TypeInfo valueType) {
      return new TypeInfo(
          "java.util.Map<String, " + valueType.javaType() + ">",
          false,
          null,
          null,
          false,
          valueType);
    }

    boolean strictListType() {
      return elementType != null
          && !javaType.startsWith("java.util.Map<")
          && ((elementType.strictObjectType() && elementType.strictDtoClass() != null)
              || elementType.strictListType());
    }

    boolean strictMapValueType() {
      return elementType != null
          && javaType.startsWith("java.util.Map<")
          && elementType.strictObjectType()
          && elementType.strictDtoClass() != null;
    }
  }

  private record ListParseResult(List<String> items, int lastIndex) {}

  private record RefListParseResult(List<String> refs, int lastIndex) {}

  private record PropertiesParseResult(Map<String, Node> properties, int lastIndex) {}

  private record PropertyWithContext(Node node, String fileName) {}
}
