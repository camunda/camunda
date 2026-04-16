/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RestControllersGenerator {

  /** Package for generated controllers — same as hand-written ones they replace. */
  private static final String CONTROLLER_PACKAGE = "io.camunda.zeebe.gateway.rest.controller";
  /** Package for service adapter interfaces (new types, no conflict). */
  private static final String ADAPTER_PACKAGE =
      "io.camunda.zeebe.gateway.rest.controller.generated";
  /** DTO package in gateway-model (for FQN resolution in controller params). */
  private static final String TARGET_PACKAGE =
      "io.camunda.gateway.protocol.model";

  private static final String SEARCH_PACKAGE = "io.camunda.gateway.mapping.http.search";

  // These are populated in main() by running schema analysis (same logic as MappingContractsGenerator)
  private static final Set<String> AVAILABLE_STRICT_CONTRACTS = new LinkedHashSet<>();
  private static final Map<String, String> SEARCH_REQUEST_DTO_MAP = new LinkedHashMap<>();
  private static final Set<String> RETAINED_RESULT_SCHEMAS = new LinkedHashSet<>();

  // Compound keys that extend LongKey in the spec but use non-numeric values at runtime
  private static final Set<String> COMPOUND_KEY_EXCLUSIONS =
      Set.of("DecisionEvaluationInstanceKey", "AuditLogKey");

  public static void main(String[] args) throws Exception {
    final var repoRoot = Path.of(args[0]);
    final var specDir =
        repoRoot.resolve("zeebe/gateway-protocol/src/main/proto/v2").normalize();
    final var controllerOutBase = repoRoot.resolve("zeebe/gateway-rest/target/generated-sources");

    if (!Files.isDirectory(specDir)) {
      throw new IllegalStateException("OpenAPI spec directory does not exist: " + specDir);
    }

    // Load schemas to populate AVAILABLE_STRICT_CONTRACTS and SEARCH_REQUEST_DTO_MAP
    // (needed for resolveSchemaType used in controller generation)
    final var allSchemas = loadSchemas(specDir);
    for (var key : allSchemas.keySet()) {
      final var name = key.schemaName();
      if (name.endsWith("Result")) {
        final var baseName = name.substring(0, name.length() - 6);
        if (!baseName.endsWith("SearchQuery")
            && allSchemas.keySet().stream().anyMatch(k -> k.schemaName().equals(baseName))) {
          RETAINED_RESULT_SCHEMAS.add(name);
        }
      }
    }
    final var contractSchemas =
        allSchemas.values().stream()
            .filter(RestControllersGenerator::isContractSchema)
            .toList();
    for (var schema : contractSchemas) {
      AVAILABLE_STRICT_CONTRACTS.add(schema.schemaName());
    }
    for (var schema : allSchemas.values()) {
      if (isEnumSchema(schema)) {
        AVAILABLE_STRICT_CONTRACTS.add(schema.schemaName());
      }
    }
    final var contractDtoNames =
        contractSchemas.stream()
            .map(s -> dtoClassName(s.schemaName()))
            .collect(Collectors.toSet());
    for (var schema : allSchemas.values()) {
      if (isPolymorphicSchema(schema)
          && !"SearchQueryPageRequest".equals(schema.schemaName())
          && !contractDtoNames.contains(dtoClassName(schema.schemaName()))) {
        final var branchNames =
            schema.node().oneOfRefs().stream()
                .map(RestControllersGenerator::refToSchemaName)
                .toList();
        final var allBranchesHaveContracts =
            branchNames.stream().allMatch(AVAILABLE_STRICT_CONTRACTS::contains);
        if (allBranchesHaveContracts) {
          AVAILABLE_STRICT_CONTRACTS.add(schema.schemaName());
        }
      }
    }
    final var searchQuerySchemas = discoverSearchQuerySchemas(allSchemas);
    for (var sqe : searchQuerySchemas) {
      SEARCH_REQUEST_DTO_MAP.put(sqe.schemaName(), sqe.requestDtoName());
    }

    // Phase 4: Generate controllers and service adapters
    final var controllerDir =
        controllerOutBase.resolve(CONTROLLER_PACKAGE.replace('.', '/'));
    final var adapterDir =
        controllerOutBase.resolve(ADAPTER_PACKAGE.replace('.', '/'));
    Files.createDirectories(controllerDir);
    Files.createDirectories(adapterDir);
    cleanupPreviouslyGeneratedFiles(controllerDir);
    cleanupPreviouslyGeneratedFiles(adapterDir);

    final var universalEntries = buildUniversalControllerEntries(specDir);
    int universalCount = 0;
    int adapterCount = 0;
    for (var ctrl : universalEntries) {
      final var controllerFile = controllerDir.resolve(ctrl.className() + ".java");
      Files.writeString(controllerFile, renderUniversalController(ctrl), StandardCharsets.UTF_8);
      System.out.println("generated (controller): " + controllerFile);
      universalCount++;

      final var adapterFile = adapterDir.resolve(ctrl.tagPascal() + "ServiceAdapter.java");
      Files.writeString(adapterFile, renderServiceAdapterInterface(ctrl), StandardCharsets.UTF_8);
      System.out.println("generated (adapter): " + adapterFile);
      adapterCount++;
    }
    System.out.println(universalCount + " controller(s) generated.");
    System.out.println(adapterCount + " service adapter(s) generated.");
  }

  // ---------------------------------------------------------------------------
  // Schema loading
  // ---------------------------------------------------------------------------

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
        schemas.put(
            new SchemaKey(fileName, schemaName), new SchemaDef(fileName, schemaName, node));
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
    final var oneOfRefs = new ArrayList<String>();
    String oneOfInlineType = null;
    String oneOfInlineFormat = null;
    final var oneOfInlineAllOfRefs = new ArrayList<String>();
    final var properties = new LinkedHashMap<String, Node>();
    Long minimum = null;
    Long maximum = null;
    Integer minLength = null;
    Integer maxLength = null;
    String pattern = null;
    Integer minItems = null;
    Integer maxItems = null;
    String defaultValue = null;
    String description = null;

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
          final int allOfBlockEnd = findNestedBlockEnd(lines, i + 1, end, baseIndent + 2);
          final var list = parseRefList(lines, i + 1, allOfBlockEnd, baseIndent + 2);
          allOfRefs.addAll(list.refs());
          // Also merge properties from inline allOf entries (e.g., $or in ProcessInstanceFilter).
          for (int ai = i + 1; ai < allOfBlockEnd; ai++) {
            final var aLine = lines.get(ai);
            if (isIgnorable(aLine)) continue;
            final int aInd = indent(aLine);
            if (aInd < baseIndent + 2) break;
            if (aInd != baseIndent + 2) continue;
            final var aTrimmed = trimmed(aLine);
            if (aTrimmed.startsWith("- ") && !aTrimmed.startsWith("- $ref:")) {
              final int inlineEnd =
                  findNestedBlockEnd(lines, ai + 1, allOfBlockEnd, baseIndent + 4);
              final var inlineNode = parseNode(lines, ai + 1, inlineEnd, baseIndent + 4);
              properties.putAll(inlineNode.properties());
              required.addAll(inlineNode.required());
              ai = inlineEnd - 1;
            }
          }
          i = allOfBlockEnd - 1;
        }
        case "oneOf" -> {
          final int oneOfBlockEnd = findNestedBlockEnd(lines, i + 1, end, baseIndent + 2);
          final var oneOfList = parseRefList(lines, i + 1, oneOfBlockEnd, baseIndent + 2);
          oneOfRefs.addAll(oneOfList.refs());
          // Capture inline primitive type from oneOf branches (e.g., "- type: string").
          // This is needed to detect filter property schemas with a plain-value branch.
          for (int oi = i + 1; oi < oneOfBlockEnd; oi++) {
            final var oLine = lines.get(oi);
            if (isIgnorable(oLine)) continue;
            final var oTrimmed = trimmed(oLine);
            if (oTrimmed.startsWith("- type:")) {
              oneOfInlineType = unquote(oTrimmed.substring("- type:".length()).trim());
              // Look for format on the next line(s) at deeper indent
              for (int fi = oi + 1; fi < oneOfBlockEnd; fi++) {
                final var fLine = lines.get(fi);
                if (isIgnorable(fLine)) continue;
                final int fInd = indent(fLine);
                if (fInd <= indent(oLine)) break;
                final var fTrimmed = trimmed(fLine);
                if (fTrimmed.startsWith("format:")) {
                  oneOfInlineFormat = unquote(fTrimmed.substring("format:".length()).trim());
                  break;
                }
              }
              // Also capture allOf refs from this inline branch (e.g. enum schema refs)
              for (int ai = oi + 1; ai < oneOfBlockEnd; ai++) {
                final var aLine = lines.get(ai);
                if (isIgnorable(aLine)) continue;
                final int aInd = indent(aLine);
                if (aInd <= indent(oLine)) break;
                final var aTrimmed = trimmed(aLine);
                if (aTrimmed.startsWith("allOf:")) {
                  final int allOfEnd =
                      findNestedBlockEnd(lines, ai + 1, oneOfBlockEnd, aInd + 2);
                  final var refList = parseRefList(lines, ai + 1, allOfEnd, aInd + 2);
                  oneOfInlineAllOfRefs.addAll(refList.refs());
                  break;
                }
              }
              break; // only capture the first inline type
            }
          }
          i = oneOfBlockEnd - 1;
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
        case "minimum" -> minimum = Long.parseLong(value);
        case "maximum" -> maximum = Long.parseLong(value);
        case "minLength" -> minLength = Integer.parseInt(value);
        case "maxLength" -> maxLength = Integer.parseInt(value);
        case "pattern" -> pattern = unquote(value);
        case "minItems" -> minItems = Integer.parseInt(value);
        case "maxItems" -> maxItems = Integer.parseInt(value);
        case "default" -> {
          // Strip trailing YAML comments (e.g., "3600000 # 1 hour" → "3600000").
          final var raw =
              value.contains(" #") ? value.substring(0, value.indexOf(" #")).trim() : value;
          defaultValue = unquote(raw);
        }
        case "description" -> {
          if (value.startsWith(">") || value.startsWith("|")) {
            // Folded (>) or literal (|) scalar — collect continuation lines.
            final var sb = new StringBuilder();
            final int contIndent = baseIndent + 2;
            for (int j = i + 1; j < end; j++) {
              final var contLine = lines.get(j);
              if (isIgnorable(contLine)) continue;
              if (indent(contLine) < contIndent) break;
              if (sb.length() > 0) sb.append(' ');
              sb.append(trimmed(contLine));
              i = j;
            }
            description = sb.toString();
          } else if (!value.isEmpty()) {
            description = unquote(value);
          }
        }
        default -> {
          // Ignore unsupported YAML keys for this generator.
        }
      }
    }

    return new Node(
        type,
        format,
        nullable,
        ref,
        items,
        required,
        enumValues,
        allOfRefs,
        oneOfRefs,
        oneOfInlineType,
        oneOfInlineFormat,
        oneOfInlineAllOfRefs,
        properties,
        uniqueItems,
        additionalProperties,
        minimum,
        maximum,
        minLength,
        maxLength,
        pattern,
        minItems,
        maxItems,
        defaultValue,
        description);
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
      if (ind >= itemIndent && trimmed(line).startsWith("- ")) {
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

  // ---------------------------------------------------------------------------
  // Schema classification
  // ---------------------------------------------------------------------------

  private static boolean isContractSchema(SchemaDef schema) {
    return !schema.node().properties().isEmpty() || !schema.node().allOfRefs().isEmpty();
  }

  private static boolean isEnumSchema(SchemaDef schema) {
    return "string".equals(schema.node().type()) && !schema.node().enumValues().isEmpty();
  }

  /** Returns true if the schema is a polymorphic oneOf with branch $refs. */
  private static boolean isPolymorphicSchema(SchemaDef schema) {
    return !schema.node().oneOfRefs().isEmpty();
  }

  /**
   * Returns true if the schema is a filter property: a oneOf with exactly one $ref branch (the
   * advanced filter) and one inline primitive branch (the plain value).
   */
  private static boolean isFilterPropertySchema(SchemaDef schema) {
    return schema.node().oneOfRefs().size() == 1 && schema.node().oneOfInlineType() != null;
  }

  /**
   * Returns true if this filter property schema represents a LongKey type. LongKey filter
   * properties accept both JSON strings ("12345") and JSON numbers (12345) for the plain value
   * branch. Detected by schema name ending in "KeyFilterProperty". Compound keys (in
   * COMPOUND_KEY_EXCLUSIONS) are excluded because their values are non-numeric strings (e.g.
   * "1-149") that must not be validated as Long.
   */
  private static boolean isLongKeyFilterProperty(String schemaName) {
    if (!schemaName.endsWith("KeyFilterProperty")) {
      return false;
    }
    // Extract the key schema name: e.g. "DecisionEvaluationInstanceKeyFilterProperty"
    // → "DecisionEvaluationInstanceKey"
    final var keySchemaName =
        schemaName.substring(0, schemaName.length() - "FilterProperty".length());
    return !COMPOUND_KEY_EXCLUSIONS.contains(keySchemaName);
  }

  /**
   * Determines the Java type of the inline primitive branch in a filter property oneOf. For LongKey
   * filter properties, the inline branch resolves to "string" via allOf, but at the JSON level it
   * accepts both strings and numbers (the deserializer handles this).
   */
  private static String filterPropertyPrimitiveJavaType(Node node) {
    if (node.oneOfInlineType() == null) return "Object";
    return switch (node.oneOfInlineType()) {
      case "string" -> {
        if ("date-time".equals(node.oneOfInlineFormat())) {
          yield "String"; // JSON sends strings; parsed to OffsetDateTime downstream
        }
        yield "String";
      }
      case "integer" -> "int32".equals(node.oneOfInlineFormat()) ? "Integer" : "Long";
      case "number" -> "Double";
      case "boolean" -> "Boolean";
      default -> "Object";
    };
  }

  /**
   * Extracts the schema name from a $ref string. E.g.
   * "#/components/schemas/AuthorizationIdBasedRequest" → "AuthorizationIdBasedRequest"
   */
  private static String refToSchemaName(String ref) {
    final int lastSlash = ref.lastIndexOf('/');
    return lastSlash >= 0 ? ref.substring(lastSlash + 1) : ref;
  }

  // ---------------------------------------------------------------------------
  // Search query schema discovery (for populating SEARCH_REQUEST_DTO_MAP)
  // ---------------------------------------------------------------------------

  /** Pagination type for a search query schema. */
  private enum PaginationType {
    /** Full pagination: offset + cursor (inherited from SearchQueryRequest). */
    FULL,
    /** Offset-only pagination: limit + from (references OffsetPagination directly). */
    OFFSET_ONLY
  }

  /**
   * A search query request schema discovered from the spec. Contains only spec-derived data: the
   * schema name, entity name (for the request DTO class name), the sort/filter schema names
   * extracted from the schema's properties, and the pagination type.
   */
  private record SearchQuerySchemaEntry(
      String schemaName,
      String entityName,
      String sortSchemaName,
      String filterSchemaName,
      PaginationType paginationType) {

    String requestDtoName() {
      return schemaName;
    }
  }

  /**
   * Discovers all search query request schemas from the spec. A schema is a search query request if
   * it either:
   *
   * <ul>
   *   <li>extends {@code SearchQueryRequest} via allOf (full pagination: offset + cursor), or</li>
   *   <li>defines its own {@code page}, {@code sort}, and {@code filter} properties where {@code
   *       page} references {@code OffsetPagination} (offset-only pagination).
   * </ul>
   *
   * <p>For each, extracts the sort and filter schema names from the schema's own properties
   * (sort.items.$ref and filter.allOfRefs).
   */
  private static List<SearchQuerySchemaEntry> discoverSearchQuerySchemas(
      Map<SchemaKey, SchemaDef> allSchemas) {
    var entries = new ArrayList<SearchQuerySchemaEntry>();

    for (var schemaDef : allSchemas.values()) {
      final var node = schemaDef.node();

      // Pattern 1: extends SearchQueryRequest via allOf (full pagination).
      boolean extendsSearchQueryRequest =
          node.allOfRefs().stream()
              .anyMatch(ref -> ref.contains("/schemas/SearchQueryRequest"));

      // Pattern 2: has page→OffsetPagination + sort + filter (offset-only pagination).
      boolean isOffsetOnlySearchQuery = false;
      if (!extendsSearchQueryRequest) {
        final var pageProp = node.properties().get("page");
        final var hasSortProp = node.properties().containsKey("sort");
        final var hasFilterProp = node.properties().containsKey("filter");
        if (pageProp != null && hasSortProp && hasFilterProp) {
          isOffsetOnlySearchQuery =
              pageProp.allOfRefs().stream()
                  .anyMatch(ref -> ref.contains("/schemas/OffsetPagination"));
        }
      }

      if (!extendsSearchQueryRequest && !isOffsetOnlySearchQuery) continue;

      final var paginationType =
          extendsSearchQueryRequest ? PaginationType.FULL : PaginationType.OFFSET_ONLY;

      final var schemaName = schemaDef.schemaName();

      // Derive entity name: strip "SearchQuery", "SearchQueryRequest" suffixes.
      var entityName =
          schemaName.replace("SearchQueryRequest", "").replace("SearchQuery", "");

      // Extract sort schema from properties.sort.items.$ref
      String sortSchemaName = null;
      final var sortProp = node.properties().get("sort");
      if (sortProp != null && sortProp.items() != null && sortProp.items().ref() != null) {
        sortSchemaName = toSchemaKey(sortProp.items().ref(), schemaDef.fileName()).schemaName();
      }
      if (sortProp != null && sortSchemaName == null) {
        System.err.println(
            "WARNING: search query schema '"
                + schemaName
                + "' has a 'sort' property but sort type could not be resolved"
                + " (items.$ref missing — check YAML indentation). Generated DTO will use List<Object>.");
      }

      // Extract filter schema from properties.filter.allOfRefs or properties.filter.$ref
      String filterSchemaName = null;
      final var filterProp = node.properties().get("filter");
      if (filterProp != null) {
        if (!filterProp.allOfRefs().isEmpty()) {
          filterSchemaName =
              toSchemaKey(filterProp.allOfRefs().get(0), schemaDef.fileName()).schemaName();
        } else if (filterProp.ref() != null) {
          filterSchemaName = toSchemaKey(filterProp.ref(), schemaDef.fileName()).schemaName();
        }
      }

      entries.add(
          new SearchQuerySchemaEntry(
              schemaName, entityName, sortSchemaName, filterSchemaName, paginationType));
    }

    entries.sort(Comparator.comparing(SearchQuerySchemaEntry::schemaName));
    return entries;
  }

  // ---------------------------------------------------------------------------
  // Type resolution
  // ---------------------------------------------------------------------------

  private static String resolveSchemaType(String schemaName) {
    if (schemaName == null) return null;
    // Check search query request DTO map first.
    final var searchRequestDto = SEARCH_REQUEST_DTO_MAP.get(schemaName);
    if (searchRequestDto != null) return searchRequestDto;
    if (AVAILABLE_STRICT_CONTRACTS.contains(schemaName)) return dtoClassName(schemaName);
    return schemaName; // fallback to original name
  }

  /** Collects all imports needed for a Java type, including inner generic type arguments. */
  private static void collectTypeImports(String javaType, Set<String> imports) {
    String raw = javaType.contains("<") ? javaType.substring(0, javaType.indexOf('<')) : javaType;
    if (javaType.contains("<")) {
      String inner = javaType.substring(javaType.indexOf('<') + 1, javaType.lastIndexOf('>'));
      collectTypeImports(inner.replace("@Valid ", "").trim(), imports);
      collectTypeImports(raw, imports);
      return;
    }
    String imp = resolveImport(raw);
    if (imp != null) imports.add(imp);
  }

  /** Resolves a Java type name to its import, or null if it's a primitive or java.lang type. */
  private static String resolveImport(String javaType) {
    // Strip generics to get the raw type
    String raw = javaType.contains("<") ? javaType.substring(0, javaType.indexOf('<')) : javaType;
    // Handle generic inner types too (e.g. List<MultipartFile>)
    if (javaType.contains("<")) {
      String outerImport = resolveImport(raw);
      return outerImport; // simplified: caller should use collectTypeImports for full resolution
    }
    if (raw.equals("String") || raw.equals("Object") || raw.equals("Void")) return null;
    if (raw.equals("List")) return "java.util.List";
    if (raw.equals("Part")) return "jakarta.servlet.http.Part";
    if (raw.equals("StreamingResponseBody"))
      return "org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody";
    if (raw.equals("MultipartFile")) return "org.springframework.web.multipart.MultipartFile";
    if (raw.equals("Resource")) return "org.springframework.core.io.Resource";
    if (AVAILABLE_STRICT_CONTRACTS.contains(raw) || SEARCH_REQUEST_DTO_MAP.containsValue(raw))
      return TARGET_PACKAGE + "." + raw;
    if (raw.endsWith("Enum")) return TARGET_PACKAGE + "." + raw;
    return null; // unknown — caller may need to add manually
  }

  // ---------------------------------------------------------------------------
  // Naming helpers
  // ---------------------------------------------------------------------------

  /** Converts a space-separated tag name to PascalCase (e.g., "User Task" → "UserTask"). */
  private static String toPascalCase(String tagName) {
    var sb = new StringBuilder();
    for (String word : tagName.split("[\\s-]+")) {
      if (!word.isEmpty()) {
        sb.append(Character.toUpperCase(word.charAt(0)));
        sb.append(word.substring(1));
      }
    }
    return sb.toString();
  }

  /**
   * Returns the DTO class name for a schema. No "Generated" prefix — same naming as
   * MappingContractsGenerator.
   */
  private static String dtoClassName(String schemaName) {
    return schemaName;
  }

  /**
   * Returns the strict enum class name for a schema. No "Generated" prefix — same naming as
   * MappingContractsGenerator.
   */
  private static String strictEnumClassName(String schemaName) {
    return schemaName;
  }

  // ---------------------------------------------------------------------------
  // Cleanup utility
  // ---------------------------------------------------------------------------

  private static void cleanupPreviouslyGeneratedFiles(Path packagePath) throws IOException {
    try (var stream = Files.list(packagePath)) {
      for (var path :
          stream.filter(path -> path.getFileName().toString().endsWith(".java")).toList()) {
        Files.delete(path);
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Phase 4: Universal controller generation with delegate pattern
  // ---------------------------------------------------------------------------

  /** A single parameter in a spec-driven controller method. */
  private record SpecParam(String name, String javaType, ParamKind kind, boolean required) {}

  /** A response with {@code format: binary} (e.g. file download). */
  private static final String BINARY_RESPONSE_TYPE = "StreamingResponseBody";

  /** Where a parameter is bound from. */
  private enum ParamKind {
    PATH,
    QUERY,
    BODY,
    PART
  }

  /** Represents a controller method derived entirely from the spec. */
  private record UniversalEndpoint(
      String methodName,
      String returnTypeParam,
      String httpMethod,
      String path,
      int statusCode,
      List<SpecParam> params,
      boolean requiresSecondaryStorage,
      boolean bodyRequired,
      boolean isMultipart,
      boolean isBinaryResponse,
      String responseMediaType) {}

  /** Complete data for generating a universal controller and its interfaces. */
  private record UniversalControllerEntry(
      String tagPascal,
      String className,
      List<UniversalEndpoint> endpoints,
      String springConditional,
      String springProfile) {}

  /** A query parameter extracted from the spec. */
  private record SpecQueryParam(String name, String javaType, boolean required) {}

  /** A multipart form-data part extracted from the spec inline schema. */
  private record MultipartPart(String name, String javaType, boolean required) {}

  private record SpecOperation(
      String path,
      String httpMethod,
      String operationId,
      String tag,
      String requestSchema,
      String responseSchema,
      int statusCode,
      List<SpecQueryParam> pathParams,
      List<SpecQueryParam> queryParams,
      boolean bodyRequired,
      boolean requiresSecondaryStorage,
      boolean codeGenerationSkip,
      String springConditional,
      String springProfile,
      boolean isMultipart,
      List<MultipartPart> multipartParts,
      boolean isBinaryResponse,
      String responseMediaType) {}

  // -- Spec parsing --

  /** Reads keys.yaml and returns the set of schema names that derive from LongKey via allOf. */
  private static Set<String> loadLongKeySchemas(Path specDir) throws IOException {
    var keysFile = specDir.resolve("keys.yaml");
    if (!Files.exists(keysFile)) return Set.of();
    var lines = Files.readAllLines(keysFile, StandardCharsets.UTF_8);
    var result = new HashSet<String>();
    String currentSchema = null;
    boolean inAllOf = false;
    for (var line : lines) {
      var t = trimmed(line);
      int ind = indent(line);
      if (ind == 4 && t.endsWith(":") && !t.contains(" ")) {
        currentSchema = t.substring(0, t.length() - 1);
        inAllOf = false;
      } else if (ind == 6 && t.equals("allOf:") && currentSchema != null) {
        inAllOf = true;
      } else if (inAllOf && t.contains("LongKey") && currentSchema != null) {
        if (!COMPOUND_KEY_EXCLUSIONS.contains(currentSchema)) {
          result.add(currentSchema);
        }
        currentSchema = null;
        inAllOf = false;
      } else if (ind <= 4 && !t.isEmpty()) {
        inAllOf = false;
      }
    }
    return result;
  }

  /** Reads all YAML files in the spec directory and extracts operation metadata. */
  private static List<SpecOperation> parseSpecOperations(Path specDir) throws IOException {
    var longKeySchemas = loadLongKeySchemas(specDir);
    var ops = new ArrayList<SpecOperation>();
    try (var stream = Files.list(specDir)) {
      var yamlFiles =
          stream
              .filter(p -> p.getFileName().toString().endsWith(".yaml"))
              .sorted(Comparator.comparing(p -> p.getFileName().toString()))
              .toList();
      for (var file : yamlFiles) {
        ops.addAll(parseOperationsFromFile(file, longKeySchemas));
      }
    }
    return ops;
  }

  private static List<SpecOperation> parseOperationsFromFile(
      Path file, Set<String> longKeySchemas) throws IOException {
    var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    var ops = new ArrayList<SpecOperation>();

    boolean inPaths = false;
    String currentPath = null;
    String currentMethod = null;

    // Per-operation state
    String operationId = null;
    String tag = null;
    String requestSchema = null;
    String responseSchema = null;
    int primaryStatusCode = -1;
    var pathParams = new ArrayList<SpecQueryParam>();
    var queryParams = new ArrayList<SpecQueryParam>();
    boolean bodyRequired = false; // OpenAPI 3.0: requestBody.required defaults to false
    boolean requiresSecondaryStorage = false;
    boolean codeGenerationSkip = false;
    String springConditional = null;
    String springProfile = null;
    boolean isMultipart = false;
    var multipartParts = new ArrayList<MultipartPart>();
    boolean isBinaryResponse = false;
    String responseMediaType = null;

    // Section tracking within an operation
    String section = null; // "tags", "parameters", "requestBody", "responses"
    int sectionIndent = -1;
    boolean inResponseSuccess = false; // inside a 2xx status code block

    for (int i = 0; i < lines.size(); i++) {
      var line = lines.get(i);
      if (isIgnorable(line)) continue;

      int ind = indent(line);
      String tr = trimmed(line);

      // Top level
      if (ind == 0) {
        // Flush previous operation
        if (currentMethod != null && operationId != null) {
          ops.add(
              new SpecOperation(
                  currentPath,
                  currentMethod,
                  operationId,
                  tag,
                  requestSchema,
                  responseSchema,
                  primaryStatusCode,
                  List.copyOf(pathParams),
                  List.copyOf(queryParams),
                  bodyRequired,
                  requiresSecondaryStorage,
                  codeGenerationSkip,
                  springConditional,
                  springProfile,
                  isMultipart,
                  List.copyOf(multipartParts),
                  isBinaryResponse,
                  responseMediaType));
        }
        resetOpState:
        {
          currentPath = null;
          currentMethod = null;
          operationId = null;
          tag = null;
          requestSchema = null;
          responseSchema = null;
          primaryStatusCode = -1;
          pathParams.clear();
          queryParams.clear();
          section = null;
          sectionIndent = -1;
          inResponseSuccess = false;
          bodyRequired = false;
          requiresSecondaryStorage = false;
          codeGenerationSkip = false;
          springConditional = null;
          springProfile = null;
          isMultipart = false;
          multipartParts.clear();
          isBinaryResponse = false;
          responseMediaType = null;
        }
        inPaths = tr.equals("paths:");
        continue;
      }

      if (!inPaths) continue;

      // Path entry (indent 2): "  /some/path:" or "  /some/{param}/action:"
      if (ind == 2 && tr.endsWith(":") && tr.startsWith("/")) {
        if (currentMethod != null && operationId != null) {
          ops.add(
              new SpecOperation(
                  currentPath,
                  currentMethod,
                  operationId,
                  tag,
                  requestSchema,
                  responseSchema,
                  primaryStatusCode,
                  List.copyOf(pathParams),
                  List.copyOf(queryParams),
                  bodyRequired,
                  requiresSecondaryStorage,
                  codeGenerationSkip,
                  springConditional,
                  springProfile,
                  isMultipart,
                  List.copyOf(multipartParts),
                  isBinaryResponse,
                  responseMediaType));
        }
        currentPath = tr.substring(0, tr.length() - 1);
        currentMethod = null;
        operationId = null;
        tag = null;
        requestSchema = null;
        responseSchema = null;
        primaryStatusCode = -1;
        pathParams.clear();
        queryParams.clear();
        section = null;
        sectionIndent = -1;
        inResponseSuccess = false;
        bodyRequired = false;
        requiresSecondaryStorage = false;
        codeGenerationSkip = false;
        springConditional = null;
        springProfile = null;
        isMultipart = false;
        multipartParts.clear();
        isBinaryResponse = false;
        responseMediaType = null;
        continue;
      }

      // HTTP method (indent 4)
      if (ind == 4 && currentPath != null && tr.endsWith(":")) {
        String method = tr.substring(0, tr.length() - 1);
        if (Set.of("get", "post", "put", "delete", "patch").contains(method)) {
          if (currentMethod != null && operationId != null) {
            ops.add(
                new SpecOperation(
                    currentPath,
                    currentMethod,
                    operationId,
                    tag,
                    requestSchema,
                    responseSchema,
                    primaryStatusCode,
                    List.copyOf(pathParams),
                    List.copyOf(queryParams),
                    bodyRequired,
                    requiresSecondaryStorage,
                    codeGenerationSkip,
                    springConditional,
                    springProfile,
                    isMultipart,
                    List.copyOf(multipartParts),
                    isBinaryResponse,
                    responseMediaType));
          }
          currentMethod = method;
          operationId = null;
          tag = null;
          requestSchema = null;
          responseSchema = null;
          primaryStatusCode = -1;
          pathParams.clear();
          queryParams.clear();
          section = null;
          sectionIndent = -1;
          inResponseSuccess = false;
          bodyRequired = false;
          requiresSecondaryStorage = false;
          codeGenerationSkip = false;
          springConditional = null;
          springProfile = null;
          isMultipart = false;
          multipartParts.clear();
          isBinaryResponse = false;
          responseMediaType = null;
          continue;
        }
      }

      // Inside an operation body (indent >= 6)
      if (ind >= 6 && currentMethod != null) {
        // Track section boundaries at indent 6
        if (ind == 6) {
          section = null;
          sectionIndent = -1;
          inResponseSuccess = false;
          if (tr.startsWith("operationId:")) {
            operationId = unquote(tr.substring("operationId:".length()).trim());
          } else if (tr.equals("tags:")) {
            section = "tags";
            sectionIndent = 6;
          } else if (tr.startsWith("requestBody:")) {
            section = "requestBody";
            sectionIndent = 6;
          } else if (tr.equals("responses:")) {
            section = "responses";
            sectionIndent = 6;
          } else if (tr.equals("parameters:")) {
            section = "parameters";
            sectionIndent = 6;
          } else if (tr.equals("x-requires-secondary-storage: true")) {
            requiresSecondaryStorage = true;
          } else if (tr.equals("x-code-generation: skip")) {
            codeGenerationSkip = true;
          } else if (tr.startsWith("x-spring-conditional:")) {
            springConditional = tr.substring("x-spring-conditional:".length()).trim();
          } else if (tr.startsWith("x-spring-profile:")) {
            springProfile = tr.substring("x-spring-profile:".length()).trim();
          }
          continue;
        }

        // Tags
        if ("tags".equals(section) && ind == 8 && tr.startsWith("- ") && tag == null) {
          tag = tr.substring(2).trim();
          continue;
        }

        // Parameters — detect path params and query params
        if ("parameters".equals(section)) {
          if (ind == 8 && tr.startsWith("- name:")) {
            String paramName = unquote(tr.substring("- name:".length()).trim());
            // Look ahead at following lines to determine param kind, type, and schema $ref
            String paramIn = null;
            boolean paramRequired = false;
            String paramType = "String"; // default
            String paramSchemaRef = null;
            for (int j = i + 1; j < lines.size() && j < i + 10; j++) {
              String nextTr = trimmed(lines.get(j));
              if (nextTr.startsWith("- name:") || indent(lines.get(j)) <= 8) break;
              if (nextTr.equals("in: path")) paramIn = "path";
              else if (nextTr.equals("in: query")) paramIn = "query";
              else if (nextTr.startsWith("required:") && nextTr.contains("true")) paramRequired = true;
              else if (nextTr.startsWith("type: boolean")) paramType = "Boolean";
              else if (nextTr.startsWith("type: integer")) paramType = "Integer";
              else if (nextTr.startsWith("type: number")) paramType = "Double";
              else if (nextTr.startsWith("$ref:"))
                paramSchemaRef = unquote(nextTr.substring("$ref:".length()).trim());
            }
            if ("path".equals(paramIn)) {
              // Determine path param type from schema $ref: keys.yaml refs are Long keys
              String pathType = "String";
              if (paramSchemaRef != null && paramSchemaRef.contains("keys.yaml")) {
                // Extract schema name from keys.yaml ref, check if it derives from LongKey
                String schemaName =
                    paramSchemaRef.substring(paramSchemaRef.lastIndexOf('/') + 1);
                if (longKeySchemas.contains(schemaName)) {
                  pathType = "Long";
                }
              } else if (paramSchemaRef != null
                  && paramSchemaRef.startsWith("#/components/schemas/")) {
                // Resolve local ref: check if the local schema derives from LongKey
                String localSchemaName =
                    paramSchemaRef.substring("#/components/schemas/".length());
                if (longKeySchemas.contains(localSchemaName)) {
                  pathType = "Long";
                } else {
                  // Check if local schema references keys.yaml LongKey descendants
                  for (int k = 0; k < lines.size(); k++) {
                    String kt = trimmed(lines.get(k));
                    if (kt.equals(localSchemaName + ":") && indent(lines.get(k)) == 4) {
                      for (int m = k + 1; m < lines.size() && m < k + 20; m++) {
                        if (indent(lines.get(m)) <= 4 && m > k + 1) break;
                        if (trimmed(lines.get(m)).contains("keys.yaml")) {
                          pathType = "Long";
                          break;
                        }
                      }
                      break;
                    }
                  }
                }
              }
              pathParams.add(new SpecQueryParam(paramName, pathType, true));
            } else if ("query".equals(paramIn))
              queryParams.add(new SpecQueryParam(paramName, paramType, paramRequired));
          }
          continue;
        }

        // RequestBody — capture required flag (indent 8), detect multipart/form-data vs JSON body.
        // For JSON: extract the top-level schema $ref (indent 14).
        // For multipart: parse inline schema properties into MultipartPart records.
        if ("requestBody".equals(section)) {
          if (ind == 8 && tr.startsWith("required:")) {
            bodyRequired = tr.contains("true");
          } else if (ind == 10 && tr.equals("multipart/form-data:")) {
            isMultipart = true;
            // Forward-scan to extract multipart properties and required list
            parseMultipartSchema(lines, i + 1, multipartParts);
          } else if (ind == 14 && requestSchema == null && !isMultipart) {
            String ref = extractRef(tr);
            if (ref != null) {
              String schema = extractSchemaFromRef(ref);
              if (schema != null) requestSchema = schema;
            }
          }
          continue;
        }

        // Responses — find the primary 2xx status code and its response schema
        if ("responses".equals(section)) {
          if (ind == 8) {
            // Status code line, e.g. '"200":' or '"204":'
            String codeStr = tr.replaceAll("[^0-9]", "");
            if (codeStr.length() >= 3) {
              int code = Integer.parseInt(codeStr.substring(0, 3));
              if (code >= 200 && code < 300 && primaryStatusCode < 0) {
                primaryStatusCode = code;
                inResponseSuccess = true;
              } else {
                inResponseSuccess = false;
              }
            }
          } else if (ind > 8 && inResponseSuccess && responseSchema == null) {
            // Detect binary response (format: binary → StreamingResponseBody)
            if (tr.equals("format: binary")) {
              isBinaryResponse = true;
            }
            // Detect non-JSON response media type (e.g. text/xml:)
            if (ind == 12
                && tr.endsWith(":")
                && !tr.startsWith("application/json")
                && !tr.startsWith("application/problem")
                && !tr.startsWith("schema")
                && !tr.startsWith("description")
                && !tr.startsWith("content")) {
              String mediaCandidate = tr.substring(0, tr.length() - 1).strip();
              if (mediaCandidate.contains("/")) {
                responseMediaType = mediaCandidate;
              }
            }
            String ref = extractRef(tr);
            if (ref != null) {
              String schema = extractSchemaFromRef(ref);
              if (schema != null && !"ProblemDetail".equals(schema)) {
                responseSchema = schema;
              }
            }
          }
          continue;
        }
      }
    }

    // Flush last operation
    if (currentMethod != null && operationId != null) {
      ops.add(
          new SpecOperation(
              currentPath,
              currentMethod,
              operationId,
              tag,
              requestSchema,
              responseSchema,
              primaryStatusCode,
              List.copyOf(pathParams),
              List.copyOf(queryParams),
              bodyRequired,
              requiresSecondaryStorage,
              codeGenerationSkip,
              springConditional,
              springProfile,
              isMultipart,
              List.copyOf(multipartParts),
              isBinaryResponse,
              responseMediaType));
    }
    return ops;
  }

  /** Extracts a schema name from a $ref like '#/components/schemas/Foo' or 'bar.yaml#/.../Foo'. */
  private static String extractSchemaFromRef(String ref) {
    String clean = unquote(ref);
    int idx = clean.indexOf("/components/schemas/");
    if (idx < 0) return null;
    return clean.substring(idx + "/components/schemas/".length());
  }

  /**
   * Forward-scans from just after {@code multipart/form-data:} to extract the inline schema
   * properties into {@code MultipartPart} records.
   */
  private static void parseMultipartSchema(List<String> lines, int startIndex, List<MultipartPart> parts) {
    boolean inProperties = false;
    boolean inRequired = false;
    String currentPropName = null;
    boolean currentIsArray = false;
    boolean currentIsBinary = false;
    String currentRefSchema = null;
    String currentSimpleType = null;
    var requiredNames = new ArrayList<String>();
    var rawParts = new ArrayList<String[]>(); // [name, javaType] pairs

    for (int j = startIndex; j < lines.size(); j++) {
      String line = lines.get(j);
      if (isIgnorable(line)) continue;
      int ind = indent(line);
      String tr = trimmed(line);

      // We're inside the multipart/form-data block (indent 12+).
      // Stop if we encounter something at indent <= 10 (sibling or parent).
      if (ind <= 10) break;

      // schema: at indent 12 — just skip, properties/required are at 14
      if (ind == 12) continue;

      // At indent 14: sections within the schema
      if (ind == 14) {
        // Flush previous property if any
        if (currentPropName != null && inProperties) {
          rawParts.add(
              new String[] {
                currentPropName,
                resolveMultipartType(
                    currentIsArray, currentIsBinary, currentRefSchema, currentSimpleType)
              });
          currentPropName = null;
          currentIsArray = false;
          currentIsBinary = false;
          currentRefSchema = null;
          currentSimpleType = null;
        }
        if (tr.equals("properties:")) {
          inProperties = true;
          inRequired = false;
        } else if (tr.equals("required:")) {
          inProperties = false;
          inRequired = true;
        }
        continue;
      }

      // At indent 16: property names (in properties) or required items (in required)
      if (ind == 16) {
        if (inProperties) {
          // Flush previous property
          if (currentPropName != null) {
            rawParts.add(
                new String[] {
                  currentPropName,
                  resolveMultipartType(
                      currentIsArray, currentIsBinary, currentRefSchema, currentSimpleType)
                });
          }
          currentPropName = tr.endsWith(":") ? tr.substring(0, tr.length() - 1) : null;
          currentIsArray = false;
          currentIsBinary = false;
          currentRefSchema = null;
          currentSimpleType = null;
        } else if (inRequired && tr.startsWith("- ")) {
          requiredNames.add(tr.substring(2).trim());
        }
        continue;
      }

      // At indent 18: property type info
      if (ind == 18 && inProperties && currentPropName != null) {
        if (tr.equals("format: binary")) currentIsBinary = true;
        else if (tr.equals("type: array")) currentIsArray = true;
        else if (tr.equals("type: string") && currentSimpleType == null) currentSimpleType = "String";
        else if (tr.equals("type: boolean")) currentSimpleType = "Boolean";
        else if (tr.equals("type: integer")) currentSimpleType = "Integer";
        else {
          String ref = extractRef(tr);
          if (ref != null) {
            String schema = extractSchemaFromRef(ref);
            if (schema != null) currentRefSchema = schema;
          }
        }
        continue;
      }

      // At indent 20+: items within an array property
      if (ind >= 20 && inProperties && currentPropName != null && currentIsArray) {
        if (tr.equals("format: binary")) currentIsBinary = true;
        else {
          String ref = extractRef(tr);
          if (ref != null) {
            String schema = extractSchemaFromRef(ref);
            if (schema != null) currentRefSchema = schema;
          }
        }
        continue;
      }
    }

    // Flush final property
    if (currentPropName != null && inProperties) {
      rawParts.add(
          new String[] {
            currentPropName,
            resolveMultipartType(currentIsArray, currentIsBinary, currentRefSchema, currentSimpleType)
          });
    }

    // Build MultipartPart records with required info
    for (var raw : rawParts) {
      parts.add(new MultipartPart(raw[0], raw[1], requiredNames.contains(raw[0])));
    }
  }

  /** Resolves the Java type for a multipart form-data property. */
  private static String resolveMultipartType(
      boolean isArray, boolean isBinary, String refSchema, String simpleType) {
    if (isBinary && isArray) return "List<Part>";
    if (isBinary) return "Part";
    if (refSchema != null) {
      // Check whether the referenced schema is a known DTO type (contract or search request).
      if (AVAILABLE_STRICT_CONTRACTS.contains(refSchema)
          || SEARCH_REQUEST_DTO_MAP.containsKey(refSchema)) {
        String resolved = resolveSchemaType(refSchema);
        return isArray ? "List<" + resolved + ">" : resolved;
      }
      // Not a known DTO — treat as scalar alias (e.g. TenantId → type: string).
      return isArray ? "List<String>" : "String";
    }
    if (simpleType != null) return simpleType;
    return "String"; // fallback
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

  // -- Universal controller entry builder --

  /**
   * Builds universal controller entries for ALL tags, entirely from the spec. No dependency on
   * generated API interfaces. Operations with {@code x-code-generation: skip} are excluded from
   * generation.
   */
  private static List<UniversalControllerEntry> buildUniversalControllerEntries(Path specDir)
      throws IOException {
    // Parse spec for all operations
    final var ops = parseSpecOperations(specDir);
    final var opsByTag = new LinkedHashMap<String, List<SpecOperation>>();
    for (var op : ops) {
      if (op.tag() != null && op.operationId() != null) {
        opsByTag.computeIfAbsent(op.tag(), k -> new ArrayList<>()).add(op);
      }
    }

    var entries = new ArrayList<UniversalControllerEntry>();

    for (var tagEntry : opsByTag.entrySet()) {
      String tagName = tagEntry.getKey();
      String tagPascal = toPascalCase(tagName);
      var tagOps = tagEntry.getValue();

      // Collect spring conditional/profile from all operations in the tag (should be uniform)
      String tagSpringConditional = null;
      String tagSpringProfile = null;
      for (var op : tagOps) {
        if (op.springConditional() != null) tagSpringConditional = op.springConditional();
        if (op.springProfile() != null) tagSpringProfile = op.springProfile();
      }

      var endpoints = new ArrayList<UniversalEndpoint>();
      for (var op : tagOps) {
        // Skip operations marked with x-code-generation: skip
        if (op.codeGenerationSkip()) {
          System.out.println("  skip " + op.operationId() + " (x-code-generation: skip)");
          continue;
        }

        // Build params: path params first, then query params, then request body or multipart parts
        var params = new ArrayList<SpecParam>();
        for (var pp : op.pathParams()) {
          params.add(new SpecParam(pp.name(), pp.javaType(), ParamKind.PATH, true));
        }
        for (var qp : op.queryParams()) {
          params.add(new SpecParam(qp.name(), qp.javaType(), ParamKind.QUERY, qp.required()));
        }
        if (op.isMultipart()) {
          // Multipart: each part becomes a PART param
          for (var part : op.multipartParts()) {
            params.add(new SpecParam(part.name(), part.javaType(), ParamKind.PART, part.required()));
          }
        } else if (op.requestSchema() != null) {
          String resolvedType = resolveSchemaType(op.requestSchema());
          // Convert schema name to param name (camelCase of schema name)
          String paramName =
              Character.toLowerCase(op.requestSchema().charAt(0)) + op.requestSchema().substring(1);
          params.add(new SpecParam(paramName, resolvedType, ParamKind.BODY, true));
        }

        // Determine return type from response schema
        String returnTypeParam;
        if (op.isBinaryResponse()) {
          returnTypeParam = BINARY_RESPONSE_TYPE;
        } else if (op.responseSchema() != null) {
          returnTypeParam = resolveSchemaType(op.responseSchema());
        } else {
          returnTypeParam = "Void";
        }

        endpoints.add(
            new UniversalEndpoint(
                op.operationId(),
                returnTypeParam,
                op.httpMethod(),
                op.path(),
                op.statusCode(),
                params,
                op.requiresSecondaryStorage(),
                op.bodyRequired(),
                op.isMultipart(),
                op.isBinaryResponse(),
                op.responseMediaType()));
      }

      // Skip tags where all operations were excluded
      if (endpoints.isEmpty()) continue;

      entries.add(
          new UniversalControllerEntry(
              tagPascal,
              tagPascal + "Controller", // NO "Generated" prefix
              endpoints,
              tagSpringConditional,
              tagSpringProfile));
    }

    entries.sort(Comparator.comparing(UniversalControllerEntry::className));
    System.out.println(entries.size() + " universal controller entries built.");
    return entries;
  }

  /** Renders a universal controller that delegates ALL methods to the service adapter. */
  private static String renderUniversalController(UniversalControllerEntry ctrl) {
    final var sb = new StringBuilder();
    sb.append(
        """
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 */
package %s;

"""
            .formatted(CONTROLLER_PACKAGE));

    // Collect imports
    final var imports = new LinkedHashSet<String>();
    imports.add("jakarta.annotation.Generated");
    imports.add("org.springframework.http.ResponseEntity");
    imports.add("org.springframework.web.bind.annotation.RequestMapping");
    imports.add("org.springframework.web.bind.annotation.RequestMethod");
    imports.add("io.camunda.zeebe.gateway.rest.controller.CamundaRestController");
    imports.add("io.camunda.security.auth.CamundaAuthenticationProvider");

    // Determine which Spring binding annotations are needed
    boolean hasPathVar = false, hasRequestBody = false, hasRequestParam = false,
        hasRequestPart = false;
    boolean hasMultipart = false;
    for (var ep : ctrl.endpoints()) {
      if (ep.isMultipart()) hasMultipart = true;
      for (var p : ep.params()) {
        switch (p.kind()) {
          case PATH -> hasPathVar = true;
          case BODY -> hasRequestBody = true;
          case QUERY -> hasRequestParam = true;
          case PART -> hasRequestPart = true;
        }
      }
    }
    if (hasPathVar) imports.add("org.springframework.web.bind.annotation.PathVariable");
    if (hasRequestBody) imports.add("org.springframework.web.bind.annotation.RequestBody");
    if (hasRequestParam) imports.add("org.springframework.web.bind.annotation.RequestParam");
    if (hasRequestPart) imports.add("org.springframework.web.bind.annotation.RequestPart");
    if (hasMultipart) imports.add("org.springframework.http.MediaType");

    // Import Part if any param uses it (directly or in List<Part>)
    boolean needsPart =
        ctrl.endpoints().stream()
            .flatMap(ep -> ep.params().stream())
            .anyMatch(p -> p.javaType().equals("Part") || p.javaType().equals("List<Part>"));
    if (needsPart) imports.add("jakarta.servlet.http.Part");

    // Import List if any param uses List<>
    boolean needsList =
        ctrl.endpoints().stream()
            .flatMap(ep -> ep.params().stream())
            .anyMatch(p -> p.javaType().startsWith("List<"));
    if (needsList) imports.add("java.util.List");

    // Import all types referenced in method parameter signatures
    // (return types are not imported — controllers use ResponseEntity<Object>)
    for (var ep : ctrl.endpoints()) {
      for (var param : ep.params()) {
        collectTypeImports(param.javaType(), imports);
      }
    }

    // Import StreamingResponseBody if any endpoint has a binary response
    boolean hasBinaryResponse = ctrl.endpoints().stream().anyMatch(UniversalEndpoint::isBinaryResponse);
    if (hasBinaryResponse) {
      imports.add(
          "org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody");
    }

    // Import @RequiresSecondaryStorage if any endpoint needs it
    boolean hasRequiresSecondaryStorage =
        ctrl.endpoints().stream().anyMatch(UniversalEndpoint::requiresSecondaryStorage);
    if (hasRequiresSecondaryStorage) {
      imports.add("io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage");
    }

    // Import Spring conditional/profile annotations if declared
    if (ctrl.springConditional() != null) {
      imports.add(ctrl.springConditional());
    }
    if (ctrl.springProfile() != null) {
      imports.add("org.springframework.context.annotation.Profile");
    }

    // Also import the generated adapter from ADAPTER_PACKAGE
    imports.add(ADAPTER_PACKAGE + "." + ctrl.tagPascal() + "ServiceAdapter");

    String adapterType = ctrl.tagPascal() + "ServiceAdapter";

    // Write imports
    imports.stream()
        .filter(i -> !i.startsWith("static "))
        .sorted()
        .forEach(i -> sb.append("import ").append(i).append(";\n"));

    // Class declaration — with optional spring conditional/profile annotations
    sb.append("\n");
    if (ctrl.springProfile() != null) {
      sb.append("@Profile(\"").append(ctrl.springProfile()).append("\")\n");
    }
    if (ctrl.springConditional() != null) {
      String simpleName =
          ctrl.springConditional()
              .substring(ctrl.springConditional().lastIndexOf('.') + 1);
      sb.append("@").append(simpleName).append("\n");
    }
    sb.append("@CamundaRestController\n");
    // UserTask and Cluster require v1 backward compatibility (see TopologyController on main).
    if ("UserTask".equals(ctrl.tagPascal()) || "Cluster".equals(ctrl.tagPascal())) {
      sb.append("@RequestMapping(path = {\"/v1\", \"/v2\"})\n");
    } else {
      sb.append("@RequestMapping(\"/v2\")\n");
    }
    sb.append(
        "@Generated(value = \"io.camunda.zeebe.gateway.rest.tools.RestControllersGenerator\")\n");
    sb.append("public class ").append(ctrl.className()).append(" {\n\n");

    // Fields
    sb.append("  private final ").append(adapterType).append(" serviceAdapter;\n");
    sb.append("  private final CamundaAuthenticationProvider authenticationProvider;\n\n");

    // Constructor
    sb.append("  public ").append(ctrl.className()).append("(\n");
    sb.append("      final ").append(adapterType).append(" serviceAdapter,\n");
    sb.append("      final CamundaAuthenticationProvider authenticationProvider) {\n");
    sb.append("    this.serviceAdapter = serviceAdapter;\n");
    sb.append("    this.authenticationProvider = authenticationProvider;\n");
    sb.append("  }\n");

    // Endpoint methods — all follow the same delegate pattern
    for (var ep : ctrl.endpoints()) {
      sb.append("\n");
      renderUniversalEndpoint(sb, ep);
    }

    sb.append("}\n");
    return sb.toString();
  }

  /** Maps a lowercase HTTP method to Spring's RequestMethod enum constant name. */
  private static String springRequestMethod(String httpMethod) {
    return httpMethod.toUpperCase();
  }

  /** Renders a single controller method using the universal validate → fold → delegate pattern. */
  private static void renderUniversalEndpoint(StringBuilder sb, UniversalEndpoint ep) {
    // Use Object for non-Void return types: the adapter returns the response body
    // via SearchQueryResponseMapper whose adaptType returns <T>T (erased to Object).
    // Using a concrete protocol-model type would cause javac to insert a checkcast
    // that fails at runtime because the actual object is a StrictSearchQueryResult.
    // Binary response (e.g. file download) returns StreamingResponseBody for proper streaming.
    String returnType;
    if (ep.isBinaryResponse()) {
      returnType = "StreamingResponseBody";
    } else if (ep.returnTypeParam() == null || "Void".equals(ep.returnTypeParam())) {
      returnType = "Void";
    } else {
      returnType = "Object";
    }

    // Determine media types
    boolean hasRequestBody = ep.params().stream().anyMatch(p -> p.kind() == ParamKind.BODY);
    boolean hasMultipart = ep.isMultipart();
    boolean isBinary = ep.isBinaryResponse();

    // @RequiresSecondaryStorage annotation (before @RequestMapping)
    if (ep.requiresSecondaryStorage()) {
      sb.append("  @RequiresSecondaryStorage\n");
    }

    // @RequestMapping annotation with HTTP method and path
    sb.append("  @RequestMapping(\n");
    sb.append("      method = RequestMethod.")
        .append(springRequestMethod(ep.httpMethod()))
        .append(",\n");
    sb.append("      value = \"").append(ep.path()).append("\"");
    if (hasMultipart) {
      sb.append(",\n      consumes = { MediaType.MULTIPART_FORM_DATA_VALUE }");
    } else if (hasRequestBody) {
      sb.append(",\n      consumes = { \"application/json\" }");
    }
    if (isBinary) {
      // Binary responses (e.g. file downloads) don't constrain content type
      sb.append(",\n      produces = {}");
    } else if (ep.responseMediaType() != null) {
      // Non-JSON response (e.g. text/xml) — include both the custom type and problem+json
      sb.append(",\n      produces = { \"")
          .append(ep.responseMediaType())
          .append("\", \"application/problem+json\" }");
    } else {
      sb.append(",\n      produces = { \"application/json\", \"application/problem+json\" }");
    }
    sb.append(")\n");

    // Method signature
    sb.append("  public ResponseEntity<")
        .append(returnType)
        .append("> ")
        .append(ep.methodName())
        .append("(");
    if (!ep.params().isEmpty()) {
      sb.append("\n");
      for (int i = 0; i < ep.params().size(); i++) {
        var param = ep.params().get(i);
        String annotation =
            switch (param.kind()) {
              case PATH -> "@PathVariable(\"" + param.name() + "\") ";
              case BODY ->
                  ep.bodyRequired() ? "@RequestBody " : "@RequestBody(required = false) ";
              case QUERY ->
                  "@RequestParam(name = \"" + param.name() + "\", required = " + false + ") ";
              case PART ->
                  param.required()
                      ? "@RequestPart(\"" + param.name() + "\") "
                      : "@RequestPart(value = \"" + param.name() + "\", required = false) ";
            };
        sb.append("      ")
            .append(annotation)
            .append("final ")
            .append(param.javaType())
            .append(" ")
            .append(param.name());
        if (i < ep.params().size() - 1) sb.append(",");
        sb.append("\n");
      }
      sb.append("  ");
    }
    sb.append(") {\n");

    // Resolve authentication context once per request.
    sb.append("    final var authentication =\n");
    sb.append("        authenticationProvider.getCamundaAuthentication();\n");

    // Body: delegate directly to service adapter
    String adapterArgs =
        ep.params().stream().map(SpecParam::name).collect(Collectors.joining(", "));
    String adapterCall =
        "serviceAdapter."
            + ep.methodName()
            + "("
            + (adapterArgs.isEmpty() ? "authentication" : adapterArgs + ", authentication")
            + ")";

    sb.append("    return ").append(adapterCall).append(";\n");
    sb.append("  }\n");
  }

  /** Renders a ServiceAdapter interface with methods for all operations. */
  private static String renderServiceAdapterInterface(UniversalControllerEntry ctrl) {
    final var sb = new StringBuilder();
    sb.append(
        """
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 */
package %s;

"""
            .formatted(ADAPTER_PACKAGE));

    final var imports = new LinkedHashSet<String>();
    imports.add("jakarta.annotation.Generated");
    imports.add("org.springframework.http.ResponseEntity");
    imports.add("io.camunda.security.auth.CamundaAuthentication");

    // Import param types (return types are not imported — adapters use ResponseEntity<Object>)
    for (var ep : ctrl.endpoints()) {
      for (var param : ep.params()) {
        collectTypeImports(param.javaType(), imports);
      }
    }

    // Import StreamingResponseBody if any endpoint has a binary response
    boolean hasBinaryResponse =
        ctrl.endpoints().stream().anyMatch(UniversalEndpoint::isBinaryResponse);
    if (hasBinaryResponse) {
      imports.add(
          "org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody");
    }

    imports.stream().sorted().forEach(i -> sb.append("import ").append(i).append(";\n"));

    sb.append("\n/**\n");
    sb.append(" * Service adapter for ").append(ctrl.tagPascal()).append(" operations.\n");
    sb.append(
        " * Implements request mapping, service delegation, and response construction.\n");
    sb.append(" */\n");
    sb.append(
        "@Generated(value = \"io.camunda.zeebe.gateway.rest.tools.RestControllersGenerator\")\n");
    sb.append("public interface ").append(ctrl.tagPascal()).append("ServiceAdapter {\n");

    for (var ep : ctrl.endpoints()) {
      sb.append("\n");
      String returnType;
      if (ep.isBinaryResponse()) {
        returnType = "StreamingResponseBody";
      } else if (ep.returnTypeParam() == null || "Void".equals(ep.returnTypeParam())) {
        returnType = "Void";
      } else {
        returnType = "Object";
      }

      sb.append("  ResponseEntity<").append(returnType).append("> ").append(ep.methodName()).append("(");
      // Params from spec + CamundaAuthentication as final parameter
      sb.append("\n");
      for (int i = 0; i < ep.params().size(); i++) {
        var param = ep.params().get(i);
        sb.append("      ").append(param.javaType()).append(" ").append(param.name()).append(",\n");
      }
      sb.append("      CamundaAuthentication authentication\n");
      sb.append("  ");
      sb.append(");\n");
    }

    sb.append("}\n");
    return sb.toString();
  }

  // ---------------------------------------------------------------------------
  // Low-level YAML helpers
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Records
  // ---------------------------------------------------------------------------

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
      List<String> oneOfRefs,
      /** Inline primitive type from a oneOf branch (e.g. "string", "integer"). */
      String oneOfInlineType,
      /** Inline primitive format from a oneOf branch (e.g. "int32", "date-time"). */
      String oneOfInlineFormat,
      /** allOf $ref targets from the inline oneOf branch (e.g. enum schema refs). */
      List<String> oneOfInlineAllOfRefs,
      Map<String, Node> properties,
      boolean uniqueItems,
      Node additionalProperties,
      Long minimum,
      Long maximum,
      Integer minLength,
      Integer maxLength,
      String pattern,
      Integer minItems,
      Integer maxItems,
      String defaultValue,
      String description) {}

  private record ListParseResult(List<String> items, int lastIndex) {}

  private record RefListParseResult(List<String> refs, int lastIndex) {}

  private record PropertiesParseResult(Map<String, Node> properties, int lastIndex) {}
}
