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
  private static final String SEARCH_PACKAGE = "io.camunda.gateway.mapping.http.search";
  private static final String PROTOCOL_PACKAGE = "io.camunda.gateway.protocol.model";
  private static final String CONTROLLER_PACKAGE = "io.camunda.zeebe.gateway.rest.controller.generated";
  private static final Path PROTOCOL_MODEL_SOURCE_DIR =
      ROOT.resolve("../gateway-model/target/generated-sources/openapi/src/main/io/camunda/gateway/protocol/model")
          .normalize();
  private static final Set<String> AVAILABLE_PROTOCOL_TYPES = discoverProtocolModelTypes();
  /** Schema names for which a strict contract DTO has been generated (populated at startup). */
  private static final Set<String> AVAILABLE_STRICT_CONTRACTS = new LinkedHashSet<>();

  /**
   * Result schemas whose "Result" suffix must NOT be stripped by dtoClassName() because
   * the base name also exists as a schema (e.g. FooQuery + FooQueryResult). SearchQuery
   * collisions are excluded — they are handled by SEARCH_REQUEST_DTO_OVERRIDES instead.
   * Populated during schema loading in main().
   */
  private static final Set<String> RETAINED_RESULT_SCHEMAS = new LinkedHashSet<>();

  public static void main(String[] args) throws Exception {
    if (!Files.isDirectory(SPEC_DIR)) {
      throw new IllegalStateException("OpenAPI spec directory does not exist: " + SPEC_DIR);
    }

    final var allSchemas = loadSchemas(SPEC_DIR);
    final var responseOnlySchemas = discoverResponseOnlySchemas(SPEC_DIR, allSchemas);

    // Detect Result schema collisions: when both FooQuery and FooQueryResult exist,
    // dtoClassName() would produce the same name for both. For non-SearchQuery pairs,
    // retain "Result" in the DTO name to avoid the collision.
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
    if (!RETAINED_RESULT_SCHEMAS.isEmpty()) {
      System.out.println(RETAINED_RESULT_SCHEMAS.size()
          + " Result schema collision(s) detected (Result suffix retained): "
          + RETAINED_RESULT_SCHEMAS);
    }
    final var contractSchemas =
        allSchemas.values().stream()
            .filter(GenerateContractMappingPoc::isContractSchema)
            .sorted(Comparator.comparing(SchemaDef::schemaName))
            .toList();

    if (contractSchemas.isEmpty()) {
      throw new IllegalStateException("No contract schemas found in " + SPEC_DIR);
    }

    // Populate the strict contract name set so resolveSchemaType can use it.
    for (var schema : contractSchemas) {
      AVAILABLE_STRICT_CONTRACTS.add(schema.schemaName());
    }
    // Also include enum schemas.
    for (var schema : allSchemas.values()) {
      if (isEnumSchema(schema)) {
        AVAILABLE_STRICT_CONTRACTS.add(schema.schemaName());
      }
    }

    // Build polymorphic schema registry: parent → list of branch schema names.
    // Only includes parents whose ALL branches have strict contracts.
    // Exclude SearchQueryPageRequest — it has an explicit flat DTO generated in Phase 3.5.
    // Exclude schemas whose dtoClassName() collides with a regular contract schema (e.g.,
    // JobResult → GeneratedJobStrictContract collides with Job → GeneratedJobStrictContract).
    final var contractDtoNames = contractSchemas.stream()
        .map(s -> dtoClassName(s.schemaName()))
        .collect(Collectors.toSet());
    final var polymorphicSchemas = new LinkedHashMap<String, List<String>>();
    for (var schema : allSchemas.values()) {
      if (isPolymorphicSchema(schema)
          && !"SearchQueryPageRequest".equals(schema.schemaName())
          && !contractDtoNames.contains(dtoClassName(schema.schemaName()))) {
        final var branchNames = schema.node().oneOfRefs().stream()
            .map(GenerateContractMappingPoc::refToSchemaName)
            .toList();
        final var allBranchesHaveContracts = branchNames.stream()
            .allMatch(AVAILABLE_STRICT_CONTRACTS::contains);
        if (allBranchesHaveContracts) {
          polymorphicSchemas.put(schema.schemaName(), branchNames);
          // Register the sealed interface name so resolveSchemaType resolves to it.
          AVAILABLE_STRICT_CONTRACTS.add(schema.schemaName());
        }
      }
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

    // Phase 1.5: Generate strict enum types.
    int enumCount = 0;
    for (var schema : allSchemas.values()) {
      if (isEnumSchema(schema)) {
        final var enumFile = packagePath.resolve(strictEnumClassName(schema.schemaName()) + ".java");
        Files.writeString(
            enumFile,
            renderStrictEnum(schema.fileName(), schema.schemaName(), schema.node().enumValues()),
            StandardCharsets.UTF_8);
        System.out.println("generated enum: " + ROOT.relativize(enumFile));
        enumCount++;
      }
    }
    System.out.println(enumCount + " strict enum types generated.");

    // Phase 1.7: Generate sealed interfaces for polymorphic oneOf schemas.
    // Build reverse lookup: branch schema name → sealed interface DTO class name.
    final var branchToSealedInterface = new LinkedHashMap<String, String>();
    // Track filter property schemas for resolveTypeInfo (sealed interface type, not Object)
    final var filterPropertySchemas = new LinkedHashMap<String, SchemaDef>();
    int sealedCount = 0;
    for (var entry : polymorphicSchemas.entrySet()) {
      final var parentSchemaName = entry.getKey();
      final var branchNames = entry.getValue();
      final var sealedName = dtoClassName(parentSchemaName);

      // Find the source file for the parent schema.
      final var parentSchema = allSchemas.values().stream()
          .filter(s -> s.schemaName().equals(parentSchemaName))
          .findFirst()
          .orElseThrow();

      if (isFilterPropertySchema(parentSchema)) {
        // Filter property schema: generate wrapper record + deserializer + upgraded sealed interface.
        filterPropertySchemas.put(parentSchemaName, parentSchema);
        final var advancedFilterDtoClass = dtoClassName(branchNames.getFirst());
        final var wrapperName = sealedName.replace("StrictContract", "PlainValueStrictContract");
        final var deserializerName = sealedName.replace("StrictContract", "Deserializer");
        final var primitiveJavaType = filterPropertyPrimitiveJavaType(parentSchema.node());
        final var isLongKey = isLongKeyFilterProperty(parentSchemaName);

        // Register the advanced filter as implementing the sealed interface.
        branchToSealedInterface.put(branchNames.getFirst(), sealedName);

        // (a) Generate plain-value wrapper record
        final var wrapperFile = packagePath.resolve(wrapperName + ".java");
        Files.writeString(
            wrapperFile,
            renderFilterPlainValueWrapper(
                parentSchema.fileName(), parentSchemaName, sealedName, wrapperName, primitiveJavaType),
            StandardCharsets.UTF_8);
        System.out.println("generated filter wrapper: " + ROOT.relativize(wrapperFile));

        // (b) Generate custom deserializer
        final var deserializerFile = packagePath.resolve(deserializerName + ".java");
        Files.writeString(
            deserializerFile,
            renderFilterPropertyDeserializer(
                parentSchema.fileName(), parentSchemaName, sealedName, wrapperName,
                advancedFilterDtoClass, deserializerName, primitiveJavaType, isLongKey),
            StandardCharsets.UTF_8);
        System.out.println("generated filter deserializer: " + ROOT.relativize(deserializerFile));

        // (c) Generate sealed interface with @JsonDeserialize and both permitted types
        final var sealedFile = packagePath.resolve(sealedName + ".java");
        Files.writeString(
            sealedFile,
            renderFilterPropertySealedInterface(
                parentSchema.fileName(), parentSchemaName, sealedName,
                advancedFilterDtoClass, wrapperName, deserializerName),
            StandardCharsets.UTF_8);
        System.out.println("generated filter sealed interface: " + ROOT.relativize(sealedFile));
      } else {
        // Regular polymorphic schema: generate standard sealed interface.
        final var branchDtoClasses = branchNames.stream()
            .map(GenerateContractMappingPoc::dtoClassName)
            .toList();
        for (var branchName : branchNames) {
          branchToSealedInterface.put(branchName, sealedName);
        }
        final var sealedFile = packagePath.resolve(sealedName + ".java");
        Files.writeString(
            sealedFile,
            renderPolymorphicSealedInterface(
                parentSchema.fileName(), parentSchemaName, branchDtoClasses),
            StandardCharsets.UTF_8);
        System.out.println("generated sealed interface: " + ROOT.relativize(sealedFile));
      }
      sealedCount++;
    }
    System.out.println(sealedCount + " polymorphic sealed interfaces generated ("
        + filterPropertySchemas.size() + " filter properties).");

    // Phase 2: write files.
    int dtoCount = 0;
    for (var plan : plans) {
      final var dtoFile = packagePath.resolve(plan.dtoClass() + ".java");
      // If this schema is a branch of a polymorphic oneOf, add implements clause.
      final var sealedParent = branchToSealedInterface.get(plan.schema().schemaName());
      final var isRequestSchema = !responseOnlySchemas.contains(plan.schema().schemaName());
      Files.writeString(
          dtoFile,
          renderStrictDto(
              plan.schema().fileName(),
              plan.schema().schemaName(),
              plan.dtoClass(),
              plan.fields(),
              sealedParent,
              isRequestSchema),
          StandardCharsets.UTF_8);
      System.out.println("generated: " + ROOT.relativize(dtoFile));
      dtoCount++;

      // toProtocol() result mappers are no longer generated — strict contracts are the
      // serialization type. Adapters call GeneratedSearchQueryResponseMapper directly.
    }
    System.out.println(dtoCount + " strict contract DTOs generated.");

    // Phase 3: Generate search query response/request mappers.
    final var responseMapperFile =
        packagePath.resolve("GeneratedSearchQueryResponseMapper.java");
    Files.writeString(
        responseMapperFile, renderSearchQueryResponseMapper(), StandardCharsets.UTF_8);
    System.out.println("generated: " + ROOT.relativize(responseMapperFile));

    final var requestMapperPath = OUT_BASE.resolve(SEARCH_PACKAGE.replace('.', '/'));
    final var requestMapperFile =
        requestMapperPath.resolve("GeneratedSearchQueryRequestMapper.java");
    Files.writeString(
        requestMapperFile, renderSearchQueryRequestMapper(), StandardCharsets.UTF_8);
    System.out.println("generated: " + ROOT.relativize(requestMapperFile));

    // Phase 3.5: Generate flat page request DTO and search query request DTOs.
    final var flatPageFile = packagePath.resolve("GeneratedSearchQueryPageRequestStrictContract.java");
    Files.writeString(flatPageFile, renderFlatPageRequestDto(), StandardCharsets.UTF_8);
    System.out.println("generated: " + ROOT.relativize(flatPageFile));

    int searchRequestDtoCount = 0;
    for (var e : REQUEST_ENTRIES) {
      final var sortContract = dtoClassName(e.requestClass().replace("SearchQuery", "SearchQuerySortRequest"));
      // Derive unique entity name from requestClass (e.g. "RoleUserSearchQueryRequest" → "RoleUser")
      final var entityName = requestEntityName(e);
      final var requestDtoName = "Generated" + entityName + "SearchQueryRequestStrictContract";
      final var sortContractClass = findSortContractClass(e, packagePath);
      final var filterParamFqn = extractFilterParamFqn(e);
      final var requestDtoFile = packagePath.resolve(requestDtoName + ".java");
      Files.writeString(requestDtoFile, renderSearchQueryRequestDto(
          requestDtoName, sortContractClass, filterParamFqn), StandardCharsets.UTF_8);
      System.out.println("generated: " + ROOT.relativize(requestDtoFile));
      searchRequestDtoCount++;
    }
    System.out.println(searchRequestDtoCount + " search query request DTOs generated.");

    // Phase 4: Build pattern-specific controller entries from the OpenAPI spec.
    // These entries are retained for reference purposes (e.g. wiring data, lookup tables)
    // but controller files are no longer written — all controllers are generated by Phase 5
    // using the universal delegate pattern for a normalised controller surface.
    final var controllerOutBase =
        ROOT.resolve("../../zeebe/gateway-rest/src/main/java").normalize();
    final var controllerPackagePath =
        controllerOutBase.resolve(CONTROLLER_PACKAGE.replace('.', '/'));
    Files.createDirectories(controllerPackagePath);
    final var controllerEntries = buildControllerEntriesFromSpec();
    System.out.println(controllerEntries.size()
        + " pattern-specific controller entries built (controllers not written — Phase 5 handles all).");

    // Phase 5: Generate universal controllers + ServiceAdapter interfaces.
    // Controllers delegate directly to the service adapter; semantic validation
    // (business rules not expressible in the spec) lives in the hand-written
    // validators called by RequestMapper inside the service adapter layer.
    final var universalEntries = buildUniversalControllerEntries();
    int universalCount = 0;
    int adapterCount = 0;
    for (var ctrl : universalEntries) {
      // Controller
      final var controllerFile =
          controllerPackagePath.resolve(ctrl.className() + ".java");
      Files.writeString(controllerFile, renderUniversalController(ctrl), StandardCharsets.UTF_8);
      System.out.println("generated (universal): " + ROOT.relativize(controllerFile));
      universalCount++;

      // ServiceAdapter interface
      final var adapterFile =
          controllerPackagePath.resolve(ctrl.tagPascal() + "ServiceAdapter.java");
      Files.writeString(adapterFile, renderServiceAdapterInterface(ctrl), StandardCharsets.UTF_8);
      System.out.println("generated: " + ROOT.relativize(adapterFile));
      adapterCount++;
    }
    System.out.println(universalCount + " universal controller(s) generated.");
    System.out.println(adapterCount + " service adapter interface(s) generated.");
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
      // Skip self-deserializing types (filter properties) — they don't need mappers.
      if (f.hasStrictObjectType() && !f.typeInfo().selfDeserializing()) {
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
    return !schema.node().properties().isEmpty() || !schema.node().allOfRefs().isEmpty();
  }

  /** Returns true if the schema is a polymorphic oneOf with branch $refs. */
  private static boolean isPolymorphicSchema(SchemaDef schema) {
    return !schema.node().oneOfRefs().isEmpty();
  }

  /**
   * Returns true if the schema is a filter property: a oneOf with exactly one $ref branch
   * (the advanced filter) and one inline primitive branch (the plain value).
   * Examples: StringFilterProperty, IntegerFilterProperty, DateTimeFilterProperty,
   * ProcessDefinitionKeyFilterProperty, etc.
   */
  private static boolean isFilterPropertySchema(SchemaDef schema) {
    return schema.node().oneOfRefs().size() == 1
        && schema.node().oneOfInlineType() != null;
  }

  /**
   * Determines the Java type of the inline primitive branch in a filter property oneOf.
   * For LongKey filter properties, the inline branch resolves to "string" via allOf, but
   * at the JSON level it accepts both strings and numbers (the deserializer handles this).
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
   * Returns true if this filter property schema represents a LongKey type.
   * LongKey filter properties accept both JSON strings ("12345") and JSON numbers (12345)
   * for the plain value branch. Detected by schema name ending in "KeyFilterProperty".
   */
  private static boolean isLongKeyFilterProperty(String schemaName) {
    return schemaName.endsWith("KeyFilterProperty");
  }

  /**
   * Extracts the schema name from a $ref string.
   * E.g. "#/components/schemas/AuthorizationIdBasedRequest" → "AuthorizationIdBasedRequest"
   */
  private static String refToSchemaName(String ref) {
    final int lastSlash = ref.lastIndexOf('/');
    return lastSlash >= 0 ? ref.substring(lastSlash + 1) : ref;
  }

  /** Generates a sealed interface for a polymorphic oneOf schema. */
  private static String renderPolymorphicSealedInterface(
      String sourceFile, String schemaName, List<String> branchDtoClasses) {
    final var sealedName = dtoClassName(schemaName);
    final var permits = String.join(",\n        ", branchDtoClasses);
    final var subtypes = branchDtoClasses.stream()
        .map(cls -> "    @JsonSubTypes.Type(" + cls + ".class)")
        .collect(Collectors.joining(",\n"));
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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.annotation.Generated;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
%s
})
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public sealed interface %s permits
        %s {
}
"""
    .formatted(sourceFile, schemaName, TARGET_PACKAGE, subtypes, sealedName, permits);
  }

  /** Generates a sealed interface for a filter property oneOf with @JsonDeserialize. */
  private static String renderFilterPropertySealedInterface(
      String sourceFile, String schemaName, String sealedName,
      String advancedFilterDtoClass, String wrapperName, String deserializerName) {
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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.annotation.Generated;

@JsonDeserialize(using = %s.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public sealed interface %s permits
        %s,
        %s {
}
"""
    .formatted(sourceFile, schemaName, TARGET_PACKAGE, deserializerName,
        sealedName, advancedFilterDtoClass, wrapperName);
  }

  /** Generates a plain-value wrapper record for the inline primitive branch of a filter property. */
  private static String renderFilterPlainValueWrapper(
      String sourceFile, String schemaName, String sealedName,
      String wrapperName, String primitiveJavaType) {
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

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.annotation.Generated;

/**
 * Wrapper for the plain-value branch of the %s oneOf.
 * Represents a direct value match (implicit $eq).
 */
@JsonDeserialize(using = JsonDeserializer.None.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record %s(%s value) implements %s {
}
"""
    .formatted(sourceFile, schemaName, TARGET_PACKAGE, schemaName, wrapperName,
        primitiveJavaType, sealedName);
  }

  /** Generates a custom Jackson deserializer for a filter property sealed interface. */
  private static String renderFilterPropertyDeserializer(
      String sourceFile, String schemaName, String sealedName, String wrapperName,
      String advancedFilterDtoClass, String deserializerName,
      String primitiveJavaType, boolean isLongKey) {
    // Build the token-to-value mapping for the plain-value branch.
    final String plainValueCase;
    if ("Integer".equals(primitiveJavaType)) {
      plainValueCase = """
          case VALUE_NUMBER_INT -> new %s(p.getIntValue());
          case VALUE_STRING -> {
            try {
              yield new %s(Integer.parseInt(p.getText()));
            } catch (NumberFormatException e) {
              throw InvalidFormatException.from(p,
                "Expected integer value for filter property", p.getText(), Integer.class);
            }
          }"""
        .formatted(wrapperName, wrapperName);
    } else if (isLongKey) {
      // LongKey: accept strings AND numbers (numbers converted to string)
      plainValueCase = """
          case VALUE_STRING -> new %s(p.getText());
          case VALUE_NUMBER_INT -> new %s(String.valueOf(p.getLongValue()));"""
        .formatted(wrapperName, wrapperName);
    } else {
      // String-typed (including date-time and enum filter properties)
      plainValueCase = """
          case VALUE_STRING -> new %s(p.getText());"""
        .formatted(wrapperName);
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.annotation.Generated;
import java.io.IOException;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class %s extends JsonDeserializer<%s> {

  @Override
  public %s deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    return switch (p.currentToken()) {
%s
      case START_OBJECT -> ctxt.readValue(p, %s.class);
      default -> throw InvalidFormatException.from(p,
          "Request property cannot be parsed", p.getText(), %s.class);
    };
  }
}
"""
    .formatted(sourceFile, schemaName, TARGET_PACKAGE,
        deserializerName, sealedName, sealedName,
        plainValueCase, advancedFilterDtoClass, primitiveJavaType);
  }

  private static boolean isEnumSchema(SchemaDef schema) {
    return "string".equals(schema.node().type()) && !schema.node().enumValues().isEmpty();
  }

  private static String strictEnumClassName(String schemaName) {
    return "Generated" + schemaName;
  }

  private static String renderStrictEnum(
      String sourceFile, String schemaName, List<String> enumValues) {
    final var className = strictEnumClassName(schemaName);
    final var constants =
        enumValues.stream()
            .map(v -> "  " + v + "(\"" + v + "\")")
            .collect(Collectors.joining(",\n\n"));
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public enum %s {

%s;

  private final String value;

  %s(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static %s fromValue(String value) {
    for (%s b : %s.values()) {
      if (b.value.equalsIgnoreCase(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
"""
        .formatted(
            sourceFile,
            schemaName,
            TARGET_PACKAGE,
            className,
            constants,
            className,
            className,
            className,
            className);
  }

  /**
   * Renders a nested enum inside a record for fields with inline enum values.
   * Uses @JsonCreator/@JsonValue so Jackson produces the expected error message
   * "Unexpected value 'x' for enum field 'field'" on unknown values.
   */
  private static String renderNestedEnum(String enumName, List<String> values) {
    final var constants = values.stream()
        .map(v -> "    " + toConstantName(v) + "(\"" + v + "\")")
        .collect(Collectors.joining(",\n\n"));
    return """
  public enum %s {
%s;

    private final String value;

    %s(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static %s fromValue(String value) {
      for (%s b : %s.values()) {
        if (b.value.equalsIgnoreCase(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }"""
        .formatted(enumName, constants, enumName, enumName, enumName, enumName);
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
    // Strip "Result" suffix UNLESS it would collide with an existing schema.
    // SearchQuery collisions (e.g. FooSearchQuery/FooSearchQueryResult) are handled
    // separately by SEARCH_REQUEST_DTO_OVERRIDES and always strip "Result".
    if (schemaName.endsWith("Result") && !RETAINED_RESULT_SCHEMAS.contains(schemaName)) {
      return "Generated" + schemaName.substring(0, schemaName.length() - 6) + "StrictContract";
    }
    return "Generated" + schemaName + "StrictContract";
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
      final var isNullable = node.nullable() || !isRequired
          || FORCE_NULLABLE_FIELDS.contains(schema.schemaName() + "." + propertyName);
        final var typeInfo = resolveTypeInfo(node, contextFile, allSchemas, new ArrayDeque<>());
        final var typeOverride = FORCE_TYPE_OVERRIDES.get(schema.schemaName() + "." + propertyName);
        final var javaType = typeOverride != null ? typeOverride : typeInfo.javaType();
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
      final var fieldConstraints = resolveConstraints(node, contextFile, allSchemas);
      final var identifier = uniqueIdentifier(toJavaIdentifier(propertyName), usedIdentifiers);
      final var mapperMethod = toJavaMethodName(propertyName);
      // Generate a nested enum in the DTO for sort request fields with inline enum values.
      // This ensures Jackson rejects unknown sort field names at deserialization time with
      // a helpful message listing valid values, rather than deferring to the mapper.
      final var useNestedEnum = hasInlineEnum && !node.enumValues().isEmpty()
          && schema.schemaName().contains("SortRequest") && "field".equals(propertyName);
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
              typeInfo,
              fieldConstraints,
              useNestedEnum ? node.enumValues() : List.of()));
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
      String sourceFile, String schemaName, String dtoClass, List<ContractField> fields,
      String sealedParent, boolean emitConstraints) {
    final boolean hasNullable = fields.stream().anyMatch(ContractField::nullable);
    final boolean hasRequiredNonNullable = fields.stream().anyMatch(f -> f.required() && !f.nullable());
    final var coercionFields = fields.stream().filter(ContractField::requiresCoercion).toList();
    final boolean hasLongKeyCoercion = !coercionFields.isEmpty();
    final boolean hasListCoercion = fields.stream().anyMatch(ContractField::hasStrictListType);
    final boolean hasJsonProperty = fields.stream().anyMatch(f -> !f.name().equals(f.identifier()));
    final boolean hasInlineEnumValues = fields.stream().anyMatch(f -> !f.inlineEnumValues().isEmpty());
    final String imports =
      "import com.fasterxml.jackson.annotation.JsonInclude;\n"
        + (hasInlineEnumValues
            ? "import com.fasterxml.jackson.annotation.JsonCreator;\n"
                + "import com.fasterxml.jackson.annotation.JsonValue;\n"
            : "")
        + "import com.fasterxml.jackson.annotation.JsonProperty;\n"
        + (sealedParent != null
            ? "import com.fasterxml.jackson.databind.JsonDeserializer;\n"
                + "import com.fasterxml.jackson.databind.annotation.JsonDeserialize;\n"
            : "")
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
                f -> {
                    final var jsonProp = "@JsonProperty(\"" + f.name() + "\") ";
                    final var fieldType = f.effectiveJavaType();
                    return "    "
                        + jsonProp
                        + (f.nullable() ? annotateNullable(fieldType) : fieldType)
                        + " "
                        + f.identifier();
                })
            .collect(Collectors.joining(",\n"));

    final boolean hasConstraints = fields.stream().anyMatch(f -> f.constraints().hasAny());
    final String constructorBody;
    {
      final var checks = new ArrayList<String>();

      // Null checks for required non-nullable fields.
      for (var f : fields) {
        if (f.required() && !f.nullable()) {
          checks.add(
              "    Objects.requireNonNull("
                  + f.identifier()
                  + ", \"No "
                  + f.name()
                  + " provided.\");");
        }
      }

      // Spec-derived constraint checks (only for request schemas, not response DTOs).
      if (emitConstraints) {
        for (var f : fields) {
        final var c = f.constraints();
        if (!c.hasAny()) continue;
        final var id = f.identifier();
        final var name = f.name();
        final var guard = f.nullable() ? "if (" + id + " != null) " : "";

        if (c.minLength() != null && "String".equals(f.javaType())) {
          if (c.minLength() == 1) {
            checks.add(
                "    " + guard + "if (" + id + ".isBlank()) throw new IllegalArgumentException(\""
                    + name + " must not be blank\");");
          } else {
            checks.add(
                "    " + guard + "if (" + id + ".length() < " + c.minLength()
                    + ") throw new IllegalArgumentException(\""
                    + name + " must have at least " + c.minLength() + " characters\");");
          }
        }
        if (c.maxLength() != null && "String".equals(f.javaType())) {
          checks.add(
              "    " + guard + "if (" + id + ".length() > " + c.maxLength()
                  + ") throw new IllegalArgumentException(\""
                  + "The provided " + name + " exceeds the limit of " + c.maxLength() + " characters.\");");
        }
        if (c.pattern() != null && "String".equals(f.javaType())) {
          final var escaped = c.pattern().replace("\\", "\\\\").replace("\"", "\\\"");
          checks.add(
              "    " + guard + "if (!" + id + ".matches(\"" + escaped
                  + "\")) throw new IllegalArgumentException(\""
                  + "The provided " + name + " contains illegal characters."
                  + " It must match the pattern '" + escaped + "'.\");");
        }
        if (c.minimum() != null
            && ("Long".equals(f.javaType()) || "Integer".equals(f.javaType()))) {
          final var suffix = "Long".equals(f.javaType()) || c.minimum() > Integer.MAX_VALUE ? "L" : "";
          final var mustBe = c.minimum() == 0 ? "not negative" : c.minimum() == 1 ? "> 0" : "at least " + c.minimum();
          checks.add(
              "    " + guard + "if (" + id + " < " + c.minimum() + suffix
                  + ") throw new IllegalArgumentException("
                  + "\"The value for " + name + " is '\" + " + id + " + \"' but must be "
                  + mustBe + ".\");");
        }
        if (c.maximum() != null
            && ("Long".equals(f.javaType()) || "Integer".equals(f.javaType()))) {
          final var suffix = "Long".equals(f.javaType()) || c.maximum() > Integer.MAX_VALUE ? "L" : "";
          checks.add(
              "    " + guard + "if (" + id + " > " + c.maximum() + suffix
                  + ") throw new IllegalArgumentException("
                  + "\"The value for " + name + " is '\" + " + id + " + \"' but must be "
                  + "at most " + c.maximum() + ".\");");
        }
        if (c.minItems() != null && f.javaType().startsWith("java.util.List")) {
          checks.add(
              "    " + guard + "if (" + id + ".size() < " + c.minItems()
                  + ") throw new IllegalArgumentException(\""
                  + name + " must have at least " + c.minItems() + " items\");");
        }
        if (c.maxItems() != null
            && (f.javaType().startsWith("java.util.List")
                || f.javaType().startsWith("java.util.Set"))) {
          checks.add(
              "    " + guard + "if (" + id + ".size() > " + c.maxItems()
                  + ") throw new IllegalArgumentException(\""
                  + name + " must have at most " + c.maxItems() + " items\");");
        }
        }
      }

      constructorBody = String.join("\n", checks);
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
        && builderCode.isBlank()
        && !hasInlineEnumValues) {
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
      // Render nested enums for fields with inline enum values
      for (var f : fields) {
        if (!f.inlineEnumValues().isEmpty()) {
          sections.add(renderNestedEnum(f.effectiveJavaType(), f.inlineEnumValues()));
        }
      }
      recordBody = "\n" + String.join("\n\n", sections) + "\n";
    }

    final String implementsClause = sealedParent != null ? " implements " + sealedParent : "";
    // When a record implements a sealed interface with @JsonDeserialize, Jackson inherits
    // the custom deserializer. Adding @JsonDeserialize(using = None.class) on the concrete
    // record prevents this inheritance, which would otherwise cause infinite recursion.
    final String jsonDeserializeAnnotation =
        sealedParent != null ? "\n@JsonDeserialize(using = JsonDeserializer.None.class)" : "";

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
@NullMarked%s
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record %s(
%s
)%s {
%s
}
"""
    .formatted(
      sourceFile,
      schemaName,
      TARGET_PACKAGE,
      imports,
      jsonDeserializeAnnotation,
      dtoClass,
        renderedFields,
      implementsClause,
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
      final var valueType = current.requiresCoercion() ? "Object" : current.effectiveJavaType();
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
      final var valueType = current.requiresCoercion() ? "Object" : current.effectiveJavaType();
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
              + (f.requiresCoercion() ? "Object" : f.effectiveJavaType())
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
                      annotateNullable(field.effectiveJavaType()),
                      field.identifier(),
                      field.effectiveJavaType(),
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
              field.nullable() ? annotateNullable(field.effectiveJavaType()) : field.effectiveJavaType(),
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
              annotateNullable(field.effectiveJavaType()),
              field.identifier(),
              field.effectiveJavaType())
          : "";

      return ("""
    %s %s(final %s %s);
  """
        .formatted(optionalStepName, field.identifier(), field.nullable() ? annotateNullable(field.effectiveJavaType()) : field.effectiveJavaType(), field.identifier()))
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
      if (f.hasStrictObjectType() && !f.typeInfo().selfDeserializing()) {
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
      // Self-deserializing types (filter properties) pass through directly — no mapper available.
      if (f.typeInfo().selfDeserializing()) {
        return "        ." + f.mapperMethod() + "(" + accessor + ")";
      }
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
    final var oneOfRefs = new ArrayList<String>();
    String oneOfInlineType = null;
    String oneOfInlineFormat = null;
    final var properties = new LinkedHashMap<String, Node>();
    Long minimum = null;
    Long maximum = null;
    Integer minLength = null;
    Integer maxLength = null;
    String pattern = null;
    Integer minItems = null;
    Integer maxItems = null;

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
              final int inlineEnd = findNestedBlockEnd(lines, ai + 1, allOfBlockEnd, baseIndent + 4);
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
        default -> {
          // Ignore unsupported YAML keys for this generator.
        }
      }
    }

    return new Node(type, format, nullable, ref, items, required, enumValues, allOfRefs, oneOfRefs, oneOfInlineType, oneOfInlineFormat, properties, uniqueItems, additionalProperties, minimum, maximum, minLength, maxLength, pattern, minItems, maxItems);
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
      // When a string-typed property also carries an allOf $ref (e.g. to a filter property
      // sealed interface), the $ref takes precedence over the primitive string type.
      if (!node.allOfRefs().isEmpty()) {
        return resolveRefTypeInfo(node.allOfRefs().getFirst(), currentFile, allSchemas, resolvingStack);
      }
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
      if (node.properties().isEmpty()) {
        return TypeInfo.scalar("java.util.Map<String, Object>");
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

    // Filter property schemas (oneOf with inline primitive + $ref) are registered in
    // AVAILABLE_STRICT_CONTRACTS but have no properties on the parent schema node.
    // Resolve them to the sealed interface type with self-deserializing flag.
    if (AVAILABLE_STRICT_CONTRACTS.contains(key.schemaName())
        && isPolymorphicSchema(schema)
        && isFilterPropertySchema(schema)) {
      final var strictType = dtoClassName(key.schemaName());
      return TypeInfo.selfDeserializingObject(strictType, protocolType);
    }

    if (!schema.node().enumValues().isEmpty()) {
      final var strictEnumType = TARGET_PACKAGE + "." + strictEnumClassName(key.schemaName());
      return TypeInfo.scalar(strictEnumType);
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

  private static String uniqueIdentifier(String base, Map<String, Integer> used) {
    final var count = used.getOrDefault(base, 0);
    used.put(base, count + 1);
    return count == 0 ? base : base + count;
  }

  private static String toJavaIdentifier(String propertyName) {
    if (propertyName == null || propertyName.isBlank()) {
      return "field";
    }

    final var parts = propertyName.split("[^A-Za-z0-9$]");
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
      List<String> oneOfRefs,
      /** Inline primitive type from a oneOf branch (e.g. "string", "integer"). */
      String oneOfInlineType,
      /** Inline primitive format from a oneOf branch (e.g. "int32", "date-time"). */
      String oneOfInlineFormat,
      Map<String, Node> properties,
      boolean uniqueItems,
      Node additionalProperties,
      Long minimum,
      Long maximum,
      Integer minLength,
      Integer maxLength,
      String pattern,
      Integer minItems,
      Integer maxItems) {}

  private record Constraints(
      Long minimum,
      Long maximum,
      Integer minLength,
      Integer maxLength,
      String pattern,
      Integer minItems,
      Integer maxItems) {
    static final Constraints NONE = new Constraints(null, null, null, null, null, null, null);

    boolean hasAny() {
      return minimum != null
          || maximum != null
          || minLength != null
          || maxLength != null
          || pattern != null
          || minItems != null
          || maxItems != null;
    }
  }

  /**
   * Resolves validation constraints for a property node, following $ref and single-element allOf
   * chains. Constraints on the leaf node (closest to the property declaration) are preferred;
   * constraints from referenced schemas fill in any gaps.
   */
  private static Constraints resolveConstraints(
      Node node, String currentFile, Map<SchemaKey, SchemaDef> allSchemas) {
    // Start with constraints directly on this node.
    var minimum = node.minimum();
    var maximum = node.maximum();
    var minLength = node.minLength();
    var maxLength = node.maxLength();
    var pattern = node.pattern();
    var minItems = node.minItems();
    var maxItems = node.maxItems();

    // Follow $ref chain.
    if (node.ref() != null) {
      final var refConstraints = resolveRefConstraints(node.ref(), currentFile, allSchemas);
      if (minimum == null) minimum = refConstraints.minimum();
      if (maximum == null) maximum = refConstraints.maximum();
      if (minLength == null) minLength = refConstraints.minLength();
      if (maxLength == null) maxLength = refConstraints.maxLength();
      if (pattern == null) pattern = refConstraints.pattern();
      if (minItems == null) minItems = refConstraints.minItems();
      if (maxItems == null) maxItems = refConstraints.maxItems();
    }

    // Follow single-element allOf chain (common pattern: allOf: [$ref: ...]).
    if (!node.allOfRefs().isEmpty() && node.allOfRefs().size() == 1) {
      final var refConstraints =
          resolveRefConstraints(node.allOfRefs().getFirst(), currentFile, allSchemas);
      if (minimum == null) minimum = refConstraints.minimum();
      if (maximum == null) maximum = refConstraints.maximum();
      if (minLength == null) minLength = refConstraints.minLength();
      if (maxLength == null) maxLength = refConstraints.maxLength();
      if (pattern == null) pattern = refConstraints.pattern();
      if (minItems == null) minItems = refConstraints.minItems();
      if (maxItems == null) maxItems = refConstraints.maxItems();
    }

    return new Constraints(minimum, maximum, minLength, maxLength, pattern, minItems, maxItems);
  }

  private static Constraints resolveRefConstraints(
      String ref, String currentFile, Map<SchemaKey, SchemaDef> allSchemas) {
    final var key = toSchemaKey(ref, currentFile);
    final var target = allSchemas.get(key);
    if (target == null) {
      return Constraints.NONE;
    }
    return resolveConstraints(target.node(), target.fileName(), allSchemas);
  }

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
      TypeInfo typeInfo,
      Constraints constraints,
      List<String> inlineEnumValues) {

    /** Returns the effective Java type, using the nested enum name if inline enum values exist. */
    String effectiveJavaType() {
      if (!inlineEnumValues.isEmpty()) {
        return capitalizeIdentifier(identifier) + "Enum";
      }
      return javaType;
    }

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
      if (typeInfo != null && typeInfo.selfDeserializing()) return false;
      return longKeyCoercion || hasStrictObjectType() || hasStrictListType();
    }
  }

  private record TypeInfo(
      String javaType,
      boolean strictObjectType,
      String strictDtoClass,
      String protocolJavaType,
      boolean protocolConvertible,
      /** True for types with their own @JsonDeserialize that don't need coercion helpers. */
      boolean selfDeserializing,
      TypeInfo elementType) {

    static TypeInfo scalar(final String javaType) {
      return new TypeInfo(javaType, false, null, null, false, false, null);
    }

    static TypeInfo strictObject(
        final String strictDtoClass,
        final String protocolJavaType,
        final boolean protocolConvertible) {
      return new TypeInfo(
          strictDtoClass, true, strictDtoClass, protocolJavaType, protocolConvertible, false, null);
    }

    /** A strict object type that handles its own deserialization (e.g., filter properties). */
    static TypeInfo selfDeserializingObject(
        final String strictDtoClass,
        final String protocolJavaType) {
      return new TypeInfo(
          strictDtoClass, true, strictDtoClass, protocolJavaType, false, true, null);
    }

    static TypeInfo listOf(final TypeInfo elementType) {
      return new TypeInfo(
          "java.util.List<" + elementType.javaType() + ">",
          false,
          null,
          null,
          false,
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

  // ---------------------------------------------------------------------------
  // Phase 3: Search query response & request mapper generation
  // ---------------------------------------------------------------------------

  /**
   * Describes a search-query-response wrapper method that converts a
   * {@code SearchQueryResult<Entity>} into a {@code StrictSearchQueryResult<Contract>}.
   *
   * @param methodSuffix  part after {@code to} in the method name, e.g. "IncidentSearchQueryResponse"
   * @param entityClass   simple name of the entity, e.g. "IncidentEntity"
   * @param entityImport  fully-qualified import (null => io.camunda.search.entities.{entityClass})
   * @param contractClass simple name of the strict contract DTO
   * @param adapterClass  simple name of the contract adapter
   * @param adapterImport fully-qualified import (null => io.camunda.gateway.mapping.http.search.contract.{adapterClass})
   * @param adaptMethod   adapter method for list mapping ("adapt" or e.g. "toSearchProjections")
   * @param listAdapter   true = adapter takes List→List; false = use stream().map()
   * @param extraParamDecl extra parameter declaration (null = none), e.g. "final boolean truncateValues"
   * @param extraParamRef  extra parameter reference (null = none), e.g. "truncateValues"
   */
  private record ResponseWrapperEntry(
      String methodSuffix,
      String entityClass,
      String entityImport,
      String contractClass,
      String adapterClass,
      String adapterImport,
      String adaptMethod,
      boolean listAdapter,
      String extraParamDecl,
      String extraParamRef) {

    ResponseWrapperEntry(
        String methodSuffix,
        String entityClass,
        String contractClass,
        String adapterClass,
        String adaptMethod,
        boolean listAdapter) {
      this(methodSuffix, entityClass, null, contractClass, adapterClass, null, adaptMethod, listAdapter, null, null);
    }

    ResponseWrapperEntry(
        String methodSuffix,
        String entityClass,
        String contractClass,
        String adapterClass) {
      this(methodSuffix, entityClass, contractClass, adapterClass, "adapt", false);
    }
  }

  /**
   * Describes a single-entity delegation method: {@code Entity → Adapter.method(entity) → Contract}.
   */
  private record SingleEntityEntry(
      String methodName,
      String entityClass,
      String entityImport,
      String contractClass,
      String adapterClass,
      String adapterImport,
      String adaptMethod) {

    SingleEntityEntry(
        String methodName,
        String entityClass,
        String contractClass,
        String adapterClass) {
      this(methodName, entityClass, null, contractClass, adapterClass, null, "adapt");
    }

    SingleEntityEntry(
        String methodName,
        String entityClass,
        String contractClass,
        String adapterClass,
        String adaptMethod) {
      this(methodName, entityClass, null, contractClass, adapterClass, null, adaptMethod);
    }
  }

  /**
   * Describes a mechanical search-query request method: request → page + sort + filter → query.
   *
   * @param methodName     method name suffix, e.g. "Incident" → "toIncidentQuery"
   * @param requestClass   simple name of request type (protocol model)
   * @param queryClass     simple name of query type (search query)
   * @param queryImport    fully-qualified import for query type (null => io.camunda.search.query.{queryClass})
   * @param builderMethod  SearchQueryBuilders method name, e.g. "incidentSearchQuery"
   * @param sortFromMethod SearchQuerySortRequestMapper::from... method name
   * @param sortOptionRef  SortOptionBuilders method reference name, e.g. "incident"
   * @param sortFieldMethod SearchQuerySortRequestMapper::apply... method name
   * @param filterExpr     filter expression, e.g. "SearchQueryFilterMapper.toIncidentFilter(request.getFilter())"
   */
  private record RequestEntry(
      String methodName,
      String requestClass,
      String queryClass,
      String queryImport,
      String builderMethod,
      String sortFromMethod,
      String sortOptionRef,
      String sortFieldMethod,
      String filterExpr) {

    RequestEntry(
        String methodName,
        String requestClass,
        String queryClass,
        String builderMethod,
        String sortFromMethod,
        String sortOptionRef,
        String sortFieldMethod,
        String filterExpr) {
      this(methodName, requestClass, queryClass, null, builderMethod,
           sortFromMethod, sortOptionRef, sortFieldMethod, filterExpr);
    }
  }

  // -- Response wrapper entries --

  private static final List<ResponseWrapperEntry> RESPONSE_WRAPPER_ENTRIES = List.of(
      new ResponseWrapperEntry("ProcessDefinitionSearchQueryResponse",
          "ProcessDefinitionEntity", "GeneratedProcessDefinitionStrictContract",
          "ProcessDefinitionContractAdapter"),
      new ResponseWrapperEntry("ProcessInstanceSearchQueryResponse",
          "ProcessInstanceEntity", "GeneratedProcessInstanceStrictContract",
          "ProcessInstanceContractAdapter"),
      new ResponseWrapperEntry("JobSearchQueryResponse",
          "JobEntity", "GeneratedJobSearchStrictContract",
          "JobContractAdapter"),
      new ResponseWrapperEntry("RoleSearchQueryResponse",
          "RoleEntity", "GeneratedRoleStrictContract",
          "RoleContractAdapter", "adapt", true),
      new ResponseWrapperEntry("RoleGroupSearchQueryResponse",
          "RoleMemberEntity", "GeneratedRoleGroupStrictContract",
          "MemberContractAdapter", "toRoleGroups", true),
      new ResponseWrapperEntry("RoleUserSearchQueryResponse",
          "RoleMemberEntity", "GeneratedRoleUserStrictContract",
          "MemberContractAdapter", "toRoleUsers", true),
      new ResponseWrapperEntry("RoleClientSearchQueryResponse",
          "RoleMemberEntity", "GeneratedRoleClientStrictContract",
          "MemberContractAdapter", "toRoleClients", true),
      new ResponseWrapperEntry("GroupSearchQueryResponse",
          "GroupEntity", "GeneratedGroupStrictContract",
          "GroupContractAdapter", "adapt", true),
      new ResponseWrapperEntry("GroupUserSearchQueryResponse",
          "GroupMemberEntity", "GeneratedGroupUserStrictContract",
          "MemberContractAdapter", "toGroupUsers", true),
      new ResponseWrapperEntry("GroupClientSearchQueryResponse",
          "GroupMemberEntity", "GeneratedGroupClientStrictContract",
          "MemberContractAdapter", "toGroupClients", true),
      new ResponseWrapperEntry("TenantSearchQueryResponse",
          "TenantEntity", "GeneratedTenantStrictContract",
          "TenantContractAdapter", "adapt", true),
      new ResponseWrapperEntry("TenantGroupSearchQueryResponse",
          "TenantMemberEntity", "GeneratedTenantGroupStrictContract",
          "MemberContractAdapter", "toTenantGroups", true),
      new ResponseWrapperEntry("TenantUserSearchQueryResponse",
          "TenantMemberEntity", "GeneratedTenantUserStrictContract",
          "MemberContractAdapter", "toTenantUsers", true),
      new ResponseWrapperEntry("TenantClientSearchQueryResponse",
          "TenantMemberEntity", "GeneratedTenantClientStrictContract",
          "MemberContractAdapter", "toTenantClients", true),
      new ResponseWrapperEntry("MappingRuleSearchQueryResponse",
          "MappingRuleEntity", "GeneratedMappingRuleStrictContract",
          "MappingRuleContractAdapter", "adapt", true),
      new ResponseWrapperEntry("DecisionDefinitionSearchQueryResponse",
          "DecisionDefinitionEntity", "GeneratedDecisionDefinitionStrictContract",
          "DecisionDefinitionContractAdapter"),
      new ResponseWrapperEntry("DecisionRequirementsSearchQueryResponse",
          "DecisionRequirementsEntity", "GeneratedDecisionRequirementsStrictContract",
          "DecisionRequirementsContractAdapter"),
      new ResponseWrapperEntry("ElementInstanceSearchQueryResponse",
          "FlowNodeInstanceEntity", "GeneratedElementInstanceStrictContract",
          "ElementInstanceContractAdapter"),
      new ResponseWrapperEntry("DecisionInstanceSearchQueryResponse",
          "DecisionInstanceEntity", "GeneratedDecisionInstanceStrictContract",
          "DecisionInstanceContractAdapter", "toSearchProjections", true),
      new ResponseWrapperEntry("UserTaskSearchQueryResponse",
          "UserTaskEntity", "GeneratedUserTaskStrictContract",
          "UserTaskContractAdapter"),
      new ResponseWrapperEntry("UserSearchQueryResponse",
          "UserEntity", "GeneratedUserStrictContract",
          "UserContractAdapter"),
      new ResponseWrapperEntry("BatchOperationSearchQueryResult",
          "BatchOperationEntity", "GeneratedBatchOperationResponseStrictContract",
          "BatchOperationResponseContractAdapter"),
      new ResponseWrapperEntry("BatchOperationItemSearchQueryResult",
          "BatchOperationItemEntity",
          "io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity",
          "GeneratedBatchOperationItemResponseStrictContract",
          "BatchOperationItemResponseContractAdapter", null, "adapt", false, null, null),
      new ResponseWrapperEntry("IncidentSearchQueryResponse",
          "IncidentEntity", "GeneratedIncidentStrictContract",
          "IncidentContractAdapter"),
      new ResponseWrapperEntry("MessageSubscriptionSearchQueryResponse",
          "MessageSubscriptionEntity", "GeneratedMessageSubscriptionStrictContract",
          "MessageSubscriptionContractAdapter"),
      new ResponseWrapperEntry("CorrelatedMessageSubscriptionSearchQueryResponse",
          "CorrelatedMessageSubscriptionEntity", "GeneratedCorrelatedMessageSubscriptionStrictContract",
          "CorrelatedMessageSubscriptionContractAdapter"),
      new ResponseWrapperEntry("AuthorizationSearchQueryResponse",
          "AuthorizationEntity", "GeneratedAuthorizationStrictContract",
          "AuthorizationContractAdapter"),
      new ResponseWrapperEntry("AuditLogSearchQueryResponse",
          "AuditLogEntity", "GeneratedAuditLogStrictContract",
          "AuditLogContractAdapter"),
      new ResponseWrapperEntry("GlobalTaskListenerSearchQueryResponse",
          "GlobalListenerEntity", "GeneratedGlobalTaskListenerStrictContract",
          "GlobalTaskListenerContractAdapter"),
      // Extra-parameter entries (variable truncation)
      new ResponseWrapperEntry("VariableSearchQueryResponse",
          "VariableEntity", null, "GeneratedVariableSearchStrictContract",
          "VariableContractAdapter", null, "toSearchProjections", true,
          "final boolean truncateValues", "truncateValues"),
      new ResponseWrapperEntry("ClusterVariableSearchQueryResponse",
          "ClusterVariableEntity", null, "GeneratedClusterVariableSearchStrictContract",
          "ClusterVariableContractAdapter", null, "toSearchProjections", true,
          "final boolean truncateValues", "truncateValues"));

  // -- Single-entity delegation entries --

  private static final List<SingleEntityEntry> SINGLE_ENTITY_ENTRIES = List.of(
      new SingleEntityEntry("toProcessDefinition",
          "ProcessDefinitionEntity", "GeneratedProcessDefinitionStrictContract",
          "ProcessDefinitionContractAdapter"),
      new SingleEntityEntry("toProcessInstance",
          "ProcessInstanceEntity", "GeneratedProcessInstanceStrictContract",
          "ProcessInstanceContractAdapter"),
      new SingleEntityEntry("toBatchOperation",
          "BatchOperationEntity", "GeneratedBatchOperationResponseStrictContract",
          "BatchOperationResponseContractAdapter"),
      new SingleEntityEntry("toBatchOperationItem",
          "BatchOperationItemEntity",
          "io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity",
          "GeneratedBatchOperationItemResponseStrictContract",
          "BatchOperationItemResponseContractAdapter", null, "adapt"),
      new SingleEntityEntry("toRole",
          "RoleEntity", "GeneratedRoleStrictContract",
          "RoleContractAdapter"),
      new SingleEntityEntry("toGroup",
          "GroupEntity", "GeneratedGroupStrictContract",
          "GroupContractAdapter"),
      new SingleEntityEntry("toTenant",
          "TenantEntity", "GeneratedTenantStrictContract",
          "TenantContractAdapter"),
      new SingleEntityEntry("toMappingRule",
          "MappingRuleEntity", "GeneratedMappingRuleStrictContract",
          "MappingRuleContractAdapter"),
      new SingleEntityEntry("toElementInstance",
          "FlowNodeInstanceEntity", "GeneratedElementInstanceStrictContract",
          "ElementInstanceContractAdapter"),
      new SingleEntityEntry("toDecisionDefinition",
          "DecisionDefinitionEntity", "GeneratedDecisionDefinitionStrictContract",
          "DecisionDefinitionContractAdapter"),
      new SingleEntityEntry("toDecisionRequirements",
          "DecisionRequirementsEntity", "GeneratedDecisionRequirementsStrictContract",
          "DecisionRequirementsContractAdapter"),
      new SingleEntityEntry("toIncident",
          "IncidentEntity", "GeneratedIncidentStrictContract",
          "IncidentContractAdapter"),
      new SingleEntityEntry("toUserTask",
          "UserTaskEntity", "GeneratedUserTaskStrictContract",
          "UserTaskContractAdapter"),
      new SingleEntityEntry("toFormItem",
          "FormEntity", "GeneratedFormStrictContract",
          "FormContractAdapter"),
      new SingleEntityEntry("toUser",
          "UserEntity", "GeneratedUserStrictContract",
          "UserContractAdapter"),
      new SingleEntityEntry("toDecisionInstance",
          "DecisionInstanceEntity", "GeneratedDecisionInstanceStrictContract",
          "DecisionInstanceContractAdapter", "toSearchProjection"),
      new SingleEntityEntry("toVariableItem",
          "VariableEntity", "GeneratedVariableStrictContract",
          "VariableContractAdapter", "toItemProjection"),
      new SingleEntityEntry("toClusterVariableResult",
          "ClusterVariableEntity", "GeneratedClusterVariableStrictContract",
          "ClusterVariableContractAdapter", "toItemProjection"),
      new SingleEntityEntry("toAuthorization",
          "AuthorizationEntity", "GeneratedAuthorizationStrictContract",
          "AuthorizationContractAdapter"),
      new SingleEntityEntry("toAuditLog",
          "AuditLogEntity", "GeneratedAuditLogStrictContract",
          "AuditLogContractAdapter"),
      new SingleEntityEntry("toProcessInstanceCallHierarchyEntry",
          "ProcessInstanceEntity", "GeneratedProcessInstanceCallHierarchyEntryStrictContract",
          "ProcessInstanceCallHierarchyEntryContractAdapter"),
      new SingleEntityEntry("toGlobalTaskListenerResult",
          "GlobalListenerEntity", "GeneratedGlobalTaskListenerStrictContract",
          "GlobalTaskListenerContractAdapter"));

  // -- Request mapper entries --

  private static final List<RequestEntry> REQUEST_ENTRIES = List.of(
      new RequestEntry("ProcessInstance", "ProcessInstanceSearchQuery", "ProcessInstanceQuery",
          "processInstanceSearchQuery", "fromProcessInstanceSearchQuerySortRequest",
          "processInstance", "applyProcessInstanceSortField",
          "SearchQueryFilterMapper.toProcessInstanceFilter(request.getFilter())"),
      new RequestEntry("Job", "JobSearchQuery", "JobQuery",
          "jobSearchQuery", "fromJobSearchQuerySortRequest",
          "job", "applyJobSortField",
          "SearchQueryFilterMapper.toJobFilter(request.getFilter())"),
      new RequestEntry("Role", "RoleSearchQueryRequest", "RoleQuery",
          "roleSearchQuery", "fromRoleSearchQuerySortRequest",
          "role", "applyRoleSortField",
          "SearchQueryFilterMapper.toRoleFilter(request.getFilter())"),
      // Role member overloads
      new RequestEntry("RoleMember", "RoleUserSearchQueryRequest", "RoleMemberQuery",
          "roleMemberSearchQuery", "fromRoleUserSearchQuerySortRequest",
          "roleMember", "applyRoleUserSortField",
          "FilterBuilders.roleMember().build()"),
      new RequestEntry("RoleMember", "RoleGroupSearchQueryRequest", "RoleMemberQuery",
          "roleMemberSearchQuery", "fromRoleGroupSearchQuerySortRequest",
          "roleMember", "applyRoleGroupSortField",
          "FilterBuilders.roleMember().build()"),
      new RequestEntry("RoleMember", "RoleClientSearchQueryRequest", "RoleMemberQuery",
          "roleMemberSearchQuery", "fromRoleClientSearchQuerySortRequest",
          "roleMember", "applyRoleClientSortField",
          "FilterBuilders.roleMember().build()"),
      new RequestEntry("Group", "GroupSearchQueryRequest", "GroupQuery",
          "groupSearchQuery", "fromGroupSearchQuerySortRequest",
          "group", "applyGroupSortField",
          "SearchQueryFilterMapper.toGroupFilter(request.getFilter())"),
      // Group member overloads
      new RequestEntry("GroupMember", "GroupUserSearchQueryRequest", "GroupMemberQuery",
          "groupMemberSearchQuery", "fromGroupUserSearchQuerySortRequest",
          "groupMember", "applyGroupUserSortField",
          "FilterBuilders.groupMember().build()"),
      new RequestEntry("GroupMember", "GroupClientSearchQueryRequest", "GroupMemberQuery",
          "groupMemberSearchQuery", "fromGroupClientSearchQuerySortRequest",
          "groupMember", "applyGroupClientSortField",
          "FilterBuilders.groupMember().build()"),
      new RequestEntry("Tenant", "TenantSearchQueryRequest", "TenantQuery",
          "tenantSearchQuery", "fromTenantSearchQuerySortRequest",
          "tenant", "applyTenantSortField",
          "SearchQueryFilterMapper.toTenantFilter(request.getFilter())"),
      // Tenant member overloads
      new RequestEntry("TenantMember", "TenantGroupSearchQueryRequest", "TenantMemberQuery",
          "tenantMemberSearchQuery", "fromTenantGroupSearchQuerySortRequest",
          "tenantMember", "applyTenantGroupSortField",
          "FilterBuilders.tenantMember().build()"),
      new RequestEntry("TenantMember", "TenantUserSearchQueryRequest", "TenantMemberQuery",
          "tenantMemberSearchQuery", "fromTenantUserSearchQuerySortRequest",
          "tenantMember", "applyTenantUserSortField",
          "FilterBuilders.tenantMember().build()"),
      new RequestEntry("TenantMember", "TenantClientSearchQueryRequest", "TenantMemberQuery",
          "tenantMemberSearchQuery", "fromTenantClientSearchQuerySortRequest",
          "tenantMember", "applyTenantClientSortField",
          "FilterBuilders.tenantMember().build()"),
      new RequestEntry("MappingRule", "MappingRuleSearchQueryRequest", "MappingRuleQuery",
          "mappingRuleSearchQuery", "fromMappingRuleSearchQuerySortRequest",
          "mappingRule", "applyMappingRuleSortField",
          "SearchQueryFilterMapper.toMappingRuleFilter(request.getFilter())"),
      new RequestEntry("ProcessDefinition", "ProcessDefinitionSearchQuery", "ProcessDefinitionQuery",
          "processDefinitionSearchQuery", "fromProcessDefinitionSearchQuerySortRequest",
          "processDefinition", "applyProcessDefinitionSortField",
          "SearchQueryFilterMapper.toProcessDefinitionFilter(request.getFilter())"),
      new RequestEntry("DecisionDefinition", "DecisionDefinitionSearchQuery", "DecisionDefinitionQuery",
          "decisionDefinitionSearchQuery", "fromDecisionDefinitionSearchQuerySortRequest",
          "decisionDefinition", "applyDecisionDefinitionSortField",
          "SearchQueryFilterMapper.toDecisionDefinitionFilter(request.getFilter())"),
      new RequestEntry("DecisionRequirements", "DecisionRequirementsSearchQuery", "DecisionRequirementsQuery",
          "decisionRequirementsSearchQuery", "fromDecisionRequirementsSearchQuerySortRequest",
          "decisionRequirements", "applyDecisionRequirementsSortField",
          "SearchQueryFilterMapper.toDecisionRequirementsFilter(request.getFilter())"),
      new RequestEntry("ElementInstance", "ElementInstanceSearchQuery", "FlowNodeInstanceQuery",
          "flownodeInstanceSearchQuery", "fromElementInstanceSearchQuerySortRequest",
          "flowNodeInstance", "applyElementInstanceSortField",
          "SearchQueryFilterMapper.toElementInstanceFilter(request.getFilter())"),
      new RequestEntry("DecisionInstance", "DecisionInstanceSearchQuery", "DecisionInstanceQuery",
          "decisionInstanceSearchQuery", "fromDecisionInstanceSearchQuerySortRequest",
          "decisionInstance", "applyDecisionInstanceSortField",
          "SearchQueryFilterMapper.toDecisionInstanceFilter(request.getFilter())"),
      new RequestEntry("UserTask", "UserTaskSearchQuery", "UserTaskQuery",
          "userTaskSearchQuery", "fromUserTaskSearchQuerySortRequest",
          "userTask", "applyUserTaskSortField",
          "SearchQueryFilterMapper.toUserTaskFilter(request.getFilter())"),
      new RequestEntry("UserTaskVariable", "UserTaskVariableSearchQueryRequest", "VariableQuery",
          "variableSearchQuery", "fromUserTaskVariableSearchQuerySortRequest",
          "variable", "applyUserTaskVariableSortField",
          "SearchQueryFilterMapper.toUserTaskVariableFilter(request.getFilter())"),
      new RequestEntry("Variable", "VariableSearchQuery", "VariableQuery",
          "variableSearchQuery", "fromVariableSearchQuerySortRequest",
          "variable", "applyVariableSortField",
          "SearchQueryFilterMapper.toVariableFilter(request.getFilter())"),
      new RequestEntry("ClusterVariable", "ClusterVariableSearchQueryRequest", "ClusterVariableQuery",
          "clusterVariableSearchQuery", "fromClusterVariableSearchQuerySortRequest",
          "clusterVariable", "applyClusterVariableSortField",
          "SearchQueryFilterMapper.toClusterVariableFilter(request.getFilter())"),
      new RequestEntry("User", "UserSearchQueryRequest", "UserQuery",
          "userSearchQuery", "fromUserSearchQuerySortRequest",
          "user", "applyUserSortField",
          "SearchQueryFilterMapper.toUserFilter(request.getFilter())"),
      new RequestEntry("Incident", "IncidentSearchQuery", "IncidentQuery",
          "incidentSearchQuery", "fromIncidentSearchQuerySortRequest",
          "incident", "applyIncidentSortField",
          "SearchQueryFilterMapper.toIncidentFilter(request.getFilter())"),
      new RequestEntry("BatchOperation", "BatchOperationSearchQuery", "BatchOperationQuery",
          "batchOperationQuery", "fromBatchOperationSearchQuerySortRequest",
          "batchOperation", "applyBatchOperationSortField",
          "SearchQueryFilterMapper.toBatchOperationFilter(request.getFilter())"),
      new RequestEntry("BatchOperationItem", "BatchOperationItemSearchQuery", "BatchOperationItemQuery",
          "batchOperationItemQuery", "fromBatchOperationItemSearchQuerySortRequest",
          "batchOperationItem", "applyBatchOperationItemSortField",
          "SearchQueryFilterMapper.toBatchOperationItemFilter(request.getFilter())"),
      new RequestEntry("Authorization", "AuthorizationSearchQuery", "AuthorizationQuery",
          "authorizationSearchQuery", "fromAuthorizationSearchQuerySortRequest",
          "authorization", "applyAuthorizationSortField",
          "SearchQueryFilterMapper.toAuthorizationFilter(request.getFilter())"),
      new RequestEntry("AuditLog", "AuditLogSearchQueryRequest", "AuditLogQuery",
          "auditLogSearchQuery", "fromAuditLogSearchQuerySortRequest",
          "auditLog", "applyAuditLogSortField",
          "SearchQueryFilterMapper.toAuditLogFilter(request.getFilter())"),
      new RequestEntry("UserTaskAuditLog", "UserTaskAuditLogSearchQueryRequest", "AuditLogQuery",
          "auditLogSearchQuery", "fromUserTaskAuditLogSearchRequest",
          "auditLog", "applyAuditLogSortField",
          "SearchQueryFilterMapper.toUserTaskAuditLogFilter(request.getFilter())"),
      new RequestEntry("MessageSubscription", "MessageSubscriptionSearchQuery", "MessageSubscriptionQuery",
          "messageSubscriptionSearchQuery", "fromMessageSubscriptionSearchQuerySortRequest",
          "messageSubscription", "applyMessageSubscriptionSortField",
          "SearchQueryFilterMapper.toMessageSubscriptionFilter(request.getFilter())"),
      new RequestEntry("CorrelatedMessageSubscription", "CorrelatedMessageSubscriptionSearchQuery",
          "CorrelatedMessageSubscriptionQuery",
          "correlatedMessageSubscriptionSearchQuery",
          "fromCorrelatedMessageSubscriptionSearchQuerySortRequest",
          "correlatedMessageSubscription", "applyCorrelatedMessageSubscriptionSortField",
          "SearchQueryFilterMapper.toCorrelatedMessageSubscriptionFilter(request.getFilter())"),
      new RequestEntry("GlobalTaskListener", "GlobalTaskListenerSearchQueryRequest", "GlobalListenerQuery",
          "globalListenerSearchQuery", "fromGlobalTaskListenerSearchQuerySortRequest",
          "globalListener", "applyGlobalTaskListenerSortField",
          "SearchQueryFilterMapper.toGlobalTaskListenerFilter(request.getFilter())"));

  /**
   * Force specific schema fields to be nullable even when the spec declares them required.
   * Format: "SchemaName.fieldName".  The CursorForwardPagination 'after' cursor is required
   * by the spec but callers validly omit it on the first page request.
   */
  private static final Set<String> FORCE_NULLABLE_FIELDS = Set.of(
      "CursorForwardPagination.after"
  );

  /**
   * Override the resolved Java type for specific schema fields.
   * Key format: "SchemaName.fieldName" → Java type string.
   * Use when the spec says {@code type: object} but the field should accept any JSON value.
   */
  private static final Map<String, String> FORCE_TYPE_OVERRIDES = Map.of(
      "CreateClusterVariableRequest.value", "Object",
      "UpdateClusterVariableRequest.value", "Object"
  );

  /**
   * Maps protocol model request class names (from REQUEST_ENTRIES) to their Phase 3.5
   * request DTO class names. Used by resolveSchemaType to resolve search query request body
   * schemas to the correct strict contract (the request DTO, not the result DTO).
   */
  private static final Map<String, String> SEARCH_REQUEST_DTO_OVERRIDES =
      REQUEST_ENTRIES.stream()
          .collect(Collectors.toMap(
              RequestEntry::requestClass,
              e -> "Generated" + requestEntityName(e) + "SearchQueryRequestStrictContract",
              (a, b) -> a));

  // -- Render method: search query response mapper --

  private static String renderSearchQueryResponseMapper() {
    final var sb = new StringBuilder();
    sb.append("""
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

import static java.util.Optional.ofNullable;

""".formatted(TARGET_PACKAGE));

    // Collect imports
    final var imports = new LinkedHashSet<String>();
    imports.add("jakarta.annotation.Generated");
    imports.add("io.camunda.gateway.mapping.http.search.contract.StrictSearchQueryPage");
    imports.add("io.camunda.gateway.mapping.http.search.contract.StrictSearchQueryResult");
    imports.add("io.camunda.search.query.SearchQueryResult");
    imports.add("java.util.Collections");
    imports.add("java.util.List");

    for (var e : RESPONSE_WRAPPER_ENTRIES) {
      imports.add(resolveEntityImport(e.entityClass(), e.entityImport()));
      imports.add(resolveAdapterImport(e.adapterClass(), e.adapterImport()));
    }
    for (var e : SINGLE_ENTITY_ENTRIES) {
      imports.add(resolveEntityImport(e.entityClass(), e.entityImport()));
      imports.add(resolveAdapterImport(e.adapterClass(), e.adapterImport()));
    }

    imports.stream().sorted().forEach(imp -> sb.append("import ").append(imp).append(";\n"));

    sb.append("""

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedSearchQueryResponseMapper {

  private GeneratedSearchQueryResponseMapper() {}

  public static StrictSearchQueryPage toStrictSearchQueryPage(final SearchQueryResult<?> result) {
    return new StrictSearchQueryPage(
        result.total(), result.hasMoreTotalItems(), result.startCursor(), result.endCursor());
  }

""");

    // Generate wrapper methods
    for (var e : RESPONSE_WRAPPER_ENTRIES) {
      final var simpleEntity = simpleClassName(e.entityClass());
      final var extraDecl = e.extraParamDecl() != null ? ", " + e.extraParamDecl() : "";

      sb.append("  public static StrictSearchQueryResult<").append(e.contractClass()).append(">\n");
      sb.append("      to").append(e.methodSuffix()).append("(\n");
      sb.append("          final SearchQueryResult<").append(simpleEntity).append("> result");
      sb.append(extraDecl).append(") {\n");

      if (e.listAdapter()) {
        // List-level adapter: adapter takes entire list and returns list
        if (e.extraParamRef() != null) {
          sb.append("    return new StrictSearchQueryResult<>(\n");
          sb.append("        ofNullable(result.items())\n");
          sb.append("            .map(items -> ").append(e.adapterClass()).append(".").append(e.adaptMethod());
          sb.append("(items, ").append(e.extraParamRef()).append("))\n");
          sb.append("            .orElseGet(Collections::emptyList),\n");
        } else {
          sb.append("    return new StrictSearchQueryResult<>(\n");
          sb.append("        ofNullable(result.items())\n");
          sb.append("            .map(").append(e.adapterClass()).append("::").append(e.adaptMethod()).append(")\n");
          sb.append("            .orElseGet(Collections::emptyList),\n");
        }
      } else {
        // Individual-item adapter: stream().map()
        sb.append("    return new StrictSearchQueryResult<>(\n");
        sb.append("        ofNullable(result.items())\n");
        sb.append("            .map(items -> items.stream().map(");
        sb.append(e.adapterClass()).append("::").append(e.adaptMethod());
        sb.append(").toList())\n");
        sb.append("            .orElseGet(Collections::emptyList),\n");
      }

      sb.append("        toStrictSearchQueryPage(result));\n");
      sb.append("  }\n\n");
    }

    // Generate single-entity delegation methods
    for (var e : SINGLE_ENTITY_ENTRIES) {
      final var simpleEntity = simpleClassName(e.entityClass());
      sb.append("  public static ").append(e.contractClass()).append(" ").append(e.methodName());
      sb.append("(final ").append(simpleEntity).append(" entity) {\n");
      sb.append("    return ").append(e.adapterClass()).append(".").append(e.adaptMethod());
      sb.append("(entity);\n");
      sb.append("  }\n\n");
    }

    sb.append("}\n");
    return sb.toString();
  }

  // -- Render method: search query request mapper --

  private static String renderSearchQueryRequestMapper() {
    final var sb = new StringBuilder();
    sb.append("""
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

""".formatted(SEARCH_PACKAGE));

    // Collect imports
    final var imports = new LinkedHashSet<String>();
    imports.add("jakarta.annotation.Generated");
    imports.add("io.camunda.gateway.mapping.http.search.SearchQueryFilterMapper");
    imports.add("io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper");
    imports.add("io.camunda.gateway.mapping.http.search.SearchQuerySortRequest");
    imports.add("io.camunda.gateway.mapping.http.search.SearchQuerySortRequestMapper");
    imports.add("io.camunda.search.filter.FilterBuilders");
    imports.add("io.camunda.search.query.SearchQueryBuilders");
    imports.add("io.camunda.search.sort.SortOptionBuilders");
    imports.add("io.camunda.zeebe.util.Either");
    imports.add("org.springframework.http.ProblemDetail");

    for (var e : REQUEST_ENTRIES) {
      imports.add(PROTOCOL_PACKAGE + "." + e.requestClass());
      imports.add(resolveQueryImport(e.queryClass(), e.queryImport()));
      // Also import strict request DTO
      var strictRequestDto = "Generated" + requestEntityName(e) + "SearchQueryRequestStrictContract";
      imports.add(TARGET_PACKAGE + "." + strictRequestDto);
      imports.add(TARGET_PACKAGE + ".GeneratedSearchQueryPageRequestStrictContract");
      // Import filter param type for strict overload
      var filterFqn = extractFilterParamFqn(e);
      if (filterFqn != null) {
        imports.add(filterFqn);
      }
    }

    imports.stream().sorted().forEach(imp -> sb.append("import ").append(imp).append(";\n"));

    sb.append("""

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedSearchQueryRequestMapper {

  private GeneratedSearchQueryRequestMapper() {}

""");

    for (var e : REQUEST_ENTRIES) {
      sb.append("  public static Either<ProblemDetail, ").append(e.queryClass());
      sb.append("> to").append(e.methodName()).append("Query(\n");
      sb.append("      final ").append(e.requestClass()).append(" request) {\n");
      sb.append("    if (request == null) {\n");
      sb.append("      return Either.right(SearchQueryBuilders.").append(e.builderMethod());
      sb.append("().build());\n");
      sb.append("    }\n");
      sb.append("    final var page = SearchQueryRequestMapper.toSearchQueryPage(request.getPage());\n");
      sb.append("    final var sort =\n");
      sb.append("        SearchQuerySortRequestMapper.toSearchQuerySort(\n");
      sb.append("            SearchQuerySortRequestMapper.").append(e.sortFromMethod());
      sb.append("(request.getSort()),\n");
      sb.append("            SortOptionBuilders::").append(e.sortOptionRef()).append(",\n");
      sb.append("            SearchQuerySortRequestMapper::").append(e.sortFieldMethod());
      sb.append(");\n");
      sb.append("    final var filter = ").append(e.filterExpr()).append(";\n");
      sb.append("    return SearchQueryRequestMapper.buildSearchQuery(\n");
      sb.append("        filter, sort, page, SearchQueryBuilders::").append(e.builderMethod());
      sb.append(");\n");
      sb.append("  }\n\n");
    }

    // Generate strict-typed overloads that accept the new request DTOs
    sb.append("  // -- Strict contract overloads (no protocol model dependency) --\n\n");

    for (var e : REQUEST_ENTRIES) {
      var entityName = requestEntityName(e);
      var strictRequestDto = "Generated" + entityName + "SearchQueryRequestStrictContract";
      var sortContractClass = findSortContractClass(e,
          OUT_BASE.resolve(TARGET_PACKAGE.replace('.', '/')));
      sb.append("  public static Either<ProblemDetail, ").append(e.queryClass());
      sb.append("> to").append(entityName).append("QueryStrict(\n");
      sb.append("      final ").append(strictRequestDto).append(" request) {\n");
      sb.append("    if (request == null) {\n");
      sb.append("      return Either.right(SearchQueryBuilders.").append(e.builderMethod());
      sb.append("().build());\n");
      sb.append("    }\n");

      // ProcessDefinition-specific: isLatestVersion constrains pagination and sort
      if ("ProcessDefinition".equals(entityName)) {
        sb.append("\n");
        sb.append("    // Validate isLatestVersion constraints before processing\n");
        sb.append("    final var isLatestVersionValidation =\n");
        sb.append("        validateIsLatestVersionConstraintsStrict(request);\n");
        sb.append("    if (isLatestVersionValidation.isLeft()) {\n");
        sb.append("      return Either.left(isLatestVersionValidation.getLeft());\n");
        sb.append("    }\n");
        sb.append("\n");
      }

      // Page: use flat page overload
      sb.append("    final var p = request.page();\n");
      sb.append("    final var page = p != null\n");
      sb.append("        ? SearchQueryRequestMapper.toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())\n");
      sb.append("        : SearchQueryRequestMapper.toSearchQueryPage(null, null, null, null);\n");
      // Sort: convert strict sort records to SearchQuerySortRequest(String, String) directly
      if (sortContractClass != null) {
        // Sort DTO has typed sort records with field()/order()
        sb.append("    final var sortRequests = request.sort() != null\n");
        sb.append("        ? request.sort().stream()\n");
        sb.append("            .map(s -> new SearchQuerySortRequest(s.field().getValue(),\n");
        sb.append("                s.order() != null ? s.order().getValue() : null))\n");
        sb.append("            .toList()\n");
        sb.append("        : java.util.List.<SearchQuerySortRequest>of();\n");
      } else {
        // Sort DTO has List<Object> — skip sort conversion (to be fixed when sort contract is available)
        sb.append("    final var sortRequests = java.util.List.<SearchQuerySortRequest>of();\n");
      }
      sb.append("    final var sort =\n");
      sb.append("        SearchQuerySortRequestMapper.toSearchQuerySort(\n");
      sb.append("            sortRequests,\n");
      sb.append("            SortOptionBuilders::").append(e.sortOptionRef()).append(",\n");
      sb.append("            SearchQuerySortRequestMapper::").append(e.sortFieldMethod());
      sb.append(");\n");
      // Filter: bridge to protocol model filter mapper via typed filter field
      final var strictFilterExpr = e.filterExpr().replace("request.getFilter()", "request.filter()");
      sb.append("    final var filter = ").append(strictFilterExpr).append(";\n");
      sb.append("    return SearchQueryRequestMapper.buildSearchQuery(\n");
      sb.append("        filter, sort, page, SearchQueryBuilders::").append(e.builderMethod());
      sb.append(");\n");
      sb.append("  }\n\n");
    }

    // Add ProcessDefinition isLatestVersion validation method
    sb.append("""
  private static Either<ProblemDetail, Void> validateIsLatestVersionConstraintsStrict(
      final GeneratedProcessDefinitionSearchQueryRequestStrictContract request) {
    final var filter = request.filter();
    if (filter == null || filter.isLatestVersion() == null || !filter.isLatestVersion()) {
      return Either.right(null);
    }

    final java.util.List<String> violations = new java.util.ArrayList<>();

    final var page = request.page();
    if (page != null) {
      if (page.before() != null) {
        violations.add(
            io.camunda.gateway.mapping.http.validator.ErrorMessages
                .ERROR_MESSAGE_UNSUPPORTED_PAGINATION_WITH_IS_LATEST_VERSION.formatted("before"));
      }
      if (page.from() != null) {
        violations.add(
            io.camunda.gateway.mapping.http.validator.ErrorMessages
                .ERROR_MESSAGE_UNSUPPORTED_PAGINATION_WITH_IS_LATEST_VERSION.formatted("from"));
      }
    }

    final var sort = request.sort();
    if (sort != null && !sort.isEmpty()) {
      final var allowedFields = java.util.Set.of("processDefinitionId", "tenantId");
      for (final var sortRequest : sort) {
        final var field = sortRequest.field();
        if (field != null && !allowedFields.contains(field.getValue())) {
          violations.add(
              io.camunda.gateway.mapping.http.validator.ErrorMessages
                  .ERROR_MESSAGE_UNSUPPORTED_SORT_FIELD_WITH_IS_LATEST_VERSION.formatted(field.getValue()));
        }
      }
    }

    if (!violations.isEmpty()) {
      final var problem =
          io.camunda.gateway.mapping.http.validator.RequestValidator.createProblemDetail(
              violations);
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }

    return Either.right(null);
  }

""");

    sb.append("}\n");
    return sb.toString();
  }

  // -- Phase 3.5: Search query request DTO generation --

  private static String renderFlatPageRequestDto() {
    return """
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

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedSearchQueryPageRequestStrictContract(
    @Nullable Integer limit,
    @Nullable Integer from,
    @Nullable String after,
    @Nullable String before
) {}
""".formatted(TARGET_PACKAGE);
  }

  /**
   * Derives a unique entity name from the requestClass, e.g.:
   * "ProcessInstanceSearchQuery" → "ProcessInstance"
   * "RoleUserSearchQueryRequest" → "RoleUser"
   */
  private static String requestEntityName(RequestEntry e) {
    return e.requestClass()
        .replace("SearchQueryRequest", "")
        .replace("SearchQuery", "");
  }

  /** Known filter parameter types that use non-standard names in the protocol model. */
  private static final Map<String, String> FILTER_PARAM_OVERRIDES = Map.of(
      "toClusterVariableFilter", "ClusterVariableSearchQueryFilterRequest",
      "toUserTaskAuditLogFilter", "UserTaskAuditLogFilter",
      "toUserTaskVariableFilter", "UserTaskVariableFilter",
      "toGlobalTaskListenerFilter", "GlobalTaskListenerSearchQueryFilterRequest");

  // Map: filter method name part → strict contract class name (overrides for non-standard naming)
  private static final Map<String, String> FILTER_CONTRACT_OVERRIDES = Map.of(
      "ClusterVariableFilter", "GeneratedClusterVariableSearchQueryFilterRequestStrictContract",
      "GlobalTaskListenerFilter", "GeneratedGlobalTaskListenerSearchQueryFilterRequestStrictContract");

  /**
   * Extracts the strict contract filter class name for a REQUEST_ENTRY's filterExpr.
   * Returns null for entries that use FilterBuilders (no filter parameter).
   */
  private static String extractFilterParamFqn(RequestEntry e) {
    var expr = e.filterExpr();
    if (!expr.contains("request.getFilter()")) {
      return null; // FilterBuilders.xxx().build() — no filter parameter
    }
    var m = java.util.regex.Pattern.compile("(to\\w+Filter)\\(").matcher(expr);
    if (!m.find()) return null;
    var methodName = m.group(1);
    // Extract the filter schema name: toXxxFilter → XxxFilter
    var filterSimple = methodName.substring(2); // strip "to"
    // Check for override
    var overrideClass = FILTER_CONTRACT_OVERRIDES.get(filterSimple);
    if (overrideClass != null) {
      return TARGET_PACKAGE + "." + overrideClass;
    }
    // Standard pattern: XxxFilter → GeneratedXxxFilterStrictContract
    return TARGET_PACKAGE + "." + dtoClassName(filterSimple);
  }

  /** Finds the strict contract sort request class for a REQUEST_ENTRY. */
  private static String findSortContractClass(RequestEntry e, Path packagePath) {
    // Try common naming patterns
    String[] candidates = {
        dtoClassName(e.requestClass().replace("SearchQuery", "SearchQuerySortRequest")),
        dtoClassName(e.requestClass().replace("SearchQuery", "SearchQuerySortRequest")
            .replace("Request", "")),
        dtoClassName(e.requestClass() + "SortRequest"),
    };
    for (var c : candidates) {
      if (Files.exists(packagePath.resolve(c + ".java"))) {
        return c;
      }
    }
    // Fallback: use the sortFromMethod name to derive it
    // e.g., sortFromMethod = "fromProcessInstanceSearchQuerySortRequest"
    String methodName = e.sortFromMethod();
    if (methodName.startsWith("from")) {
      String schemaName = methodName.substring(4); // strip "from"
      String candidate = dtoClassName(schemaName);
      if (Files.exists(packagePath.resolve(candidate + ".java"))) {
        return candidate;
      }
    }
    return null; // no sort contract found
  }

  /** Finds the strict contract filter class for a REQUEST_ENTRY. */
  private static String findFilterContractClass(RequestEntry e, Path packagePath) {
    // Extract filter class from filterExpr
    // e.g., "SearchQueryFilterMapper.toProcessInstanceFilter(request.getFilter())"
    // → filter schema name is "ProcessInstanceFilter"
    String filterExpr = e.filterExpr();
    if (filterExpr.contains("FilterBuilders.")) {
      return null; // no filter type, uses FilterBuilders directly
    }
    var matcher = java.util.regex.Pattern.compile(
        "SearchQueryFilterMapper\\.to(\\w+)\\(").matcher(filterExpr);
    if (matcher.find()) {
      String filterMethodName = matcher.group(1);
      // filterMethodName = "ProcessInstanceFilter" → schema name
      String candidate = dtoClassName(filterMethodName);
      if (Files.exists(packagePath.resolve(candidate + ".java"))) {
        return candidate;
      }
    }
    return null;
  }

  private static String renderSearchQueryRequestDto(
      String className, String sortContractClass, String filterContractClass) {
    var sb = new StringBuilder();

    // Build import block
    var filterImport = "";
    if (filterContractClass != null && filterContractClass.contains(".")) {
      filterImport = "\nimport " + filterContractClass + ";";
    }

    sb.append("""
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

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Generated;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;%s

@JsonInclude(JsonInclude.Include.NON_NULL)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record %s(
    @Nullable GeneratedSearchQueryPageRequestStrictContract page,
""".formatted(TARGET_PACKAGE, filterImport, className));

    if (sortContractClass != null) {
      sb.append("    @Nullable List<").append(sortContractClass).append("> sort,\n");
    } else {
      sb.append("    @Nullable List<Object> sort,\n");
    }

    if (filterContractClass != null) {
      // filterContractClass is an FQN — extract simple name for the field type
      var filterSimple = filterContractClass.contains(".")
          ? filterContractClass.substring(filterContractClass.lastIndexOf('.') + 1)
          : filterContractClass;
      sb.append("    @Nullable ").append(filterSimple).append(" filter\n");
    } else {
      sb.append("    @Nullable Object filter\n");
    }

    sb.append(") {}\n");
    return sb.toString();
  }

  // -- Helpers for resolving imports --

  private static String resolveEntityImport(String entityClass, String explicitImport) {
    if (explicitImport != null) return explicitImport;
    return "io.camunda.search.entities." + entityClass;
  }

  private static String resolveAdapterImport(String adapterClass, String explicitImport) {
    if (explicitImport != null) return explicitImport;
    return "io.camunda.gateway.mapping.http.search.contract." + adapterClass;
  }

  private static String resolveQueryImport(String queryClass, String explicitImport) {
    if (explicitImport != null) return explicitImport;
    return "io.camunda.search.query." + queryClass;
  }

  private static String simpleClassName(String className) {
    final int dotIdx = className.lastIndexOf('.');
    return dotIdx >= 0 ? className.substring(dotIdx + 1) : className;
  }

  // ---------------------------------------------------------------------------
  // Phase 4: Universal controller generation with delegate pattern
  // ---------------------------------------------------------------------------

  // -- API interface parsing --

  /** Path to the generated API interface source files. */
  private static final Path API_INTERFACE_SOURCE_DIR =
      ROOT.resolve("../gateway-model/target/generated-sources/openapi/src/main/io/camunda/gateway/protocol/api")
          .normalize();

  /** Represents a method parameter parsed from a generated API interface. */
  private record ApiMethodParam(String javaType, String paramName) {}

  /** Represents a full method signature parsed from a generated API interface. */
  private record ApiMethodSignature(
      String methodName,
      String returnTypeParam,
      List<ApiMethodParam> params) {}

  /**
   * Parses the generated API interface Java file for a tag and extracts all method signatures.
   * Returns empty list if the file does not exist.
   */
  private static List<ApiMethodSignature> parseApiInterface(String tagPascal) throws IOException {
    var apiFile = API_INTERFACE_SOURCE_DIR.resolve(tagPascal + "Api.java");
    if (!Files.exists(apiFile)) return List.of();

    var content = Files.readString(apiFile, StandardCharsets.UTF_8);
    var signatures = new ArrayList<ApiMethodSignature>();

    // Match method signatures: ResponseEntity<T> methodName(...)
    // The interface has no body — methods end with ";"
    // Pattern: captures return type param (supporting nested generics via one level of nesting),
    // method name, and full param text
    var methodPattern = java.util.regex.Pattern.compile(
        "ResponseEntity<((?:[^<>]|<[^>]*>)*)>\\s+(\\w+)\\s*\\(([^;]*?)\\)\\s*;",
        java.util.regex.Pattern.DOTALL);
    var matcher = methodPattern.matcher(content);

    while (matcher.find()) {
      String returnTypeParam = matcher.group(1).trim();
      String methodName = matcher.group(2).trim();
      String paramsText = matcher.group(3).trim();
      var params = parseMethodParams(paramsText);
      signatures.add(new ApiMethodSignature(methodName, returnTypeParam, params));
    }
    return signatures;
  }

  /** Parses a comma-separated parameter list from a method signature, stripping annotations. */
  private static List<ApiMethodParam> parseMethodParams(String paramsText) {
    if (paramsText.isBlank()) return List.of();
    var params = new ArrayList<ApiMethodParam>();

    // Split on commas that are not inside angle brackets (generics)
    var paramFragments = splitParams(paramsText);
    for (var fragment : paramFragments) {
      var param = parseOneParam(fragment.trim());
      if (param != null) params.add(param);
    }
    return params;
  }

  /** Splits parameter text on commas, respecting generics, parentheses, and string literals. */
  private static List<String> splitParams(String text) {
    var result = new ArrayList<String>();
    int angleDepth = 0;
    int parenDepth = 0;
    boolean inString = false;
    int start = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
        inString = !inString;
        continue;
      }
      if (inString) continue;
      if (c == '<') angleDepth++;
      else if (c == '>') angleDepth--;
      else if (c == '(') parenDepth++;
      else if (c == ')') parenDepth--;
      else if (c == ',' && angleDepth == 0 && parenDepth == 0) {
        result.add(text.substring(start, i));
        start = i + 1;
      }
    }
    result.add(text.substring(start));
    return result;
  }

  /** Parses a single parameter fragment, stripping all annotations to extract type + name. */
  private static ApiMethodParam parseOneParam(String fragment) {
    // Strip annotations character by character, handling balanced parens and string literals
    var sb = new StringBuilder();
    int i = 0;
    while (i < fragment.length()) {
      if (fragment.charAt(i) == '@') {
        // Skip annotation name
        i++;
        while (i < fragment.length() && Character.isJavaIdentifierPart(fragment.charAt(i))) i++;
        // If followed by (, skip the balanced parenthesized content
        if (i < fragment.length() && fragment.charAt(i) == '(') {
          int depth = 1;
          i++; // skip opening (
          boolean inStr = false;
          while (i < fragment.length() && depth > 0) {
            char c = fragment.charAt(i);
            if (c == '"' && (i == 0 || fragment.charAt(i - 1) != '\\')) inStr = !inStr;
            if (!inStr) {
              if (c == '(') depth++;
              else if (c == ')') depth--;
            }
            i++;
          }
        }
      } else {
        sb.append(fragment.charAt(i));
        i++;
      }
    }
    String stripped = sb.toString().replaceAll("\\s+", " ").trim();
    int lastSpace = stripped.lastIndexOf(' ');
    if (lastSpace < 0) return null;
    String javaType = stripped.substring(0, lastSpace).trim();
    String paramName = stripped.substring(lastSpace + 1).trim();
    return new ApiMethodParam(javaType, paramName);
  }

  // -- Universal endpoint model --

  /** A single parameter in a spec-driven controller method. */
  private record SpecParam(String name, String javaType, ParamKind kind, boolean required) {}

  /** A response with {@code format: binary} (e.g. file download). */
  private static final String BINARY_RESPONSE_TYPE = "StreamingResponseBody";

  /** Where a parameter is bound from. */
  private enum ParamKind { PATH, QUERY, BODY, PART }

  /** Represents a controller method derived entirely from the spec. */
  private record UniversalEndpoint(
      String methodName,
      String returnTypeParam,
      String httpMethod,
      String path,
      int statusCode,
      List<SpecParam> params,
      EndpointKind classifiedKind,
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

  // -- Existing records (retained for adapter generation) --

  private record ControllerEndpoint(
      EndpointKind kind,
      String methodName,
      String requestType,
      String responseType,
      String pathParam,
      String requestMapperExpr,
      String serviceCallExpr,
      String responseMapperExpr,
      String internalQueryType,
      String validationLabel) {}

  private enum EndpointKind { SEARCH, GET_BY_KEY, MUTATION_VOID, STATISTICS, MUTATION_RESPONSE }

  private record ControllerEntry(
      String className,
      String apiInterface,
      String apiImport,
      String serviceClass,
      String serviceImport,
      String serviceField,
      List<ControllerEndpoint> endpoints) {}

  // -- Spec operation record --

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
      List<String> pathParams,
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

  // -- MUTATION_RESPONSE wiring hints --

  /** Hint for MUTATION_RESPONSE endpoints that use a domain mapper (fold + executeSync).
   *  mapperMethodExpr: "userMapper.toUserRequest(request)" — {p} replaced with pathParam name.
   *  serviceCallExpr:  "{sf}.createUser(%s, {a})" — %s = converted DTO, {sf}/{a} expanded.
   *  responseMapperRef: "ResponseMapper::toUserCreateResponse" or null for Void responses.
   *  httpStatus:       "CREATED", "OK", or "NO_CONTENT". */
  private record MutationResponseHint(
      String mapperMethodExpr, String serviceCallExpr,
      String responseMapperRef, String httpStatus) {}

  /** Declares a domain mapper dependency for a controller that has MUTATION_RESPONSE endpoints. */
  private record MapperDependency(
      String fieldType, String fieldName, String constructExpr, List<String> imports) {}

  /** Hint for sub-resource SEARCH endpoints (search under a parent key path param). */
  private record SubResourceSearchHint(String serviceMethod, boolean parseLong) {}

  // -- GET_BY_KEY service wiring hints --

  private record GetByKeyHint(String serviceMethod, boolean parseLong) {}

  /**
   * Maps operationId → service method name + whether to Long.parseLong the path param.
   * Default (not in map): getByKey with Long.parseLong.
   */
  private static final Map<String, GetByKeyHint> GET_BY_KEY_HINTS = Map.ofEntries(
      Map.entry("getAuditLog", new GetByKeyHint("getAuditLog", false)),
      Map.entry("getBatchOperation", new GetByKeyHint("getById", false)),
      Map.entry("getDecisionInstance", new GetByKeyHint("getById", false)),
      Map.entry("getTenant", new GetByKeyHint("getById", false)),
      Map.entry("getGroup", new GetByKeyHint("getGroup", false)),
      Map.entry("getRole", new GetByKeyHint("getRole", false)),
      Map.entry("getUser", new GetByKeyHint("getUser", false)),
      Map.entry("getMappingRule", new GetByKeyHint("getMappingRule", false)),
      Map.entry("getAuthorization", new GetByKeyHint("getAuthorization", true)),
      Map.entry("getGlobalTaskListener", new GetByKeyHint("getGlobalTaskListener", false)));

  /** Overrides for SEARCH endpoints where the service method is not just "search". */
  private static final Map<String, String> SEARCH_SERVICE_OVERRIDES = Map.of(
      "searchBatchOperationItems", "searchItems",
      "searchCorrelatedMessageSubscriptions", "searchCorrelated");

  /** STATISTICS endpoint wiring. Each entry provides the service method, response mapper suffix,
   *  and internal query type — these cannot be derived from naming conventions. */
  private record StatisticsHint(
      String serviceMethod, String responseMapperMethod, String queryTypeFqn) {}

  private static final Map<String, StatisticsHint> STATISTICS_HINTS = Map.ofEntries(
      Map.entry("getProcessInstanceStatisticsByError",
          new StatisticsHint("incidentProcessInstanceStatisticsByError",
              "SearchQueryResponseMapper.toIncidentProcessInstanceStatisticsByErrorResult(result)",
              "io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery")),
      Map.entry("getProcessInstanceStatisticsByDefinition",
          new StatisticsHint("searchIncidentProcessInstanceStatisticsByDefinition",
              "SearchQueryResponseMapper.toIncidentProcessInstanceStatisticsByDefinitionQueryResult(result)",
              "io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery")),
      Map.entry("getProcessDefinitionStatistics",
          new StatisticsHint("elementStatistics",
              "SearchQueryResponseMapper.toProcessDefinitionElementStatisticsQueryResult(result)",
              "io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery")),
      Map.entry("getProcessDefinitionInstanceStatistics",
          new StatisticsHint("instanceStatistics",
              "SearchQueryResponseMapper.toProcessDefinitionInstanceStatisticsQueryResult(result)",
              "io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery")),
      Map.entry("getProcessDefinitionInstanceVersionStatistics",
          new StatisticsHint("instanceVersionStatistics",
              "SearchQueryResponseMapper.toProcessDefinitionInstanceVersionStatisticsQueryResult(result)",
              "io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery")),
      Map.entry("getProcessDefinitionMessageSubscriptionStatistics",
          new StatisticsHint("messageSubscriptionStatistics",
              "SearchQueryResponseMapper.toProcessDefinitionMessageSubscriptionStatisticsQueryResult(result)",
              "io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery")),
      Map.entry("getProcessInstanceStatistics",
          new StatisticsHint("elementStatistics",
              "SearchQueryResponseMapper.toProcessInstanceElementStatisticsQueryResult(result)",
              "io.camunda.search.query.ProcessInstanceFlowNodeStatisticsQuery")),
      Map.entry("getGlobalJobStatistics",
          new StatisticsHint("getGlobalStatistics",
              "SearchQueryResponseMapper.toGlobalJobStatisticsQueryResult(result)",
              "io.camunda.search.query.GlobalJobStatisticsQuery")),
      Map.entry("getJobTypeStatistics",
          new StatisticsHint("getJobTypeStatistics",
              "SearchQueryResponseMapper.toJobTypeStatisticsQueryResult(result)",
              "io.camunda.search.query.JobTypeStatisticsQuery")),
      Map.entry("getJobWorkerStatistics",
          new StatisticsHint("getJobWorkerStatistics",
              "SearchQueryResponseMapper.toJobWorkerStatisticsQueryResult(result)",
              "io.camunda.search.query.JobWorkerStatisticsQuery")),
      Map.entry("getJobTimeSeriesStatistics",
          new StatisticsHint("getJobTimeSeriesStatistics",
              "SearchQueryResponseMapper.toJobTimeSeriesStatisticsQueryResult(result)",
              "io.camunda.search.query.JobTimeSeriesStatisticsQuery")),
      Map.entry("getJobErrorStatistics",
          new StatisticsHint("getJobErrorStatistics",
              "SearchQueryResponseMapper.toJobErrorStatisticsQueryResult(result)",
              "io.camunda.search.query.JobErrorStatisticsQuery")));

  /** MUTATION_VOID endpoint wiring. Maps operationId → service call expression template.
   *  Templates use {sf} for service field, {p} for path param, {a} for auth. */
  private static final Map<String, String> MUTATION_VOID_HINTS = Map.ofEntries(
      Map.entry("resolveIncident",
          "{sf}.resolveIncident(Long.parseLong({p}), request == null ? null : request.getOperationReference(), {a})"),
      Map.entry("cancelBatchOperation", "{sf}.cancel({p}, {a})"),
      Map.entry("suspendBatchOperation", "{sf}.suspend({p}, {a})"),
      Map.entry("resumeBatchOperation", "{sf}.resume({p}, {a})"),
      Map.entry("deleteDecisionInstance", "{sf}.deleteDecisionInstance(Long.parseLong({p}), {a})"),
      Map.entry("deleteAuthorization", "{sf}.deleteAuthorization(Long.parseLong({p}), {a})"),
      Map.entry("deleteGroup", "{sf}.deleteGroup({p}, {a})"),
      Map.entry("deleteRole", "{sf}.deleteRole({p}, {a})"),
      Map.entry("deleteMappingRule", "{sf}.deleteMappingRule({p}, {a})"),
      Map.entry("deleteTenant", "{sf}.deleteTenant({p}, {a})"),
      Map.entry("deleteUser", "{sf}.deleteUser({p}, {a})"),
      Map.entry("deleteGlobalTaskListener", "{sf}.deleteGlobalListener({p}, {a})"),
      Map.entry("pinClock", "{sf}.pinClock(request.getTimestamp(), {a})"),
      Map.entry("resetClock", "{sf}.resetClock({a})"),
      Map.entry("assignUserTask", "{sf}.assignUserTask(Long.parseLong({p}), request, {a})"),
      Map.entry("unassignUserTask", "{sf}.unassignUserTask(Long.parseLong({p}), {a})"),
      Map.entry("completeUserTask", "{sf}.completeUserTask(Long.parseLong({p}), request, {a})"),
      Map.entry("updateUserTask", "{sf}.updateUserTask(Long.parseLong({p}), request, {a})"),
      Map.entry("updateJob", "{sf}.updateJob(Long.parseLong({p}), request, {a})"),
      Map.entry("failJob", "{sf}.failJob(Long.parseLong({p}), request, {a})"),
      Map.entry("throwJobError", "{sf}.errorJob(Long.parseLong({p}), request, {a})"),
      Map.entry("createElementInstanceVariables",
          "{sf}.setVariables(new io.camunda.service.ElementInstanceServices.SetVariablesRequest(Long.parseLong({p}), request.getVariables(), request.getLocal(), request.getOperationReference()), {a})"),
      Map.entry("deleteResource", "{sf}.deleteResource(Long.parseLong({p}), request, {a})"),
      Map.entry("cancelProcessInstance",
          "{sf}.cancelProcessInstance(Long.parseLong({p}), request, {a})"),
      Map.entry("deleteProcessInstance",
          "{sf}.deleteProcessInstance(Long.parseLong({p}), {a})"),
      Map.entry("resolveProcessInstanceIncidents",
          "{sf}.resolveProcessInstanceIncidents(Long.parseLong({p}), request, {a})"));

  /** MUTATION_RESPONSE endpoint wiring. Maps operationId → mapper/service/response wiring.
   *  These operations use a domain mapper to validate+convert the request, then call the service,
   *  and map the result to a typed response (or return Void for NO_CONTENT endpoints). */
  private static final Map<String, MutationResponseHint> MUTATION_RESPONSE_HINTS = Map.ofEntries(
      // Authorization
      Map.entry("createAuthorization", new MutationResponseHint(
          "authorizationMapper.toCreateAuthorizationRequest(request)",
          "{sf}.createAuthorization(%s, {a})",
          "ResponseMapper::toAuthorizationCreateResponse",
          "CREATED")),
      Map.entry("updateAuthorization", new MutationResponseHint(
          "authorizationMapper.toUpdateAuthorizationRequest(Long.parseLong({p}), request)",
          "{sf}.updateAuthorization(%s, {a})",
          null,
          "NO_CONTENT")),
      // User
      Map.entry("createUser", new MutationResponseHint(
          "userMapper.toUserRequest(request)",
          "{sf}.createUser(%s, {a})",
          "ResponseMapper::toUserCreateResponse",
          "CREATED")),
      Map.entry("updateUser", new MutationResponseHint(
          "userMapper.toUserUpdateRequest(request, {p})",
          "{sf}.updateUser(%s, {a})",
          "ResponseMapper::toUserUpdateResponse",
          "OK")),
      // MappingRule
      Map.entry("createMappingRule", new MutationResponseHint(
          "mappingRuleMapper.toMappingRuleCreateRequest(request)",
          "{sf}.createMappingRule(%s, {a})",
          "ResponseMapper::toMappingRuleCreateResponse",
          "CREATED")),
      Map.entry("updateMappingRule", new MutationResponseHint(
          "mappingRuleMapper.toMappingRuleUpdateRequest({p}, request)",
          "{sf}.updateMappingRule(%s, {a})",
          "ResponseMapper::toMappingRuleUpdateResponse",
          "OK")));

  /** Domain mapper dependencies per controller tag (PascalCase). Controllers with
   *  MUTATION_RESPONSE endpoints need a domain mapper for request validation + conversion. */
  private static final Map<String, MapperDependency> MAPPER_DEPENDENCIES = Map.of(
      "Authorization", new MapperDependency(
          "AuthorizationMapper", "authorizationMapper",
          "new AuthorizationMapper(new AuthorizationRequestValidator(new AuthorizationValidator(identifierValidator)))",
          List.of("io.camunda.gateway.mapping.http.mapper.AuthorizationMapper",
                  "io.camunda.gateway.mapping.http.validator.AuthorizationRequestValidator",
                  "io.camunda.security.validation.AuthorizationValidator")),
      "User", new MapperDependency(
          "UserMapper", "userMapper",
          "new UserMapper(new UserRequestValidator(new UserValidator(identifierValidator)))",
          List.of("io.camunda.gateway.mapping.http.mapper.UserMapper",
                  "io.camunda.gateway.mapping.http.validator.UserRequestValidator",
                  "io.camunda.security.validation.UserValidator")),
      "MappingRule", new MapperDependency(
          "MappingRuleMapper", "mappingRuleMapper",
          "new MappingRuleMapper(new MappingRuleRequestValidator(new MappingRuleValidator(identifierValidator)))",
          List.of("io.camunda.gateway.mapping.http.mapper.MappingRuleMapper",
                  "io.camunda.gateway.mapping.http.validator.MappingRuleRequestValidator",
                  "io.camunda.security.validation.MappingRuleValidator")));

  /** Sub-resource SEARCH endpoints (search under a parent key). Maps operationId → service
   *  method + whether to Long.parseLong the path param. */
  private static final Map<String, SubResourceSearchHint> SUB_RESOURCE_SEARCH_HINTS = Map.of(
      "searchElementInstanceIncidents", new SubResourceSearchHint("searchIncidents", true));

  /** Response type overrides for GET_BY_KEY when the spec schema name was remapped. */
  private static final Map<String, String> RESPONSE_TYPE_OVERRIDES = Map.of(
      "getIncident", "GeneratedIncidentStrictContract");

  /** Overrides for GET_BY_KEY response mapper method when it differs from convention. */
  private static final Map<String, String> GET_BY_KEY_RESPONSE_MAPPER_OVERRIDES = Map.of(
      "getDecisionInstance",
          "SearchQueryResponseMapper.toDecisionInstanceGetQueryResponse(result)");

  /** Operations to skip entirely (CUSTOM endpoints that need hand-written controllers). */
  private static final Set<String> SKIPPED_OPERATIONS = Set.of(
      // XML/text endpoints
      "getDecisionDefinitionXML", "getDecisionRequirementsXML", "getProcessDefinitionXML",
      // Document/file handling
      "createDocument", "createDocuments", "getDocument", "deleteDocument", "createDocumentLink",
      // Streaming/async endpoints
      "activateJobs",
      // Multi-part/complex body
      "createDeployment",
      // Special response shapes
      "createProcessInstance", "createProcessInstanceWithResult",
      "migrateProcessInstance", "modifyProcessInstance",
      "evaluateDecision", "evaluateExpression", "evaluateConditionals",
      "correlateMessage", "publishMessage", "broadcastSignal",
      "getAuthentication", "getLicense", "getTopology", "getStatus",
      "getUsageMetrics", "getSystemConfiguration", "createAdminUser",
      "getStartProcessForm", "getUserTaskForm",
      "getProcessInstanceCallHierarchy", "getProcessInstanceSequenceFlows",
      "activateAdHocSubProcessActivities",
      "getResource", "getResourceContent",
      // Batch operation creation (returns BatchOperationCreatedResult)
      "deleteDecisionInstancesBatchOperation",
      "cancelProcessInstancesBatchOperation",
      "deleteProcessInstancesBatchOperation",
      "resolveIncidentsBatchOperation",
      "migrateProcessInstancesBatchOperation",
      "modifyProcessInstancesBatchOperation",
      // CRUD mutations with response bodies (not yet wired via MUTATION_RESPONSE_HINTS)
      "createGroup", "updateGroup",
      "createRole", "updateRole",
      "createTenant", "updateTenant",
      "createGlobalTaskListener", "updateGlobalTaskListener",
      "completeJob",
      // Member assignment operations (different service patterns)
      "assignUserToGroup", "unassignUserFromGroup",
      "assignClientToGroup", "unassignClientFromGroup",
      "assignMappingRuleToGroup", "unassignMappingRuleFromGroup",
      "assignRoleToUser", "unassignRoleFromUser",
      "assignRoleToClient", "unassignRoleFromClient",
      "assignRoleToGroup", "unassignRoleFromGroup",
      "assignRoleToMappingRule", "unassignRoleFromMappingRule",
      "assignUserToTenant", "unassignUserFromTenant",
      "assignClientToTenant", "unassignClientFromTenant",
      "assignGroupToTenant", "unassignGroupFromTenant",
      "assignMappingRuleToTenant", "unassignMappingRuleFromTenant",
      "assignRoleToTenant", "unassignRoleFromTenant",
      // Cluster variable CRUD
      "createGlobalClusterVariable", "createTenantClusterVariable",
      "deleteGlobalClusterVariable", "deleteTenantClusterVariable",
      "updateGlobalClusterVariable", "updateTenantClusterVariable",
      "getGlobalClusterVariable", "getTenantClusterVariable",
      // Sub-resource searches (not yet wired via SUB_RESOURCE_SEARCH_HINTS)
      "searchProcessInstanceIncidents",
      "searchUserTaskAuditLogs",
      // Complex PATCH operations with response bodies
      "resolveProcessInstanceIncidents",
      // GET with query params (not a GET_BY_KEY pattern)
      "getGlobalJobStatistics",
      // Member searches without Phase 3 ResponseWrapperEntries
      "searchMappingRulesForGroup", "searchRolesForGroup",
      "searchMappingRulesForRole",
      "searchRolesForTenant", "searchMappingRulesForTenant",
      // Searches requiring extra params (truncateValues)
      "searchUserTaskVariables", "searchVariables");

  // -- Spec parsing --

  /** Reads all YAML files in the spec directory and extracts operation metadata. */
  private static List<SpecOperation> parseSpecOperations(Path specDir) throws IOException {
    var ops = new ArrayList<SpecOperation>();
    try (var stream = Files.list(specDir)) {
      var yamlFiles = stream
          .filter(p -> p.getFileName().toString().endsWith(".yaml"))
          .sorted(Comparator.comparing(p -> p.getFileName().toString()))
          .toList();
      for (var file : yamlFiles) {
        ops.addAll(parseOperationsFromFile(file));
      }
    }
    return ops;
  }

  private static List<SpecOperation> parseOperationsFromFile(Path file) throws IOException {
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
    var pathParams = new ArrayList<String>();
    var queryParams = new ArrayList<SpecQueryParam>();
    boolean bodyRequired = true; // default per OpenAPI spec
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
          ops.add(new SpecOperation(currentPath, currentMethod, operationId, tag,
              requestSchema, responseSchema, primaryStatusCode, List.copyOf(pathParams), List.copyOf(queryParams),
              bodyRequired, requiresSecondaryStorage, codeGenerationSkip, springConditional, springProfile,
              isMultipart, List.copyOf(multipartParts), isBinaryResponse, responseMediaType));
        }
        resetOpState:
        {
          currentPath = null; currentMethod = null; operationId = null; tag = null;
          requestSchema = null; responseSchema = null; primaryStatusCode = -1;
          pathParams.clear(); queryParams.clear(); section = null; sectionIndent = -1; inResponseSuccess = false;
          bodyRequired = true; requiresSecondaryStorage = false; codeGenerationSkip = false;
          springConditional = null; springProfile = null;
          isMultipart = false; multipartParts.clear(); isBinaryResponse = false; responseMediaType = null;
        }
        inPaths = tr.equals("paths:");
        continue;
      }

      if (!inPaths) continue;

      // Path entry (indent 2): "  /some/path:" or "  /some/{param}/action:"
      if (ind == 2 && tr.endsWith(":") && tr.startsWith("/")) {
        if (currentMethod != null && operationId != null) {
          ops.add(new SpecOperation(currentPath, currentMethod, operationId, tag,
              requestSchema, responseSchema, primaryStatusCode, List.copyOf(pathParams), List.copyOf(queryParams),
              bodyRequired, requiresSecondaryStorage, codeGenerationSkip, springConditional, springProfile,
              isMultipart, List.copyOf(multipartParts), isBinaryResponse, responseMediaType));
        }
        currentPath = tr.substring(0, tr.length() - 1);
        currentMethod = null; operationId = null; tag = null;
        requestSchema = null; responseSchema = null; primaryStatusCode = -1;
        pathParams.clear(); queryParams.clear(); section = null; sectionIndent = -1; inResponseSuccess = false;
        bodyRequired = true; requiresSecondaryStorage = false; codeGenerationSkip = false;
        springConditional = null; springProfile = null;
        isMultipart = false; multipartParts.clear(); isBinaryResponse = false; responseMediaType = null;
        continue;
      }

      // HTTP method (indent 4)
      if (ind == 4 && currentPath != null && tr.endsWith(":")) {
        String method = tr.substring(0, tr.length() - 1);
        if (Set.of("get", "post", "put", "delete", "patch").contains(method)) {
          if (currentMethod != null && operationId != null) {
            ops.add(new SpecOperation(currentPath, currentMethod, operationId, tag,
                requestSchema, responseSchema, primaryStatusCode, List.copyOf(pathParams), List.copyOf(queryParams),
                bodyRequired, requiresSecondaryStorage, codeGenerationSkip, springConditional, springProfile,
                isMultipart, List.copyOf(multipartParts), isBinaryResponse, responseMediaType));
          }
          currentMethod = method;
          operationId = null; tag = null; requestSchema = null; responseSchema = null;
          primaryStatusCode = -1; pathParams.clear(); queryParams.clear(); section = null; sectionIndent = -1;
          inResponseSuccess = false;
          bodyRequired = true; requiresSecondaryStorage = false; codeGenerationSkip = false;
          springConditional = null; springProfile = null;
          isMultipart = false; multipartParts.clear(); isBinaryResponse = false; responseMediaType = null;
          continue;
        }
      }

      // Inside an operation body (indent >= 6)
      if (ind >= 6 && currentMethod != null) {
        // Track section boundaries at indent 6
        if (ind == 6) {
          section = null; sectionIndent = -1; inResponseSuccess = false;
          if (tr.startsWith("operationId:")) {
            operationId = unquote(tr.substring("operationId:".length()).trim());
          } else if (tr.equals("tags:")) {
            section = "tags"; sectionIndent = 6;
          } else if (tr.startsWith("requestBody:")) {
            section = "requestBody"; sectionIndent = 6;
          } else if (tr.equals("responses:")) {
            section = "responses"; sectionIndent = 6;
          } else if (tr.equals("parameters:")) {
            section = "parameters"; sectionIndent = 6;
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
            // Look ahead at following lines to determine param kind and type
            String paramIn = null;
            boolean paramRequired = false;
            String paramType = "String"; // default
            for (int j = i + 1; j < lines.size() && j < i + 10; j++) {
              String nextTr = trimmed(lines.get(j));
              if (nextTr.startsWith("- name:") || indent(lines.get(j)) <= 8) break;
              if (nextTr.equals("in: path")) paramIn = "path";
              else if (nextTr.equals("in: query")) paramIn = "query";
              else if (nextTr.startsWith("required:") && nextTr.contains("true")) paramRequired = true;
              else if (nextTr.startsWith("type: boolean")) paramType = "Boolean";
              else if (nextTr.startsWith("type: integer")) paramType = "Integer";
              else if (nextTr.startsWith("type: number")) paramType = "Double";
            }
            if ("path".equals(paramIn)) pathParams.add(paramName);
            else if ("query".equals(paramIn)) queryParams.add(new SpecQueryParam(paramName, paramType, paramRequired));
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
            if (ind == 12 && tr.endsWith(":") && !tr.startsWith("application/json")
                && !tr.startsWith("application/problem") && !tr.startsWith("schema")
                && !tr.startsWith("description") && !tr.startsWith("content")) {
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
      ops.add(new SpecOperation(currentPath, currentMethod, operationId, tag,
          requestSchema, responseSchema, primaryStatusCode, List.copyOf(pathParams), List.copyOf(queryParams),
          bodyRequired, requiresSecondaryStorage, codeGenerationSkip, springConditional, springProfile,
          isMultipart, List.copyOf(multipartParts), isBinaryResponse, responseMediaType));
    }
    return ops;
  }

  /** Extracts a schema name from a $ref like '#/components/schemas/Foo' or 'bar.yaml#/components/schemas/Foo'. */
  private static String extractSchemaFromRef(String ref) {
    String clean = unquote(ref);
    int idx = clean.indexOf("/components/schemas/");
    if (idx < 0) return null;
    return clean.substring(idx + "/components/schemas/".length());
  }

  /**
   * Forward-scans from just after {@code multipart/form-data:} to extract the inline schema
   * properties into {@code MultipartPart} records.
   *
   * <p>Detects:
   * <ul>
   *   <li>{@code format: binary} → {@code Part}</li>
   *   <li>{@code type: array} + items with {@code format: binary} → {@code List<Part>}</li>
   *   <li>{@code $ref: '#/.../Foo'} → {@code Foo}</li>
   *   <li>{@code type: array} + items with {@code $ref} → {@code List<Foo>}</li>
   *   <li>Simple {@code type: string} (no format) → {@code String}</li>
   * </ul>
   */
  private static void parseMultipartSchema(List<String> lines, int startIndex,
      List<MultipartPart> parts) {
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
          rawParts.add(new String[]{ currentPropName, resolveMultipartType(currentIsArray, currentIsBinary, currentRefSchema, currentSimpleType) });
          currentPropName = null; currentIsArray = false; currentIsBinary = false;
          currentRefSchema = null; currentSimpleType = null;
        }
        if (tr.equals("properties:")) {
          inProperties = true; inRequired = false;
        } else if (tr.equals("required:")) {
          inProperties = false; inRequired = true;
        } else {
          // Other fields at indent 14 like type, additionalProperties — ignore
        }
        continue;
      }

      // At indent 16: property names (in properties) or required items (in required)
      if (ind == 16) {
        if (inProperties) {
          // Flush previous property
          if (currentPropName != null) {
            rawParts.add(new String[]{ currentPropName, resolveMultipartType(currentIsArray, currentIsBinary, currentRefSchema, currentSimpleType) });
          }
          currentPropName = tr.endsWith(":") ? tr.substring(0, tr.length() - 1) : null;
          currentIsArray = false; currentIsBinary = false; currentRefSchema = null; currentSimpleType = null;
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
      rawParts.add(new String[]{ currentPropName, resolveMultipartType(currentIsArray, currentIsBinary, currentRefSchema, currentSimpleType) });
    }

    // Build MultipartPart records with required info
    for (var raw : rawParts) {
      parts.add(new MultipartPart(raw[0], raw[1], requiredNames.contains(raw[0])));
    }
  }

  /** Resolves the Java type for a multipart form-data property. */
  private static String resolveMultipartType(boolean isArray, boolean isBinary,
      String refSchema, String simpleType) {
    if (isBinary && isArray) return "List<Part>";
    if (isBinary) return "Part";
    if (refSchema != null) {
      String resolved = resolveSchemaType(refSchema);
      // If the resolved type is not a known protocol or strict-contract type,
      // it's a scalar alias (e.g. TenantId → type: string) — fall back to String.
      if (resolved.equals(refSchema) && !AVAILABLE_PROTOCOL_TYPES.contains(resolved)) {
        resolved = "String";
      }
      return isArray ? "List<" + resolved + ">" : resolved;
    }
    if (simpleType != null) return simpleType;
    return "String"; // fallback
  }

  // -- Classification --

  private static EndpointKind classifyEndpoint(SpecOperation op) {
    // MUTATION_RESPONSE: explicitly wired operations using domain mapper fold pattern
    if (MUTATION_RESPONSE_HINTS.containsKey(op.operationId())) {
      return EndpointKind.MUTATION_RESPONSE;
    }
    // SEARCH: POST + path ends with /search + 200 (includes sub-resource searches)
    if ("post".equals(op.httpMethod()) && op.path().endsWith("/search")
        && op.statusCode() == 200) {
      return EndpointKind.SEARCH;
    }
    // STATISTICS: POST + path contains "statistics" + 200
    if ("post".equals(op.httpMethod()) && op.path().contains("statistics")
        && op.statusCode() == 200) {
      return EndpointKind.STATISTICS;
    }
    // GET_BY_KEY: GET + has path params + 200 + has response schema
    if ("get".equals(op.httpMethod()) && !op.pathParams().isEmpty()
        && op.statusCode() == 200 && op.responseSchema() != null) {
      return EndpointKind.GET_BY_KEY;
    }
    // MUTATION_VOID: non-GET + 204 + no response schema
    if (!"get".equals(op.httpMethod()) && op.statusCode() == 204
        && op.responseSchema() == null) {
      return EndpointKind.MUTATION_VOID;
    }
    return null; // CUSTOM — not handled
  }

  // -- Naming helpers --

  /** Converts a tag like "Batch operation" or "Decision definition" to PascalCase "BatchOperation". */
  private static String tagToPascalCase(String tag) {
    var sb = new StringBuilder();
    for (String word : tag.split("[\\s\\-]+")) {
      if (!word.isEmpty()) {
        sb.append(Character.toUpperCase(word.charAt(0)));
        if (word.length() > 1) sb.append(word.substring(1));
      }
    }
    return sb.toString();
  }

  private static String lowerFirst(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }

  /** Strips a SearchQuery/SearchQueryRequest suffix to get the entity name. */
  private static String entityFromRequestSchema(String requestSchema) {
    if (requestSchema.endsWith("SearchQueryRequest")) {
      return requestSchema.substring(0, requestSchema.length() - "SearchQueryRequest".length());
    }
    if (requestSchema.endsWith("SearchQuery")) {
      return requestSchema.substring(0, requestSchema.length() - "SearchQuery".length());
    }
    return requestSchema;
  }

  /** Derives the entity name for a GET_BY_KEY response mapper from the response schema. */
  private static String entityFromResponseSchema(String responseSchema) {
    if (responseSchema.endsWith("Result")) {
      return responseSchema.substring(0, responseSchema.length() - "Result".length());
    }
    if (responseSchema.endsWith("Response")) {
      return responseSchema.substring(0, responseSchema.length() - "Response".length());
    }
    return responseSchema;
  }

  // -- Build controller entries from spec --

  private static List<ControllerEntry> buildControllerEntriesFromSpec() throws IOException {
    final var ops = parseSpecOperations(SPEC_DIR);
    System.out.println("Parsed " + ops.size() + " operations from spec.");

    // Build lookup: request schema → RequestEntry (from Phase 3 data)
    final Map<String, RequestEntry> requestBySchema = new HashMap<>();
    for (var e : REQUEST_ENTRIES) {
      requestBySchema.put(e.requestClass(), e);
    }

    // Build lookup: response schema name → ResponseWrapperEntry (for SEARCH response mappers)
    // The wrapper entry's methodSuffix may end in "Response" where the spec schema uses "Result".
    // Some spec schemas use "SearchResult" instead of "SearchQueryResult" (e.g. UserSearchResult).
    final Map<String, ResponseWrapperEntry> wrapperByResponseSchema = new HashMap<>();
    for (var e : RESPONSE_WRAPPER_ENTRIES) {
      String suffix = e.methodSuffix();
      if (suffix.endsWith("Response")) {
        // "XxxSearchQueryResponse" → spec "XxxSearchQueryResult"
        String key1 = suffix.substring(0, suffix.length() - "Response".length()) + "Result";
        wrapperByResponseSchema.put(key1, e);
        // Also "XxxSearchQueryResponse" → spec "XxxSearchResult" (without "Query")
        String key2 = key1.replace("SearchQueryResult", "SearchResult");
        if (!key2.equals(key1)) wrapperByResponseSchema.put(key2, e);
      }
      if (suffix.endsWith("Result")) {
        wrapperByResponseSchema.put(suffix, e);
        // Also "XxxSearchQueryResult" → spec "XxxSearchResult"
        String alt = suffix.replace("SearchQueryResult", "SearchResult");
        if (!alt.equals(suffix)) wrapperByResponseSchema.put(alt, e);
      }
    }

    // Build lookup: entity name → SingleEntityEntry (for GET_BY_KEY response mappers)
    final Map<String, SingleEntityEntry> singleEntityByName = new HashMap<>();
    for (var e : SINGLE_ENTITY_ENTRIES) {
      // methodName is like "toIncident" → entity name is "Incident"
      if (e.methodName().startsWith("to")) {
        singleEntityByName.put(e.methodName().substring(2), e);
      }
    }

    // Group operations by tag → list of endpoints
    final var tagEndpoints = new LinkedHashMap<String, List<ControllerEndpoint>>();
    int searchCount = 0, getCount = 0, statsCount = 0, mutCount = 0, mutRespCount = 0, skipCount = 0;

    for (var op : ops) {
      if (op.tag() == null || op.operationId() == null) continue;
      if (SKIPPED_OPERATIONS.contains(op.operationId())) {
        skipCount++;
        continue;
      }

      EndpointKind kind = classifyEndpoint(op);
      if (kind == null) {
        skipCount++;
        continue;
      }

      String tagPascal = tagToPascalCase(op.tag());
      String serviceField = lowerFirst(tagPascal) + "Services";
      ControllerEndpoint ep = null;

      switch (kind) {
        case SEARCH -> {
          ep = buildSearchEndpoint(op, tagPascal, serviceField, requestBySchema, wrapperByResponseSchema);
          if (ep != null) searchCount++;
        }
        case GET_BY_KEY -> {
          ep = buildGetByKeyEndpoint(op, serviceField, singleEntityByName);
          if (ep != null) getCount++;
        }
        case STATISTICS -> {
          ep = buildStatisticsEndpoint(op, serviceField);
          if (ep != null) statsCount++;
        }
        case MUTATION_VOID -> {
          ep = buildMutationVoidEndpoint(op, serviceField);
          if (ep != null) mutCount++;
        }
        case MUTATION_RESPONSE -> {
          ep = buildMutationResponseEndpoint(op, serviceField);
          if (ep != null) mutRespCount++;
        }
      }

      if (ep != null) {
        tagEndpoints.computeIfAbsent(op.tag(), k -> new ArrayList<>()).add(ep);
      } else {
        System.out.println("  skipped: " + op.operationId() + " (no wiring data)");
        skipCount++;
      }
    }

    System.out.println("Classified: search=" + searchCount + " getByKey=" + getCount
        + " statistics=" + statsCount + " mutationVoid=" + mutCount
        + " mutationResponse=" + mutRespCount + " skipped=" + skipCount);

    // Build map of ALL operationIds per tag (to check controller completeness)
    final var allOpsPerTag = new LinkedHashMap<String, Set<String>>();
    for (var op : ops) {
      if (op.tag() != null && op.operationId() != null) {
        allOpsPerTag.computeIfAbsent(op.tag(), k -> new LinkedHashSet<>()).add(op.operationId());
      }
    }

    // Only generate controllers where ALL operations for the tag are covered.
    // If any operations are missing (skipped or no wiring), the controller would
    // fail to implement all abstract methods from the API interface.
    var entries = new ArrayList<ControllerEntry>();
    for (var entry : tagEndpoints.entrySet()) {
      String tag = entry.getKey();
      var generatedOps = entry.getValue().stream()
          .map(ControllerEndpoint::methodName)
          .collect(Collectors.toSet());
      var allOps = allOpsPerTag.getOrDefault(tag, Set.of());
      var missing = new LinkedHashSet<>(allOps);
      missing.removeAll(generatedOps);
      missing.removeAll(SKIPPED_OPERATIONS);
      if (!missing.isEmpty()) {
        System.out.println("  skip controller " + tagToPascalCase(tag)
            + ": missing ops " + missing);
        continue;
      }
      // Also skip if ANY of the tag's spec operations are in SKIPPED_OPERATIONS
      // (the API interface still declares those methods as abstract)
      var skippedForTag = new LinkedHashSet<>(allOps);
      skippedForTag.retainAll(SKIPPED_OPERATIONS);
      if (!skippedForTag.isEmpty()) {
        System.out.println("  skip controller " + tagToPascalCase(tag)
            + ": has skipped ops " + skippedForTag);
        continue;
      }
      String tagPascal = tagToPascalCase(tag);
      entries.add(new ControllerEntry(
          "Generated" + tagPascal + "Controller",
          tagPascal + "Api",
          "io.camunda.gateway.protocol.api." + tagPascal + "Api",
          tagPascal + "Services",
          "io.camunda.service." + tagPascal + "Services",
          lowerFirst(tagPascal) + "Services",
          entry.getValue()));
    }

    entries.sort(Comparator.comparing(ControllerEntry::className));
    return entries;
  }

  // -- Universal controller entry builder --

  /**
   * Builds universal controller entries for ALL tags, entirely from the spec.
   * No dependency on generated API interfaces.
   * Operations with {@code x-code-generation: skip} are excluded from generation.
   */
  private static List<UniversalControllerEntry> buildUniversalControllerEntries() throws IOException {
    // Parse spec for all operations
    final var ops = parseSpecOperations(SPEC_DIR);
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

        EndpointKind kind = classifyEndpoint(op);

        // Build params: path params first, then query params, then request body or multipart parts
        var params = new ArrayList<SpecParam>();
        for (var pp : op.pathParams()) {
          params.add(new SpecParam(pp, "String", ParamKind.PATH, true));
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
          String paramName = Character.toLowerCase(op.requestSchema().charAt(0))
              + op.requestSchema().substring(1);
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

        endpoints.add(new UniversalEndpoint(
            op.operationId(),
            returnTypeParam,
            op.httpMethod(),
            op.path(),
            op.statusCode(),
            params,
            kind,
            op.requiresSecondaryStorage(),
            op.bodyRequired(),
            op.isMultipart(),
            op.isBinaryResponse(),
            op.responseMediaType()));
      }

      // Skip tags where all operations were excluded
      if (endpoints.isEmpty()) continue;

      entries.add(new UniversalControllerEntry(
          tagPascal,
          "Generated" + tagPascal + "Controller",
          endpoints,
          tagSpringConditional,
          tagSpringProfile));
    }

    entries.sort(Comparator.comparing(UniversalControllerEntry::className));
    System.out.println(entries.size() + " universal controller entries built.");
    return entries;
  }

  // -- Endpoint builders --

  private static ControllerEndpoint buildSearchEndpoint(
      SpecOperation op, String tagPascal, String serviceField,
      Map<String, RequestEntry> requestBySchema,
      Map<String, ResponseWrapperEntry> wrapperByResponseSchema) {

    if (op.requestSchema() == null || op.responseSchema() == null) return null;

    // Look up response wrapper entry first (required for all SEARCH endpoints)
    ResponseWrapperEntry wrapperEntry = wrapperByResponseSchema.get(op.responseSchema());
    if (wrapperEntry == null) {
      System.out.println("  warn: no ResponseWrapperEntry for schema " + op.responseSchema()
          + " (operationId=" + op.operationId() + ")");
      return null;
    }

    // Skip search endpoints with extra parameters (e.g. truncateValues) — these need custom code
    if (wrapperEntry.extraParamDecl() != null) {
      System.out.println("  skipped: " + op.operationId() + " (requires extra params: "
          + wrapperEntry.extraParamDecl() + ")");
      return null;
    }

    // Try Phase 3 lookup for request mapper, fall back to naming convention
    String requestMapperExpr;
    String queryImport;
    RequestEntry reqEntry = requestBySchema.get(op.requestSchema());
    if (reqEntry != null) {
      String entityName = reqEntry.methodName();
      requestMapperExpr = "SearchQueryRequestMapper.to" + entityName + "Query(request)";
      queryImport = reqEntry.queryImport() != null
          ? reqEntry.queryImport()
          : "io.camunda.search.query." + reqEntry.queryClass();
    } else {
      // Convention fallback: derive entity from request schema name
      String entity = entityFromRequestSchema(op.requestSchema());
      requestMapperExpr = "SearchQueryRequestMapper.to" + entity + "Query(request)";
      queryImport = "io.camunda.search.query." + entity + "Query";
    }

    String serviceMethod = SEARCH_SERVICE_OVERRIDES.getOrDefault(op.operationId(), "search");
    String serviceCallExpr;
    String pathParam = null;

    // Sub-resource SEARCH: path param passed to service before query
    SubResourceSearchHint subHint = SUB_RESOURCE_SEARCH_HINTS.get(op.operationId());
    if (subHint != null && !op.pathParams().isEmpty()) {
      pathParam = op.pathParams().get(0);
      serviceMethod = subHint.serviceMethod();
      serviceCallExpr = serviceField + "." + serviceMethod + "(%s, query, %s)";
    } else {
      serviceCallExpr = serviceField + "." + serviceMethod + "(query, %s)";
    }

    String responseMapperExpr = "SearchQueryResponseMapper.to" + wrapperEntry.methodSuffix() + "(result)";

    return new ControllerEndpoint(
        EndpointKind.SEARCH,
        op.operationId(),
        op.requestSchema(),
        op.responseSchema(),
        pathParam,
        requestMapperExpr,
        serviceCallExpr,
        responseMapperExpr,
        queryImport,
        null);
  }

  private static ControllerEndpoint buildGetByKeyEndpoint(
      SpecOperation op, String serviceField,
      Map<String, SingleEntityEntry> singleEntityByName) {

    if (op.responseSchema() == null || op.pathParams().isEmpty()) return null;

    String pathParam = op.pathParams().get(0);

    // Determine response type (with optional override for schema mappings)
    String responseType = RESPONSE_TYPE_OVERRIDES.getOrDefault(op.operationId(), op.responseSchema());

    // Determine response mapper
    String responseMapperExpr = GET_BY_KEY_RESPONSE_MAPPER_OVERRIDES.get(op.operationId());
    if (responseMapperExpr == null) {
      // Derive from entity name: strip Result/Response suffix, look up in SINGLE_ENTITY_ENTRIES
      String entity = entityFromResponseSchema(op.responseSchema());
      SingleEntityEntry seEntry = singleEntityByName.get(entity);
      if (seEntry != null) {
        responseMapperExpr = "SearchQueryResponseMapper." + seEntry.methodName() + "(result)";
      } else {
        // Fallback: try "to" + entity
        responseMapperExpr = "SearchQueryResponseMapper.to" + entity + "(result)";
      }
    }

    // Determine service call
    GetByKeyHint hint = GET_BY_KEY_HINTS.getOrDefault(op.operationId(),
        new GetByKeyHint("getByKey", true));
    String keyExpr = hint.parseLong() ? "Long.parseLong(%s)" : "%s";
    String serviceCallExpr = serviceField + "." + hint.serviceMethod() + "(" + keyExpr + ", %s)";

    return new ControllerEndpoint(
        EndpointKind.GET_BY_KEY,
        op.operationId(),
        null,
        responseType,
        pathParam,
        null,
        serviceCallExpr,
        responseMapperExpr,
        null,
        null);
  }

  private static ControllerEndpoint buildStatisticsEndpoint(SpecOperation op, String serviceField) {
    StatisticsHint hint = STATISTICS_HINTS.get(op.operationId());
    if (hint == null) return null;

    String requestMapperExpr = "SearchQueryRequestMapper.to"
        + op.requestSchema() + "(request)";
    String serviceCallExpr = serviceField + "." + hint.serviceMethod() + "(query, %s)";

    return new ControllerEndpoint(
        EndpointKind.STATISTICS,
        op.operationId(),
        op.requestSchema(),
        op.responseSchema(),
        null,
        requestMapperExpr,
        serviceCallExpr,
        hint.responseMapperMethod(),
        hint.queryTypeFqn(),
        null);
  }

  private static ControllerEndpoint buildMutationVoidEndpoint(SpecOperation op, String serviceField) {
    String template = MUTATION_VOID_HINTS.get(op.operationId());
    if (template == null) return null;

    String pathParam = op.pathParams().isEmpty() ? null : op.pathParams().get(0);
    String serviceCallExpr = template
        .replace("{sf}", serviceField)
        .replace("{p}", pathParam != null ? "%s" : "")
        .replace("{a}", "%s");

    return new ControllerEndpoint(
        EndpointKind.MUTATION_VOID,
        op.operationId(),
        op.requestSchema(),
        null,
        pathParam,
        null,
        serviceCallExpr,
        null,
        null,
        null);
  }

  private static ControllerEndpoint buildMutationResponseEndpoint(
      SpecOperation op, String serviceField) {
    MutationResponseHint hint = MUTATION_RESPONSE_HINTS.get(op.operationId());
    if (hint == null) return null;

    String pathParam = op.pathParams().isEmpty() ? null : op.pathParams().get(0);

    // Expand mapper method expression: {p} → actual path param name
    String mapperExpr = hint.mapperMethodExpr()
        .replace("{p}", pathParam != null ? pathParam : "");

    // Expand service call expression: {sf} → serviceField, {a} → %s (for auth)
    String serviceCallExpr = hint.serviceCallExpr()
        .replace("{sf}", serviceField)
        .replace("{a}", "%s");

    return new ControllerEndpoint(
        EndpointKind.MUTATION_RESPONSE,
        op.operationId(),
        op.requestSchema(),
        op.responseSchema(),   // null for Void-returning (e.g. updateAuthorization)
        pathParam,
        mapperExpr,            // requestMapperExpr → domain mapper fold expression
        serviceCallExpr,       // service call with %s for converted DTO and auth
        hint.responseMapperRef(),  // method reference or null for Void
        null,
        hint.httpStatus());    // validationLabel repurposed as httpStatus
  }

  // -- Render method: controller --

  private static String renderController(ControllerEntry ctrl) {
    final var sb = new StringBuilder();
    sb.append("""
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

""".formatted(CONTROLLER_PACKAGE));

    // Collect imports
    final var imports = new LinkedHashSet<String>();
    imports.add("jakarta.annotation.Generated");
    imports.add(ctrl.apiImport());
    imports.add(ctrl.serviceImport());
    imports.add("io.camunda.security.auth.CamundaAuthenticationProvider");
    imports.add("org.springframework.http.ResponseEntity");
    imports.add("org.springframework.web.bind.annotation.RequestMapping");
    imports.add("io.camunda.zeebe.gateway.rest.controller.CamundaRestController");

    boolean hasSearch = false;
    boolean hasGetByKey = false;
    boolean hasMutationVoid = false;
    boolean hasStatistics = false;
    boolean hasValidation = false;
    boolean hasMutationResponse = false;

    for (var ep : ctrl.endpoints()) {
      switch (ep.kind()) {
        case SEARCH -> hasSearch = true;
        case GET_BY_KEY -> hasGetByKey = true;
        case MUTATION_VOID -> hasMutationVoid = true;
        case STATISTICS -> hasStatistics = true;
        case MUTATION_RESPONSE -> hasMutationResponse = true;
      }
      if (ep.requestType() != null) {
        imports.add(PROTOCOL_PACKAGE + "." + ep.requestType());
      }
      if (ep.responseType() != null) {
        imports.add(PROTOCOL_PACKAGE + "." + ep.responseType());
      }
      if (ep.validationLabel() != null && ep.kind() != EndpointKind.MUTATION_RESPONSE) {
        hasValidation = true;
      }
    }

    // Look up mapper dependency for controllers with MUTATION_RESPONSE endpoints
    String tagPascal = ctrl.className().replace("Generated", "").replace("Controller", "");
    MapperDependency mapperDep = hasMutationResponse ? MAPPER_DEPENDENCIES.get(tagPascal) : null;

    if (hasSearch || hasStatistics) {
      imports.add(SEARCH_PACKAGE + ".SearchQueryRequestMapper");
      imports.add(SEARCH_PACKAGE + ".SearchQueryResponseMapper");
      imports.add("io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper");
    }
    if (hasGetByKey) {
      imports.add(SEARCH_PACKAGE + ".SearchQueryResponseMapper");
    }
    if (hasGetByKey || hasSearch || hasStatistics) {
      imports.add("static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse");
    }
    if (hasMutationVoid || hasMutationResponse) {
      imports.add("io.camunda.zeebe.gateway.rest.mapper.RequestExecutor");
    }
    if (hasMutationResponse) {
      imports.add("io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper");
      imports.add("org.springframework.http.HttpStatus");
      imports.add("io.camunda.gateway.mapping.http.ResponseMapper");
      if (mapperDep != null) {
        imports.addAll(mapperDep.imports());
        imports.add("io.camunda.security.validation.IdentifierValidator");
      }
    }
    if (hasValidation) {
      imports.add("jakarta.validation.ValidationException");
      imports.add("io.camunda.gateway.mapping.http.GatewayErrorMapper");
      imports.add("org.springframework.http.HttpStatus");
    }

    // Write imports (statics first, then regular)
    imports.stream()
        .filter(i -> i.startsWith("static "))
        .sorted()
        .forEach(i -> sb.append("import ").append(i).append(";\n"));
    if (imports.stream().anyMatch(i -> i.startsWith("static "))) {
      sb.append("\n");
    }
    imports.stream()
        .filter(i -> !i.startsWith("static "))
        .sorted()
        .forEach(i -> sb.append("import ").append(i).append(";\n"));

    // Class declaration
    sb.append("\n@CamundaRestController\n");
    sb.append("@RequestMapping(\"/v2\")\n");
    sb.append("@Generated(value = \"io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc\")\n");
    sb.append("public class ").append(ctrl.className());
    sb.append(" implements ").append(ctrl.apiInterface()).append(" {\n\n");

    // Fields
    sb.append("  private final ").append(ctrl.serviceClass()).append(" ").append(ctrl.serviceField()).append(";\n");
    sb.append("  private final CamundaAuthenticationProvider authenticationProvider;\n");
    if (mapperDep != null) {
      sb.append("  private final ").append(mapperDep.fieldType()).append(" ").append(mapperDep.fieldName()).append(";\n");
    }
    sb.append("\n");

    // Constructor
    sb.append("  public ").append(ctrl.className()).append("(\n");
    sb.append("      final ").append(ctrl.serviceClass()).append(" ").append(ctrl.serviceField()).append(",\n");
    if (mapperDep != null) {
      sb.append("      final CamundaAuthenticationProvider authenticationProvider,\n");
      sb.append("      final IdentifierValidator identifierValidator) {\n");
    } else {
      sb.append("      final CamundaAuthenticationProvider authenticationProvider) {\n");
    }
    sb.append("    this.").append(ctrl.serviceField()).append(" = ").append(ctrl.serviceField()).append(";\n");
    sb.append("    this.authenticationProvider = authenticationProvider;\n");
    if (mapperDep != null) {
      sb.append("    ").append(mapperDep.fieldName()).append(" = ").append(mapperDep.constructExpr()).append(";\n");
    }
    sb.append("  }\n");

    // Endpoint methods
    for (var ep : ctrl.endpoints()) {
      sb.append("\n");
      switch (ep.kind()) {
        case SEARCH -> renderSearchEndpoint(sb, ep);
        case GET_BY_KEY -> renderGetByKeyEndpoint(sb, ep);
        case MUTATION_VOID -> renderMutationVoidEndpoint(sb, ep);
        case STATISTICS -> renderStatisticsEndpoint(sb, ep);
        case MUTATION_RESPONSE -> renderMutationResponseEndpoint(sb, ep);
      }
    }

    sb.append("}\n");
    return sb.toString();
  }

  private static void renderSearchEndpoint(StringBuilder sb, ControllerEndpoint ep) {
    final boolean hasPathParam = ep.pathParam() != null;
    sb.append("  @Override\n");
    sb.append("  public ResponseEntity<").append(ep.responseType()).append("> ");
    sb.append(ep.methodName()).append("(\n");
    if (hasPathParam) {
      sb.append("      final String ").append(ep.pathParam()).append(",\n");
    }
    sb.append("      final ").append(ep.requestType()).append(" request) {\n");
    sb.append("    return ").append(ep.requestMapperExpr()).append("\n");
    if (hasPathParam) {
      // Sub-resource SEARCH: lambda passes path param to internal method
      SubResourceSearchHint subHint = SUB_RESOURCE_SEARCH_HINTS.get(ep.methodName());
      String keyExpr = (subHint != null && subHint.parseLong())
          ? "Long.parseLong(" + ep.pathParam() + ")"
          : ep.pathParam();
      sb.append("        .fold(RestErrorMapper::mapProblemToResponse,\n");
      sb.append("            query -> ").append(ep.methodName()).append("Internal(").append(keyExpr).append(", query));\n");
    } else {
      sb.append("        .fold(RestErrorMapper::mapProblemToResponse, this::").append(ep.methodName()).append("Internal);\n");
    }
    sb.append("  }\n\n");

    // Private search helper
    sb.append("  private ResponseEntity<").append(ep.responseType()).append("> ");
    if (hasPathParam) {
      SubResourceSearchHint subHint = SUB_RESOURCE_SEARCH_HINTS.get(ep.methodName());
      String paramType = (subHint != null && subHint.parseLong()) ? "long" : "String";
      sb.append(ep.methodName()).append("Internal(\n");
      sb.append("      final ").append(paramType).append(" ").append(ep.pathParam()).append(",\n");
      sb.append("      final ").append(ep.internalQueryType()).append(" query) {\n");
    } else {
      sb.append(ep.methodName()).append("Internal(final ").append(ep.internalQueryType()).append(" query) {\n");
    }
    sb.append("    try {\n");
    sb.append("      final var result =\n");
    if (hasPathParam) {
      sb.append("          ").append(ep.serviceCallExpr().formatted(ep.pathParam(), "authenticationProvider.getCamundaAuthentication()")).append(";\n");
    } else {
      sb.append("          ").append(ep.serviceCallExpr().formatted("authenticationProvider.getCamundaAuthentication()")).append(";\n");
    }
    sb.append("      return ResponseEntity.ok(\n");
    sb.append("          ").append(ep.responseMapperExpr()).append(");\n");
    if (ep.validationLabel() != null) {
      sb.append("    } catch (final ValidationException e) {\n");
      sb.append("      final var problemDetail =\n");
      sb.append("          GatewayErrorMapper.createProblemDetail(\n");
      sb.append("              HttpStatus.BAD_REQUEST,\n");
      sb.append("              e.getMessage(),\n");
      sb.append("              \"Validation failed for ").append(ep.validationLabel()).append("\");\n");
      sb.append("      return RestErrorMapper.mapProblemToResponse(problemDetail);\n");
    }
    sb.append("    } catch (final Exception e) {\n");
    sb.append("      return mapErrorToResponse(e);\n");
    sb.append("    }\n");
    sb.append("  }\n");
  }

  private static void renderGetByKeyEndpoint(StringBuilder sb, ControllerEndpoint ep) {
    sb.append("  @Override\n");
    final String returnTypeParam = ep.responseType() != null ? ep.responseType() : "?";
    sb.append("  public ResponseEntity<").append(returnTypeParam).append("> ");
    sb.append(ep.methodName()).append("(final String ").append(ep.pathParam()).append(") {\n");
    sb.append("    try {\n");
    // serviceCallExpr has two %s placeholders: first for the key param, second for auth
    sb.append("      final var result =\n");
    sb.append("          ").append(ep.serviceCallExpr().formatted(ep.pathParam(), "authenticationProvider.getCamundaAuthentication()")).append(";\n");
    sb.append("      return ResponseEntity.ok(\n");
    sb.append("          ").append(ep.responseMapperExpr()).append(");\n");
    sb.append("    } catch (final Exception e) {\n");
    sb.append("      return mapErrorToResponse(e);\n");
    sb.append("    }\n");
    sb.append("  }\n");
  }

  private static void renderMutationVoidEndpoint(StringBuilder sb, ControllerEndpoint ep) {
    sb.append("  @Override\n");
    final boolean hasBody = ep.requestType() != null;
    final boolean hasPath = ep.pathParam() != null;
    if (!hasPath && !hasBody) {
      sb.append("  public ResponseEntity<Void> ").append(ep.methodName()).append("() {\n");
    } else {
      sb.append("  public ResponseEntity<Void> ").append(ep.methodName()).append("(\n");
      if (hasPath) {
        sb.append("      final String ").append(ep.pathParam());
        if (hasBody) sb.append(",\n");
      }
      if (hasBody) {
        sb.append("      final ").append(ep.requestType()).append(" request");
      }
      sb.append(") {\n");
    }
    sb.append("    return RequestExecutor.executeSync(\n");
    sb.append("        () ->\n");
    sb.append("            ");
    // serviceCallExpr may have %s for pathParam and auth
    if (hasPath) {
      sb.append(ep.serviceCallExpr().formatted(ep.pathParam(), "authenticationProvider.getCamundaAuthentication()"));
    } else {
      sb.append(ep.serviceCallExpr().formatted("authenticationProvider.getCamundaAuthentication()"));
    }
    sb.append(");\n");
    sb.append("  }\n");
  }

  private static void renderStatisticsEndpoint(StringBuilder sb, ControllerEndpoint ep) {
    sb.append("  @Override\n");
    sb.append("  public ResponseEntity<").append(ep.responseType()).append("> ");
    sb.append(ep.methodName()).append("(\n");
    sb.append("      final ").append(ep.requestType()).append(" request) {\n");
    sb.append("    return ").append(ep.requestMapperExpr()).append("\n");
    sb.append("        .fold(RestErrorMapper::mapProblemToResponse, this::").append(ep.methodName()).append("Internal);\n");
    sb.append("  }\n\n");

    // Private statistics helper
    sb.append("  private ResponseEntity<").append(ep.responseType()).append("> ");
    sb.append(ep.methodName()).append("Internal(final ").append(ep.internalQueryType()).append(" query) {\n");
    sb.append("    try {\n");
    sb.append("      final var result =\n");
    sb.append("          ").append(ep.serviceCallExpr().formatted("authenticationProvider.getCamundaAuthentication()")).append(";\n");
    sb.append("      return ResponseEntity.ok(\n");
    sb.append("          ").append(ep.responseMapperExpr()).append(");\n");
    if (ep.validationLabel() != null) {
      sb.append("    } catch (final ValidationException e) {\n");
      sb.append("      final var problemDetail =\n");
      sb.append("          GatewayErrorMapper.createProblemDetail(\n");
      sb.append("              HttpStatus.BAD_REQUEST,\n");
      sb.append("              e.getMessage(),\n");
      sb.append("              \"Validation failed for ").append(ep.validationLabel()).append("\");\n");
      sb.append("      return RestErrorMapper.mapProblemToResponse(problemDetail);\n");
    }
    sb.append("    } catch (final Exception e) {\n");
    sb.append("      return mapErrorToResponse(e);\n");
    sb.append("    }\n");
    sb.append("  }\n");
  }

  private static void renderMutationResponseEndpoint(StringBuilder sb, ControllerEndpoint ep) {
    final boolean hasPath = ep.pathParam() != null;
    final boolean hasBody = ep.requestType() != null;
    final boolean returnsVoid = ep.responseMapperExpr() == null;
    final String httpStatus = ep.validationLabel(); // repurposed field for httpStatus
    final String returnType = returnsVoid ? "Void" : ep.responseType();

    // Method signature
    sb.append("  @Override\n");
    sb.append("  public ResponseEntity<").append(returnType).append("> ").append(ep.methodName()).append("(\n");
    if (hasPath) {
      sb.append("      final String ").append(ep.pathParam());
      if (hasBody) sb.append(",\n");
    }
    if (hasBody) {
      sb.append("      final ").append(ep.requestType()).append(" request");
    }
    sb.append(") {\n");

    // Body: mapper.fold → executeSync
    sb.append("    return ").append(ep.requestMapperExpr()).append("\n");
    sb.append("        .fold(\n");
    sb.append("            RestErrorMapper::mapProblemToResponse,\n");
    sb.append("            converted ->\n");
    if (returnsVoid) {
      // Void-returning: executeSync(supplier) → ResponseEntity<Void>
      sb.append("                RequestExecutor.executeSync(\n");
      sb.append("                    () ->\n");
      sb.append("                        ").append(ep.serviceCallExpr().formatted(
          "converted", "authenticationProvider.getCamundaAuthentication()")).append("));\n");
    } else {
      // Body-returning: executeSync(supplier, resultMapper, status) → ResponseEntity<T>
      sb.append("                RequestExecutor.executeSync(\n");
      sb.append("                    () ->\n");
      sb.append("                        ").append(ep.serviceCallExpr().formatted(
          "converted", "authenticationProvider.getCamundaAuthentication()")).append(",\n");
      sb.append("                    ").append(ep.responseMapperExpr()).append(",\n");
      sb.append("                    HttpStatus.").append(httpStatus).append("));\n");
    }
    sb.append("  }\n");
  }

  // ---------------------------------------------------------------------------
  // Phase 5: Universal controller + interface generation (delegate pattern)
  // ---------------------------------------------------------------------------

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
    if (raw.equals("StreamingResponseBody")) return "org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody";
    if (raw.equals("MultipartFile")) return "org.springframework.web.multipart.MultipartFile";
    if (raw.equals("Resource")) return "org.springframework.core.io.Resource";
    if (raw.startsWith("Generated") && raw.endsWith("StrictContract"))
      return TARGET_PACKAGE + "." + raw;
    if (raw.startsWith("Generated") && raw.endsWith("Enum"))
      return TARGET_PACKAGE + "." + raw;
    if (AVAILABLE_PROTOCOL_TYPES.contains(raw)) return PROTOCOL_PACKAGE + "." + raw;
    return null; // unknown — caller may need to add manually
  }

  /**
   * Resolves a spec schema name to the Java type name to use in generated code.
   * Prefers strict contract types when available, falling back to protocol model types.
   *
   * Special handling for search query schemas: The protocol model collapses request/result
   * into one name (e.g. "ProcessInstanceSearchQuery"), but the spec has both
   * "ProcessInstanceSearchQuery" (result: page/items) and the request body (filter/sort/page).
   * dtoClassName strips "Result", causing both to map to the same class name — with the
   * result schema overwriting the request. SEARCH_REQUEST_DTO_OVERRIDES maps the protocol
   * model request class to the correct Phase 3.5 request DTO.
   */
  private static String resolveSchemaType(String schemaName) {
    if (schemaName == null) return null;
    // Check Phase 3.5 search query request DTO overrides first.
    final var searchRequestDto = SEARCH_REQUEST_DTO_OVERRIDES.get(schemaName);
    if (searchRequestDto != null) return searchRequestDto;
    if (AVAILABLE_STRICT_CONTRACTS.contains(schemaName)) return dtoClassName(schemaName);
    if (AVAILABLE_PROTOCOL_TYPES.contains(schemaName)) return schemaName;
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

  /** Renders a universal controller that delegates ALL methods to validator + adapter interfaces. */
  private static String renderUniversalController(UniversalControllerEntry ctrl) {
    final var sb = new StringBuilder();
    sb.append("""
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

""".formatted(CONTROLLER_PACKAGE));

    // Collect imports
    final var imports = new LinkedHashSet<String>();
    imports.add("jakarta.annotation.Generated");
    imports.add("org.springframework.http.ResponseEntity");
    imports.add("org.springframework.web.bind.annotation.RequestMapping");
    imports.add("org.springframework.web.bind.annotation.RequestMethod");
    imports.add("io.camunda.zeebe.gateway.rest.controller.CamundaRestController");
    imports.add("io.camunda.security.auth.CamundaAuthenticationProvider");

    // Determine which Spring binding annotations are needed
    boolean hasPathVar = false, hasRequestBody = false, hasRequestParam = false, hasRequestPart = false;
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
    boolean needsPart = ctrl.endpoints().stream()
        .flatMap(ep -> ep.params().stream())
        .anyMatch(p -> p.javaType().equals("Part") || p.javaType().equals("List<Part>"));
    if (needsPart) imports.add("jakarta.servlet.http.Part");

    // Import List if any param uses List<>
    boolean needsList = ctrl.endpoints().stream()
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
    boolean hasBinaryResponse = ctrl.endpoints().stream()
        .anyMatch(UniversalEndpoint::isBinaryResponse);
    if (hasBinaryResponse) {
      imports.add("org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody");
    }

    // Import @RequiresSecondaryStorage if any endpoint needs it
    boolean hasRequiresSecondaryStorage = ctrl.endpoints().stream()
        .anyMatch(UniversalEndpoint::requiresSecondaryStorage);
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
      String simpleName = ctrl.springConditional().substring(
          ctrl.springConditional().lastIndexOf('.') + 1);
      sb.append("@").append(simpleName).append("\n");
    }
    sb.append("@CamundaRestController\n");
    // UserTask and Cluster require v1 backward compatibility (see TopologyController on main).
    if ("UserTask".equals(ctrl.tagPascal()) || "Cluster".equals(ctrl.tagPascal())) {
      sb.append("@RequestMapping(path = {\"/v1\", \"/v2\"})\n");
    } else {
      sb.append("@RequestMapping(\"/v2\")\n");
    }
    sb.append("@Generated(value = \"io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc\")\n");
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
    sb.append("      method = RequestMethod.").append(springRequestMethod(ep.httpMethod())).append(",\n");
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
      sb.append(",\n      produces = { \"").append(ep.responseMediaType())
          .append("\", \"application/problem+json\" }");
    } else {
      sb.append(",\n      produces = { \"application/json\", \"application/problem+json\" }");
    }
    sb.append(")\n");

    // Method signature
    sb.append("  public ResponseEntity<").append(returnType).append("> ").append(ep.methodName()).append("(");
    if (!ep.params().isEmpty()) {
      sb.append("\n");
      for (int i = 0; i < ep.params().size(); i++) {
        var param = ep.params().get(i);
        String annotation = switch (param.kind()) {
          case PATH -> "@PathVariable(\"" + param.name() + "\") ";
          case BODY -> ep.bodyRequired()
              ? "@RequestBody "
              : "@RequestBody(required = false) ";
          case QUERY -> "@RequestParam(name = \"" + param.name() + "\", required = " + false + ") ";
          case PART -> param.required()
              ? "@RequestPart(\"" + param.name() + "\") "
              : "@RequestPart(value = \"" + param.name() + "\", required = false) ";
        };
        sb.append("      ").append(annotation).append("final ").append(param.javaType())
            .append(" ").append(param.name());
        if (i < ep.params().size() - 1) sb.append(",");
        sb.append("\n");
      }
      sb.append("  ");
    }
    sb.append(") {\n");

    // Resolve authentication context once per request
    sb.append("    final var authentication = authenticationProvider.getCamundaAuthentication();\n");

    // Body: delegate directly to service adapter
    String adapterArgs = ep.params().stream()
        .map(SpecParam::name)
        .collect(Collectors.joining(", "));
    String adapterCall = "serviceAdapter." + ep.methodName() + "("
        + (adapterArgs.isEmpty() ? "authentication" : adapterArgs + ", authentication")
        + ")";

    sb.append("    return ").append(adapterCall).append(";\n");
    sb.append("  }\n");
  }

  /** Renders a ServiceAdapter interface with methods for all operations. */
  private static String renderServiceAdapterInterface(UniversalControllerEntry ctrl) {
    final var sb = new StringBuilder();
    sb.append("""
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

""".formatted(CONTROLLER_PACKAGE));

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
    boolean hasBinaryResponse = ctrl.endpoints().stream()
        .anyMatch(UniversalEndpoint::isBinaryResponse);
    if (hasBinaryResponse) {
      imports.add("org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody");
    }

    imports.stream().sorted().forEach(i -> sb.append("import ").append(i).append(";\n"));

    sb.append("\n/**\n");
    sb.append(" * Service adapter for ").append(ctrl.tagPascal()).append(" operations.\n");
    sb.append(" * Implements request mapping, service delegation, and response construction.\n");
    sb.append(" */\n");
    sb.append("@Generated(value = \"io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc\")\n");
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
      boolean hasParams = !ep.params().isEmpty();
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
}
