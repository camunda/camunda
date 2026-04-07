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
import java.util.HashSet;
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
   * Maps spec search query schema names to their Phase 3.5 request DTO class names.
   * Populated by discoverSearchQuerySchemas() in main(). Used by resolveSchemaType()
   * to route search request schemas to the correct DTO instead of the result DTO.
   */
  private static final Map<String, String> SEARCH_REQUEST_DTO_MAP = new LinkedHashMap<>();

  /**
   * Result schemas whose "Result" suffix must NOT be stripped by dtoClassName() because
   * the base name also exists as a schema (e.g. FooQuery + FooQueryResult). SearchQuery
   * collisions are excluded — they are handled by SEARCH_REQUEST_DTO_MAP instead.
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

    // Phase 1: compute fields for all schemas.
    record SchemaGenPlan(
        SchemaDef schema,
        String dtoClass,
        List<ContractField> fields) {}

    final var plans = new ArrayList<SchemaGenPlan>();

    for (var schema : contractSchemas) {
      final var fields = toContractFields(schema, allSchemas);
      final var dtoClass = dtoClassName(schema.schemaName());
      plans.add(new SchemaGenPlan(schema, dtoClass, fields));
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
        final var enumSchemaName = resolveFilterPropertyEnumSchema(
            parentSchema.node(), allSchemas, parentSchema.fileName());

        // Register the advanced filter as implementing the sealed interface.
        branchToSealedInterface.put(branchNames.getFirst(), sealedName);

        // (a) Generate plain-value wrapper record
        final var wrapperFile = packagePath.resolve(wrapperName + ".java");
        Files.writeString(
            wrapperFile,
            renderFilterPlainValueWrapper(
                parentSchema.fileName(), parentSchemaName, sealedName, wrapperName,
                primitiveJavaType),
            StandardCharsets.UTF_8);
        System.out.println("generated filter wrapper: " + ROOT.relativize(wrapperFile));

        // (b) Generate custom deserializer
        final var deserializerFile = packagePath.resolve(deserializerName + ".java");
        Files.writeString(
            deserializerFile,
            renderFilterPropertyDeserializer(
                parentSchema.fileName(), parentSchemaName, sealedName, wrapperName,
                advancedFilterDtoClass, deserializerName, primitiveJavaType, isLongKey,
                enumSchemaName, allSchemas),
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

        // Compute discriminating fields for each branch: required fields whose property names
        // are unique to that branch (not present as properties in any other branch).
        // This lets us generate a custom deserializer with helpful error messages.
        final var branchDiscriminators = new ArrayList<PolymorphicBranch>();
        final var allBranchPropertyNames = new LinkedHashSet<String>();
        final var branchSchemas = new ArrayList<SchemaDef>();
        for (var branchName : branchNames) {
          final var branchSchema = allSchemas.values().stream()
              .filter(s -> s.schemaName().equals(branchName))
              .findFirst().orElse(null);
          branchSchemas.add(branchSchema);
          if (branchSchema != null) {
            allBranchPropertyNames.addAll(branchSchema.node().properties().keySet());
          }
        }
        for (int bi = 0; bi < branchNames.size(); bi++) {
          final var branchSchema = branchSchemas.get(bi);
          final var dtoClass = branchDtoClasses.get(bi);
          if (branchSchema != null) {
            // Unique properties: present in this branch but not in any other branch.
            final var otherBranchProps = new LinkedHashSet<String>();
            for (int oi = 0; oi < branchSchemas.size(); oi++) {
              if (oi != bi && branchSchemas.get(oi) != null) {
                otherBranchProps.addAll(branchSchemas.get(oi).node().properties().keySet());
              }
            }
            final var uniqueProps = new ArrayList<>(branchSchema.node().properties().keySet());
            uniqueProps.removeAll(otherBranchProps);
            branchDiscriminators.add(new PolymorphicBranch(dtoClass, uniqueProps));
          } else {
            branchDiscriminators.add(new PolymorphicBranch(dtoClass, List.of()));
          }
        }

        final boolean hasDiscriminators = branchDiscriminators.stream()
            .anyMatch(b -> !b.uniqueFields().isEmpty());

        if (hasDiscriminators) {
          // Generate custom deserializer with helpful error messages.
          final var deserializerName = sealedName.replace("StrictContract", "Deserializer");
          final var deserializerFile = packagePath.resolve(deserializerName + ".java");
          Files.writeString(
              deserializerFile,
              renderPolymorphicDeserializer(
                  parentSchema.fileName(), parentSchemaName, sealedName,
                  deserializerName, branchDiscriminators),
              StandardCharsets.UTF_8);
          System.out.println("generated polymorphic deserializer: " + ROOT.relativize(deserializerFile));

          final var sealedFile = packagePath.resolve(sealedName + ".java");
          Files.writeString(
              sealedFile,
              renderPolymorphicSealedInterfaceWithDeserializer(
                  parentSchema.fileName(), parentSchemaName, branchDtoClasses, deserializerName),
              StandardCharsets.UTF_8);
          System.out.println("generated sealed interface: " + ROOT.relativize(sealedFile));
        } else {
          // Fall back to Jackson DEDUCTION (no unique discriminating fields available).
          final var sealedFile = packagePath.resolve(sealedName + ".java");
          Files.writeString(
              sealedFile,
              renderPolymorphicSealedInterface(
                  parentSchema.fileName(), parentSchemaName, branchDtoClasses),
              StandardCharsets.UTF_8);
          System.out.println("generated sealed interface: " + ROOT.relativize(sealedFile));
        }
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
    }
    System.out.println(dtoCount + " strict contract DTOs generated.");

    // Phase 3.5: Generate flat page request DTO and search query request DTOs.
    // Discover search query schemas directly from the spec (schemas extending SearchQueryRequest).
    final var searchQuerySchemas = discoverSearchQuerySchemas(allSchemas);
    System.out.println(searchQuerySchemas.size() + " search query request schemas discovered from spec.");

    final var flatPageFile = packagePath.resolve("GeneratedSearchQueryPageRequestStrictContract.java");
    Files.writeString(flatPageFile, renderFlatPageRequestDto(), StandardCharsets.UTF_8);
    System.out.println("generated: " + ROOT.relativize(flatPageFile));

    final var offsetPageFile = packagePath.resolve("GeneratedOffsetPaginationStrictContract.java");
    Files.writeString(offsetPageFile, renderOffsetPaginationDto(), StandardCharsets.UTF_8);
    System.out.println("generated: " + ROOT.relativize(offsetPageFile));

    int searchRequestDtoCount = 0;
    for (var sqe : searchQuerySchemas) {
      final var requestDtoName = sqe.requestDtoName();
      final var sortContractClass = sqe.sortSchemaName() != null
          ? dtoClassName(sqe.sortSchemaName()) : null;
      final var filterContractFqn = sqe.filterSchemaName() != null
          ? TARGET_PACKAGE + "." + dtoClassName(sqe.filterSchemaName()) : null;
      final var requestDtoFile = packagePath.resolve(requestDtoName + ".java");
      Files.writeString(requestDtoFile, renderSearchQueryRequestDto(
          requestDtoName, sortContractClass, filterContractFqn, sqe.paginationType()), StandardCharsets.UTF_8);
      System.out.println("generated: " + ROOT.relativize(requestDtoFile));

      // Populate the override map so resolveSchemaType() routes this schema to the request DTO.
      SEARCH_REQUEST_DTO_MAP.put(sqe.schemaName(), requestDtoName);
      searchRequestDtoCount++;
    }
    System.out.println(searchRequestDtoCount + " search query request DTOs generated.");

    // Phase 3.7: Generate filter mappers for search query entities.
    // For each search query with a filter schema, generate a mapper that converts
    // the generated strict-contract filter DTO to the domain filter object.
    //
    // Only entities whose domain filter builders follow the Operations convention
    // (filter property → builder::fieldOperations) are generated here. Entities
    // with naming mismatches, old-style list builders, or custom validation needs
    // are handled by hand-written validator/mapper files (Phase 2 of slice 5).
    int filterMapperCount = 0;
    for (var sqe : searchQuerySchemas) {
      if (sqe.filterSchemaName() == null) continue;
      if (FILTER_MAPPER_SKIP.contains(sqe.filterSchemaName())) continue;

      final var filterSchema = findSchemaByName(allSchemas, sqe.filterSchemaName());
      if (filterSchema == null) {
        System.err.println("WARNING: filter schema '" + sqe.filterSchemaName()
            + "' not found, skipping filter mapper generation");
        continue;
      }

      final var filterFields = toContractFields(filterSchema, allSchemas);
      final var filterDtoClass = dtoClassName(sqe.filterSchemaName());
      final var mapperClassName = "Generated" + sqe.filterSchemaName() + "Mapper";
      final var mapperFile = packagePath.resolve(mapperClassName + ".java");
      Files.writeString(mapperFile, renderFilterMapper(
          mapperClassName, sqe, filterSchema, filterDtoClass, filterFields, allSchemas),
          StandardCharsets.UTF_8);
      System.out.println("generated: " + ROOT.relativize(mapperFile));
      filterMapperCount++;
    }
    System.out.println(filterMapperCount + " filter mapper(s) generated.");
    System.out.println(FILTER_MAPPER_SKIP.size() + " filter mapper(s) skipped (need hand-written mappers).");

    // Phase 4: Generate universal controllers + ServiceAdapter interfaces.
    // Controllers delegate directly to the service adapter; semantic validation
    // (business rules not expressible in the spec) lives in the hand-written
    // validators called by RequestMapper inside the service adapter layer.
    final var controllerOutBase =
        ROOT.resolve("../../zeebe/gateway-rest/src/main/java").normalize();
    final var controllerPackagePath =
        controllerOutBase.resolve(CONTROLLER_PACKAGE.replace('.', '/'));
    Files.createDirectories(controllerPackagePath);
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

    // Format generated files with google-java-format via Maven Spotless.
    formatGeneratedFiles();
  }

  /**
   * Runs {@code mvn spotless:apply} on modules containing generated code so that the output
   * matches the project's google-java-format configuration. Without this step, the pre-commit
   * spotless hook reformats every generated file, creating noisy diffs for developers.
   */
  private static void formatGeneratedFiles() throws Exception {
    final var repoRoot = ROOT.resolve("../..").normalize().toFile();
    final var mvnw = System.getProperty("os.name").toLowerCase().contains("win")
        ? "mvnw.cmd" : "./mvnw";
    System.out.println("Formatting generated files (spotless:apply)...");
    final var process = new ProcessBuilder(
        mvnw, "spotless:apply",
        "-pl", "gateways/gateway-mapping-http,zeebe/gateway-rest",
        "-T1C", "-q")
        .directory(repoRoot)
        .inheritIO()
        .start();
    final var exitCode = process.waitFor();
    if (exitCode != 0) {
      System.err.println("Warning: spotless:apply exited with code " + exitCode
          + ". Run 'mvn spotless:apply' manually to format generated files.");
    } else {
      System.out.println("Formatting complete.");
    }
  }

  /**
   * Discovers schemas that appear only in response paths (not in request paths) by tracing $ref
   * chains from the {@code paths:} block in each OpenAPI YAML file.
   *
   * <p>Schemas referenced (directly or transitively) from {@code requestBody:} blocks are excluded.
   * This is used to determine which DTOs are request schemas (needing strict validation) vs
   * response-only schemas.
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

  // -- Search query schema discovery (spec-driven) --

  /** Pagination type for a search query schema. */
  private enum PaginationType {
    /** Full pagination: offset + cursor (inherited from SearchQueryRequest). */
    FULL,
    /** Offset-only pagination: limit + from (references OffsetPagination directly). */
    OFFSET_ONLY
  }

  /**
   * A search query request schema discovered from the spec. Contains only spec-derived data:
   * the schema name, entity name (for the request DTO class name), the sort/filter schema
   * names extracted from the schema's properties, and the pagination type.
   */
  private record SearchQuerySchemaEntry(
      String schemaName,
      String entityName,
      String sortSchemaName,
      String filterSchemaName,
      PaginationType paginationType) {

    String requestDtoName() {
      return "Generated" + entityName + "SearchQueryRequestStrictContract";
    }
  }

  /**
   * Discovers all search query request schemas from the spec. A schema is a search query request
   * if it either:
   * <ul>
   *   <li>extends {@code SearchQueryRequest} via allOf (full pagination: offset + cursor), or</li>
   *   <li>defines its own {@code page}, {@code sort}, and {@code filter} properties where
   *       {@code page} references {@code OffsetPagination} (offset-only pagination).</li>
   * </ul>
   *
   * <p>For each, extracts the sort and filter schema names from the schema's own properties
   * (sort.items.$ref and filter.allOfRefs).
   *
   * <p>This replaces the hand-maintained REQUEST_ENTRIES table — the spec is the single source
   * of truth for which schemas are search query requests and what their sort/filter types are.
   */
  private static List<SearchQuerySchemaEntry> discoverSearchQuerySchemas(
      Map<SchemaKey, SchemaDef> allSchemas) {
    var entries = new ArrayList<SearchQuerySchemaEntry>();

    for (var schemaDef : allSchemas.values()) {
      final var node = schemaDef.node();

      // Pattern 1: extends SearchQueryRequest via allOf (full pagination).
      boolean extendsSearchQueryRequest = node.allOfRefs().stream()
          .anyMatch(ref -> ref.contains("/schemas/SearchQueryRequest"));

      // Pattern 2: has page→OffsetPagination + sort + filter (offset-only pagination).
      boolean isOffsetOnlySearchQuery = false;
      if (!extendsSearchQueryRequest) {
        final var pageProp = node.properties().get("page");
        final var hasSortProp = node.properties().containsKey("sort");
        final var hasFilterProp = node.properties().containsKey("filter");
        if (pageProp != null && hasSortProp && hasFilterProp) {
          isOffsetOnlySearchQuery = pageProp.allOfRefs().stream()
              .anyMatch(ref -> ref.contains("/schemas/OffsetPagination"));
        }
      }

      if (!extendsSearchQueryRequest && !isOffsetOnlySearchQuery) continue;

      final var paginationType = extendsSearchQueryRequest
          ? PaginationType.FULL : PaginationType.OFFSET_ONLY;

      final var schemaName = schemaDef.schemaName();

      // Derive entity name: strip "SearchQuery", "SearchQueryRequest" suffixes.
      var entityName = schemaName
          .replace("SearchQueryRequest", "")
          .replace("SearchQuery", "");

      // Extract sort schema from properties.sort.items.$ref
      String sortSchemaName = null;
      final var sortProp = node.properties().get("sort");
      if (sortProp != null && sortProp.items() != null && sortProp.items().ref() != null) {
        sortSchemaName = toSchemaKey(sortProp.items().ref(), schemaDef.fileName()).schemaName();
      }
      if (sortProp != null && sortSchemaName == null) {
        System.err.println("WARNING: search query schema '" + schemaName
            + "' has a 'sort' property but sort type could not be resolved"
            + " (items.$ref missing — check YAML indentation). Generated DTO will use List<Object>.");
      }

      // Extract filter schema from properties.filter.allOfRefs or properties.filter.$ref
      String filterSchemaName = null;
      final var filterProp = node.properties().get("filter");
      if (filterProp != null) {
        if (!filterProp.allOfRefs().isEmpty()) {
          filterSchemaName = toSchemaKey(filterProp.allOfRefs().get(0), schemaDef.fileName()).schemaName();
        } else if (filterProp.ref() != null) {
          filterSchemaName = toSchemaKey(filterProp.ref(), schemaDef.fileName()).schemaName();
        }
      }

      entries.add(new SearchQuerySchemaEntry(
          schemaName, entityName, sortSchemaName, filterSchemaName, paginationType));
    }

    entries.sort(Comparator.comparing(SearchQuerySchemaEntry::schemaName));
    return entries;
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
   * Compound keys (in COMPOUND_KEY_EXCLUSIONS) are excluded because their values are
   * non-numeric strings (e.g. "1-149") that must not be validated as Long.
   */
  private static boolean isLongKeyFilterProperty(String schemaName) {
    if (!schemaName.endsWith("KeyFilterProperty")) {
      return false;
    }
    // Extract the key schema name: e.g. "DecisionEvaluationInstanceKeyFilterProperty"
    // → "DecisionEvaluationInstanceKey"
    final var keySchemaName = schemaName.substring(0, schemaName.length() - "FilterProperty".length());
    return !COMPOUND_KEY_EXCLUSIONS.contains(keySchemaName);
  }

  /**
   * Resolves the enum schema referenced by the inline oneOf branch of a filter property, if any.
   * Returns the enum schema name (e.g. "BatchOperationItemStateEnum") or null if the plain-value
   * branch does not reference an enum.
   */
  private static String resolveFilterPropertyEnumSchema(
      Node node, Map<SchemaKey, SchemaDef> allSchemas, String contextFile) {
    if (node.oneOfInlineAllOfRefs().isEmpty()) {
      return null;
    }
    for (var ref : node.oneOfInlineAllOfRefs()) {
      final var schemaName = refToSchemaName(ref);
      // Look up in allSchemas to check if it's an enum
      final var key = new SchemaKey(contextFile, schemaName);
      final var target = allSchemas.get(key);
      if (target != null && isEnumSchema(target)) {
        return schemaName;
      }
    }
    return null;
  }

  /** Resolves the enum values list for a named enum schema. */
  private static List<String> resolveEnumValues(
      String enumSchemaName, String contextFile, Map<SchemaKey, SchemaDef> allSchemas) {
    final var key = new SchemaKey(contextFile, enumSchemaName);
    final var schema = allSchemas.get(key);
    if (schema == null || schema.node().enumValues().isEmpty()) {
      throw new IllegalStateException(
          "Expected enum schema '%s' in '%s' but not found or has no enum values"
              .formatted(enumSchemaName, contextFile));
    }
    return schema.node().enumValues();
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

  /**
   * Generates a sealed interface with @JsonDeserialize pointing to a custom deserializer,
   * replacing the @JsonTypeInfo(DEDUCTION) approach for polymorphic oneOf schemas where
   * we can provide better error messages.
   */
  private static String renderPolymorphicSealedInterfaceWithDeserializer(
      String sourceFile, String schemaName, List<String> branchDtoClasses,
      String deserializerName) {
    final var sealedName = dtoClassName(schemaName);
    final var permits = String.join(",\n        ", branchDtoClasses);
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
        %s {
}
"""
    .formatted(sourceFile, schemaName, TARGET_PACKAGE, deserializerName, sealedName, permits);
  }

  /**
   * Generates a custom Jackson deserializer for a polymorphic oneOf sealed interface.
   * Uses tree-model parsing to detect which branch's unique fields are present,
   * and produces a helpful error message when no branch matches.
   */
  private static String renderPolymorphicDeserializer(
      String sourceFile, String schemaName, String sealedName,
      String deserializerName, List<PolymorphicBranch> branches) {

    // Build the dispatching logic: for each branch, check if its unique field is present.
    final var branchCases = new StringBuilder();
    final var allUniqueFields = new ArrayList<String>();
    for (var branch : branches) {
      if (!branch.uniqueFields().isEmpty()) {
        allUniqueFields.addAll(branch.uniqueFields());
        final var fieldCheck = branch.uniqueFields().stream()
            .map(f -> "node.has(\"" + f + "\")")
            .collect(Collectors.joining(" && "));
        branchCases.append("""
          if (%s) {
            return p.getCodec().treeToValue(node, %s.class);
          }
    """.formatted(fieldCheck, branch.dtoClass()));
      }
    }

    // Build the error message listing discriminating fields (sorted for deterministic output).
    allUniqueFields.sort(null);
    final var fieldList = allUniqueFields.stream()
        .collect(Collectors.joining(", "));

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import jakarta.annotation.Generated;
import java.io.IOException;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class %s extends JsonDeserializer<%s> {

  @Override
  public %s deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    final JsonNode node = p.readValueAsTree();
%s
    throw ValueInstantiationException.from(p,
        "At least one of [%s] is required",
        ctxt.constructType(%s.class),
        new IllegalArgumentException("At least one of [%s] is required"));
  }
}
"""
    .formatted(sourceFile, schemaName, TARGET_PACKAGE,
        deserializerName, sealedName, sealedName,
        branchCases.toString(),
        fieldList, sealedName, fieldList);
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
      String primitiveJavaType, boolean isLongKey,
      String enumSchemaName, Map<SchemaKey, SchemaDef> allSchemas) {
    // Build the token-to-value mapping for the plain-value branch.
    final String plainValueCase;
    var enumSetStr = "";
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
      // LongKey: accept strings AND numbers (numbers converted to string).
      // Validate that string values are parseable as Long to fail fast with
      // INVALID_ARGUMENT instead of a generic Bad Request from downstream parsing.
      plainValueCase = """
          case VALUE_STRING -> {
            try {
              Long.parseLong(p.getText());
            } catch (NumberFormatException e) {
              throw InvalidFormatException.from(p,
                "The provided " + p.currentName() + " '" + p.getText() + "' is not a valid key. Expected a numeric value.",
                p.getText(), Long.class);
            }
            yield new %s(p.getText());
          }
          case VALUE_NUMBER_INT -> new %s(String.valueOf(p.getLongValue()));"""
        .formatted(wrapperName, wrapperName);
    } else if (enumSchemaName != null) {
      // Enum-typed: validate string against known enum values at deserialization time.
      final var enumValues = resolveEnumValues(enumSchemaName, sourceFile, allSchemas);
      final var enumListStr = enumValues.stream().collect(Collectors.joining(", "));
      enumSetStr = enumValues.stream()
          .map(v -> "\"" + v + "\"")
          .collect(Collectors.joining(", "));
      plainValueCase = """
          case VALUE_STRING -> {
            final var text = p.getText();
            if (!VALID_VALUES.contains(text)) {
              throw InvalidFormatException.from(p,
                "Unexpected value '" + text + "' for enum field '" + p.currentName() + "'. Use any of the following values: [%s]",
                text, String.class);
            }
            yield new %s(text);
          }"""
        .formatted(enumListStr, wrapperName);
    } else {
      // String-typed (including date-time filter properties)
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
%s
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class %s extends JsonDeserializer<%s> {
%s
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
        enumSchemaName != null ? "import java.util.Set;\n" : "",
        deserializerName, sealedName,
        enumSchemaName != null ? "\n  private static final Set<String> VALID_VALUES = Set.of(" + enumSetStr + ");\n" : "",
        sealedName,
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
    // separately by SEARCH_REQUEST_DTO_MAP and always strip "Result".
    if (schemaName.endsWith("Result") && !RETAINED_RESULT_SCHEMAS.contains(schemaName)) {
      return "Generated" + schemaName.substring(0, schemaName.length() - 6) + "StrictContract";
    }
    return "Generated" + schemaName + "StrictContract";
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
      final var fieldConstraints = resolveConstraints(node, contextFile, allSchemas);
      final var fieldDefault = resolveDefaultValue(node, contextFile, allSchemas);
      final var fieldDescription = resolveDescription(node, contextFile, allSchemas);
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
              typeInfo,
              fieldConstraints,
              useNestedEnum ? node.enumValues() : List.of(),
              fieldDefault,
              fieldDescription));
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
    final boolean hasSchema = fields.stream().anyMatch(f -> f.description() != null || f.defaultValue() != null);
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
        + (hasSchema ? "import io.swagger.v3.oas.annotations.media.Schema;\n" : "")
        + "import jakarta.annotation.Generated;\n"
        + (hasListCoercion || hasRequiredNonNullable ? "import java.util.ArrayList;\n" : "")
        + "import org.jspecify.annotations.NullMarked;\n"
        + (hasNullable ? "import org.jspecify.annotations.Nullable;\n" : "");

    final String renderedFields =
        fields.stream()
            .map(
                f -> {
                    final var schemaParts = new ArrayList<String>();
                    if (f.description() != null) {
                      schemaParts.add("description = \"" + escapeJavaString(f.description()) + "\"");
                    }
                    if (f.defaultValue() != null) {
                      schemaParts.add("defaultValue = \"" + escapeJavaString(f.defaultValue()) + "\"");
                    }
                    final var schemaAnnotation = schemaParts.isEmpty() ? ""
                        : "@Schema(" + String.join(", ", schemaParts) + ") ";
                    final var jsonProp = "@JsonProperty(\"" + f.name() + "\") ";
                    final var fieldType = f.effectiveJavaType();
                    return "    "
                        + schemaAnnotation
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

      // Null checks for required non-nullable fields — aggregate all missing fields.
      final var requiredNonNullFields = fields.stream()
          .filter(f -> f.required() && !f.nullable())
          .toList();
      if (!requiredNonNullFields.isEmpty()) {
        checks.add("    var missingFields = new ArrayList<String>();");
        for (var f : requiredNonNullFields) {
          final var message = schemaName.contains("SortRequest") && "field".equals(f.name())
              ? "Sort field must not be null."
              : "No " + f.name() + " provided.";
          checks.add(
              "    if (" + f.identifier() + " == null) missingFields.add(\"" + message + "\");");
        }
        checks.add(
            "    if (!missingFields.isEmpty()) throw new IllegalArgumentException(String.join(\" \", missingFields));");
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

      // Apply spec-declared defaults for nullable fields that were not provided.
      for (var f : fields) {
        if (f.defaultValue() != null && f.nullable()) {
          final var literal = toJavaDefaultLiteral(f.defaultValue(), f.javaType());
          if (literal != null) {
            checks.add(
                "    if (" + f.identifier() + " == null) " + f.identifier() + " = " + literal + ";");
          }
        }
      }

      // Coerce null List/Set fields to empty collections. The old protocol POJOs initialized
      // all list fields to new ArrayList<>(), so null was never serialized. With
      // @JsonInclude(ALWAYS), a null list would serialize as JSON null instead of [].
      // Skip required+non-nullable fields (those throw on null above) and nullable fields
      // (null carries semantic meaning, e.g. "don't change" in Changeset).
      for (var f : fields) {
        if (f.required() && !f.nullable()) continue;
        if (f.nullable()) continue;
        if (f.javaType().startsWith("java.util.List")) {
          checks.add(
              "    if (" + f.identifier() + " == null) " + f.identifier()
                  + " = java.util.List.of();");
        } else if (f.javaType().startsWith("java.util.Set")) {
          checks.add(
              "    if (" + f.identifier() + " == null) " + f.identifier()
                  + " = java.util.Set.of();");
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
    // For request schemas, generate typed long accessors for LongKey fields so callers
    // can use e.g. request.processDefinitionKeyAsLong() instead of Long.parseLong().
    final String longKeyAccessors = emitConstraints ? renderLongKeyAccessors(fields) : "";
    final String builderCode = renderBuilder(schemaName, dtoClass, fields);

    final String recordBody;
    if (constructorBody.isBlank()
      && fieldReferences.isBlank()
        && coercionHelpers.isBlank()
        && longKeyAccessors.isBlank()
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
      if (!longKeyAccessors.isBlank()) {
        sections.add(longKeyAccessors);
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

  /**
   * Renders typed long accessor methods for LongKey fields on request DTOs. The compact constructor
   * guarantees the string matches {@code ^-?[0-9]+$}, so {@code Long.parseLong} is safe.
   * This encapsulates the string-to-long coercion in the DTO, mirroring how response DTOs
   * encapsulate long-to-string coercion via {@code coerceXxxKey()}.
   */
  private static String renderLongKeyAccessors(final List<ContractField> fields) {
    final var accessors =
        fields.stream()
            .filter(ContractField::longKeyCoercion)
            .map(
                field -> {
                  final var methodName = field.identifier() + "AsLong";
                  if (field.nullable()) {
                    return """
  /** Returns the {@code %s} parsed as a {@code Long}, or {@code null} if absent. */
  public @Nullable Long %s() {
    return %s == null ? null : Long.parseLong(%s);
  }
"""
                        .formatted(field.name(), methodName, field.identifier(), field.identifier());
                  } else {
                    return """
  /** Returns the {@code %s} parsed as a {@code long}. */
  public long %s() {
    return Long.parseLong(%s);
  }
"""
                        .formatted(field.name(), methodName, field.identifier());
                  }
                })
            .toList();
    return accessors.isEmpty() ? "" : String.join("\n", accessors);
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
              // Also capture allOf refs from this inline branch (e.g. enum schema refs)
              for (int ai = oi + 1; ai < oneOfBlockEnd; ai++) {
                final var aLine = lines.get(ai);
                if (isIgnorable(aLine)) continue;
                final int aInd = indent(aLine);
                if (aInd <= indent(oLine)) break;
                final var aTrimmed = trimmed(aLine);
                if (aTrimmed.startsWith("allOf:")) {
                  final int allOfEnd = findNestedBlockEnd(lines, ai + 1, oneOfBlockEnd, aInd + 2);
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
          final var raw = value.contains(" #") ? value.substring(0, value.indexOf(" #")).trim() : value;
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

    return new Node(type, format, nullable, ref, items, required, enumValues, allOfRefs, oneOfRefs, oneOfInlineType, oneOfInlineFormat, oneOfInlineAllOfRefs, properties, uniqueItems, additionalProperties, minimum, maximum, minLength, maxLength, pattern, minItems, maxItems, defaultValue, description);
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

    if (!node.oneOfRefs().isEmpty()
        && node.oneOfRefs().stream()
            .allMatch(ref -> isLongKeyRef(ref, currentFile, allSchemas, resolvingStack))) {
      return true;
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

      if (!schema.node().oneOfRefs().isEmpty()
          && schema.node().oneOfRefs().stream()
              .allMatch(
                  oneOfRef ->
                      isLongKeyRef(oneOfRef, key.fileName(), allSchemas, resolvingStack))) {
        return true;
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

  /** Escapes a string for use inside a Java string literal (double-quoted). */
  private static String escapeJavaString(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /**
   * Converts a raw spec default value to a Java literal string suitable for code emission.
   * Returns null if the default cannot be represented for the given Java type.
   */
  private static String toJavaDefaultLiteral(String defaultValue, String javaType) {
    return switch (javaType) {
      case "Boolean" -> "true".equals(defaultValue) || "false".equals(defaultValue) ? defaultValue
          : null;
      case "Integer" -> {
        try {
          Integer.parseInt(defaultValue);
          yield defaultValue;
        } catch (NumberFormatException e) {
          yield null;
        }
      }
      case "Long" -> {
        try {
          Long.parseLong(defaultValue);
          yield defaultValue + "L";
        } catch (NumberFormatException e) {
          yield null;
        }
      }
      case "String" -> "\"" + defaultValue.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
      default -> {
        if (javaType.startsWith("java.util.List") && "[]".equals(defaultValue)) {
          yield "java.util.List.of()";
        }
        yield null;
      }
    };
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

  /**
   * Resolves the default value for a property node, following $ref and single-element allOf chains.
   * Returns the raw default string from the spec, or null if no default is declared.
   */
  private static String resolveDefaultValue(
      Node node, String currentFile, Map<SchemaKey, SchemaDef> allSchemas) {
    if (node.defaultValue() != null) {
      return node.defaultValue();
    }
    if (node.ref() != null) {
      final var key = toSchemaKey(node.ref(), currentFile);
      final var target = allSchemas.get(key);
      if (target != null) {
        return resolveDefaultValue(target.node(), target.fileName(), allSchemas);
      }
    }
    if (!node.allOfRefs().isEmpty() && node.allOfRefs().size() == 1) {
      final var key = toSchemaKey(node.allOfRefs().getFirst(), currentFile);
      final var target = allSchemas.get(key);
      if (target != null) {
        return resolveDefaultValue(target.node(), target.fileName(), allSchemas);
      }
    }
    return null;
  }

  /**
   * Resolves the description for a property node, following $ref and single-element allOf chains.
   * Returns the description string from the spec, or null if no description is declared.
   */
  private static String resolveDescription(
      Node node, String currentFile, Map<SchemaKey, SchemaDef> allSchemas) {
    if (node.description() != null) {
      return node.description();
    }
    if (node.ref() != null) {
      final var key = toSchemaKey(node.ref(), currentFile);
      final var target = allSchemas.get(key);
      if (target != null) {
        return resolveDescription(target.node(), target.fileName(), allSchemas);
      }
    }
    if (!node.allOfRefs().isEmpty() && node.allOfRefs().size() == 1) {
      final var key = toSchemaKey(node.allOfRefs().getFirst(), currentFile);
      final var target = allSchemas.get(key);
      if (target != null) {
        return resolveDescription(target.node(), target.fileName(), allSchemas);
      }
    }
    return null;
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
      TypeInfo typeInfo,
      Constraints constraints,
      List<String> inlineEnumValues,
      String defaultValue,
      String description) {

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

  private record PolymorphicBranch(String dtoClass, List<String> uniqueFields) {}

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

  private static String renderOffsetPaginationDto() {
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
public record GeneratedOffsetPaginationStrictContract(
    @Nullable Integer limit,
    @Nullable Integer from
) {}
""".formatted(TARGET_PACKAGE);
  }


  private static String renderSearchQueryRequestDto(
      String className, String sortContractClass, String filterContractClass,
      PaginationType paginationType) {
    var sb = new StringBuilder();

    // Build import block
    var filterImport = "";
    if (filterContractClass != null && filterContractClass.contains(".")) {
      filterImport = "\nimport " + filterContractClass + ";";
    }

    final var pageType = paginationType == PaginationType.OFFSET_ONLY
        ? "GeneratedOffsetPaginationStrictContract"
        : "GeneratedSearchQueryPageRequestStrictContract";

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
    @Nullable %s page,
""".formatted(TARGET_PACKAGE, filterImport, className, pageType));

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
  // Compound keys that extend LongKey in the spec but use non-numeric values at runtime
  // (e.g. "2251799813691760-1"). Must remain String in Java path parameters.
  private static final Set<String> COMPOUND_KEY_EXCLUSIONS =
      Set.of("DecisionEvaluationInstanceKey", "AuditLogKey");

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
      var yamlFiles = stream
          .filter(p -> p.getFileName().toString().endsWith(".yaml"))
          .sorted(Comparator.comparing(p -> p.getFileName().toString()))
          .toList();
      for (var file : yamlFiles) {
        ops.addAll(parseOperationsFromFile(file, longKeySchemas));
      }
    }
    return ops;
  }

  private static List<SpecOperation> parseOperationsFromFile(Path file, Set<String> longKeySchemas) throws IOException {
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
          bodyRequired = false; requiresSecondaryStorage = false; codeGenerationSkip = false;
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
        bodyRequired = false; requiresSecondaryStorage = false; codeGenerationSkip = false;
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
          bodyRequired = false; requiresSecondaryStorage = false; codeGenerationSkip = false;
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
              else if (nextTr.startsWith("$ref:")) paramSchemaRef = unquote(nextTr.substring("$ref:".length()).trim());
            }
            if ("path".equals(paramIn)) {
              // Determine path param type from schema $ref: keys.yaml refs are Long keys
              String pathType = "String";
              if (paramSchemaRef != null && paramSchemaRef.contains("keys.yaml")) {
                // Extract schema name from keys.yaml ref, check if it derives from LongKey
                String schemaName = paramSchemaRef.substring(paramSchemaRef.lastIndexOf('/') + 1);
                if (longKeySchemas.contains(schemaName)) {
                  pathType = "Long";
                }
              } else if (paramSchemaRef != null && paramSchemaRef.startsWith("#/components/schemas/")) {
                // Resolve local ref: check if the local schema derives from LongKey
                String localSchemaName = paramSchemaRef.substring("#/components/schemas/".length());
                if (longKeySchemas.contains(localSchemaName)) {
                  pathType = "Long";
                } else {
                  // Check if local schema references keys.yaml LongKey descendants
                  for (int k = 0; k < lines.size(); k++) {
                    String kt = trimmed(lines.get(k));
                    if (kt.equals(localSchemaName + ":") && indent(lines.get(k)) == 4) {
                      for (int m = k + 1; m < lines.size() && m < k + 20; m++) {
                        if (indent(lines.get(m)) <= 4 && m > k + 1) break;
                        if (trimmed(lines.get(m)).contains("keys.yaml")) { pathType = "Long"; break; }
                      }
                      break;
                    }
                  }
                }
              }
              pathParams.add(new SpecQueryParam(paramName, pathType, true));
            }
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
   * Special handling for search query schemas: The spec has both a request schema
   * (with filter/sort/page) and a result schema (with page/items) for each search
   * endpoint. dtoClassName strips "Result", which could cause both to map to the
   * same class name. SEARCH_REQUEST_DTO_MAP routes request schemas to the correct
   * Phase 3.5 request DTO.
   */
  private static String resolveSchemaType(String schemaName) {
    if (schemaName == null) return null;
    // Check Phase 3.5 search query request DTO map first.
    final var searchRequestDto = SEARCH_REQUEST_DTO_MAP.get(schemaName);
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

    // Resolve authentication context once per request.
    // Unprotected endpoints (e.g. /v2/license, /v2/status) have no Spring Authentication
    // in the security context — getCamundaAuthentication() throws when no converter handles
    // null. Fall back to anonymous authentication for these endpoints.
    sb.append("    final var authentication =\n");
    sb.append("        authenticationProvider.getAnonymousIfUnavailable();\n");

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

  // ──────────────────────────────────────────────────────────────────────────
  // Phase 3.7 — Filter mapper generation
  // ──────────────────────────────────────────────────────────────────────────

  /** Known entity name → builder factory method overrides (when naming conventions differ). */
  private static final Map<String, String> ENTITY_BUILDER_OVERRIDES = Map.of(
      "ElementInstance", "flowNodeInstance",
      "GlobalTaskListener", "globalListener"
  );

  /** Filter schema name → domain filter class name overrides. */
  private static final Map<String, String> DOMAIN_FILTER_OVERRIDES = Map.of(
      "ElementInstanceFilter", "FlowNodeInstanceFilter",
      "GlobalTaskListenerFilter", "GlobalListenerFilter",
      "ClusterVariableSearchQueryFilterRequest", "ClusterVariableFilter"
  );

  /**
   * Per-entity field overrides for filter mapper generation. Each override specifies a builder
   * base name (for name mismatches) and/or an operation type override (when the spec type
   * differs from the domain builder's expected type).
   *
   * @param builderBase builder method base name (null = use field identifier)
   * @param opType operation type override (null = use detected type from schema)
   */
  private record FilterFieldOverride(String builderBase, String opType) {
    static FilterFieldOverride name(String builderBase) {
      return new FilterFieldOverride(builderBase, null);
    }
    static FilterFieldOverride type(String opType) {
      return new FilterFieldOverride(null, opType);
    }
    static FilterFieldOverride nameAndType(String builderBase, String opType) {
      return new FilterFieldOverride(builderBase, opType);
    }
  }

  private static final Map<String, Map<String, FilterFieldOverride>> FILTER_FIELD_OVERRIDES = Map.of(
      "CorrelatedMessageSubscriptionFilter", Map.of(
          "elementId", FilterFieldOverride.name("flowNodeId"),
          "elementInstanceKey", FilterFieldOverride.name("flowNodeInstanceKey"),
          "messageKey", FilterFieldOverride.type("Long")
      ),
      "MessageSubscriptionFilter", Map.of(
          "elementId", FilterFieldOverride.name("flowNodeId"),
          "elementInstanceKey", FilterFieldOverride.name("flowNodeInstanceKey"),
          "lastUpdatedDate", FilterFieldOverride.name("dateTime")
      ),
      "BatchOperationItemFilter", Map.of(
          "itemKey", FilterFieldOverride.type("Long")
      )
  );

  /**
   * Filter schema names for which mapper generation is skipped. These entities need hand-written
   * mapper/validator files because:
   * <ul>
   *   <li>The domain builder method names don't match the schema field names (naming mismatch)</li>
   *   <li>The domain builder uses old-style list methods instead of Operations methods</li>
   *   <li>No corresponding FilterBuilders factory method exists (statistics/specialized)</li>
   *   <li>Custom validation logic is required ($Or, variables, tags, enum converters)</li>
   * </ul>
   */
  private static final Set<String> FILTER_MAPPER_SKIP = Set.of(
      // No FilterBuilders factory method / specialized domain mapping
      "UserTaskVariableFilter",
      "UserTaskAuditLogFilter",
      "GlobalTaskListenerSearchQueryFilterRequest",
      "IncidentProcessInstanceStatisticsByDefinitionFilter",
      "ProcessDefinitionInstanceVersionStatisticsFilter",
      // Old-style builders (list methods, no Operations) + naming mismatches
      "DecisionDefinitionFilter",
      "DecisionInstanceFilter",
      "DecisionRequirementsFilter",
      "ProcessDefinitionFilter",
      // Builder name mismatches + custom validation needed
      "AuthorizationFilter",
      "ProcessInstanceFilter",
      "UserTaskFilter",
      "IncidentFilter",
      "VariableFilter",
      "ElementInstanceFilter",
      "AuditLogFilter",
      // Enum getValue pattern (not Operations)
      "BatchOperationFilter"
  );

  private static SchemaDef findSchemaByName(
      Map<SchemaKey, SchemaDef> allSchemas, String schemaName) {
    for (var entry : allSchemas.entrySet()) {
      if (entry.getKey().schemaName().equals(schemaName)) {
        return entry.getValue();
      }
    }
    return null;
  }

  /**
   * Determines the Java operation type for a filter property field. This is the type T in
   * {@code mapToOperations(T.class)} and the type parameter of {@code List<Operation<T>>}
   * in the domain filter builder method.
   */
  private static String determineFilterOperationType(
      ContractField field, Map<SchemaKey, SchemaDef> allSchemas) {
    final var protocolType = field.typeInfo() != null ? field.typeInfo().protocolJavaType() : null;
    if (protocolType == null) return "String";

    final var schemaName = protocolType.substring(protocolType.lastIndexOf('.') + 1);

    // LongKey filter properties (e.g., ProcessInstanceKeyFilterProperty) → Long
    if (isLongKeyFilterProperty(schemaName)) return "Long";

    // Look up the filter property schema for format detection
    final var schema = findSchemaByName(allSchemas, schemaName);
    if (schema != null && "date-time".equals(schema.node().oneOfInlineFormat())) {
      return "java.time.OffsetDateTime";
    }

    // Use the primitive type for everything else
    if (schema != null) {
      return switch (filterPropertyPrimitiveJavaType(schema.node())) {
        case "Integer" -> "Integer";
        case "Long" -> "Long";
        case "Double" -> "Double";
        default -> "String";
      };
    }
    return "String";
  }

  /**
   * Renders a filter mapper class for a search query entity. The mapper converts a generated
   * strict-contract filter DTO to the corresponding domain filter object using FilterBuilders.
   *
   * <p>Simple scalar and filter property fields are mapped mechanically. Complex fields (nested
   * object lists, collections of structured types) are skipped with a TODO comment — these are
   * handled by hand-written validators for entities that need custom mapping logic.
   */
  private static String renderFilterMapper(
      String mapperClassName,
      SearchQuerySchemaEntry sqe,
      SchemaDef filterSchema,
      String filterDtoClass,
      List<ContractField> filterFields,
      Map<SchemaKey, SchemaDef> allSchemas) {

    // Domain filter type (e.g., "JobFilter", "FlowNodeInstanceFilter")
    final var domainFilterName =
        DOMAIN_FILTER_OVERRIDES.getOrDefault(sqe.filterSchemaName(), sqe.filterSchemaName());
    final var domainFilterFqn = "io.camunda.search.filter." + domainFilterName;

    // Builder factory method (e.g., "job", "processInstance", "flowNodeInstance")
    final var builderFactoryMethod =
        ENTITY_BUILDER_OVERRIDES.getOrDefault(sqe.entityName(), lowerFirst(sqe.entityName()));

    // Method name (e.g., "toJobFilter")
    final var methodName = "to" + domainFilterName;

    // Build field mapping lines
    final var fieldMappings = new ArrayList<String>();
    boolean needsOffsetDateTime = false;

    // Look up per-entity field overrides (builder name and/or type overrides)
    final var fieldOverrides = FILTER_FIELD_OVERRIDES
        .getOrDefault(sqe.filterSchemaName(), Map.of());

    for (var f : filterFields) {
      final var id = f.identifier();
      final var typeInfo = f.typeInfo();
      // Resolve builder base name and type override from per-field overrides
      final var override = fieldOverrides.get(id);
      final var builderBase = (override != null && override.builderBase() != null)
          ? override.builderBase() : id;

      if (typeInfo != null && typeInfo.selfDeserializing()) {
        // Filter property field → mapToOperations(Type.class) → builder::fieldOperations
        final var detectedType = determineFilterOperationType(f, allSchemas);
        final var opType = (override != null && override.opType() != null)
            ? override.opType() : detectedType;
        if (opType.contains("OffsetDateTime")) needsOffsetDateTime = true;
        fieldMappings.add(
            "    ofNullable(filter." + id + "())"
                + ".map(mapToOperations(" + simpleTypeName(opType) + ".class))"
                + ".ifPresent(builder::" + builderBase + "Operations);");
      } else if ("Boolean".equals(f.javaType())) {
        // Boolean field → direct
        fieldMappings.add(
            "    ofNullable(filter." + id + "()).ifPresent(builder::" + builderBase + ");");
      } else if (typeInfo != null
          && (typeInfo.strictObjectType() || typeInfo.strictListType())) {
        // Complex nested type (e.g., $or, variables) — requires hand-written validator
        fieldMappings.add(
            "    // TODO: " + id + " — complex type, requires validator");
      } else if (f.javaType().startsWith("java.util.List")
          || f.javaType().startsWith("java.util.Set")) {
        // Collection type — requires hand-written validator
        fieldMappings.add(
            "    // TODO: " + id + " — collection type, requires validator");
      } else {
        // Simple scalar (String, Integer, etc.) — direct value
        fieldMappings.add(
            "    ofNullable(filter." + id + "()).ifPresent(builder::" + builderBase + ");");
      }
    }

    // Render the class
    final var sb = new StringBuilder();
    sb.append("/*\n * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under\n");
    sb.append(" * one or more contributor license agreements. See the NOTICE file distributed\n");
    sb.append(" * with this work for additional information regarding copyright ownership.\n");
    sb.append(" * Licensed under the Camunda License 1.0. You may not use this file\n");
    sb.append(" * except in compliance with the Camunda License 1.0.\n */\n");
    sb.append("package ").append(TARGET_PACKAGE).append(";\n\n");

    // Imports
    sb.append("import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;\n");
    sb.append("import static java.util.Optional.ofNullable;\n\n");
    sb.append("import ").append(domainFilterFqn).append(";\n");
    sb.append("import io.camunda.search.filter.FilterBuilders;\n");
    sb.append("import jakarta.annotation.Generated;\n");
    if (needsOffsetDateTime) {
      sb.append("import java.time.OffsetDateTime;\n");
    }
    sb.append("import org.jspecify.annotations.NullMarked;\n");
    sb.append("import org.jspecify.annotations.Nullable;\n\n");

    // Class
    sb.append("@Generated(value = \"GenerateContractMappingPoc\")\n");
    sb.append("@NullMarked\n");
    sb.append("public final class ").append(mapperClassName).append(" {\n\n");
    sb.append("  private ").append(mapperClassName).append("() {}\n\n");

    // Mapper method
    sb.append("  public static ").append(domainFilterName).append(" ").append(methodName).append("(\n");
    sb.append("      @Nullable final ").append(filterDtoClass).append(" filter) {\n");
    sb.append("    if (filter == null) {\n");
    sb.append("      return FilterBuilders.").append(builderFactoryMethod).append("().build();\n");
    sb.append("    }\n");
    sb.append("    final var builder = FilterBuilders.").append(builderFactoryMethod).append("();\n");
    for (var mapping : fieldMappings) {
      sb.append(mapping).append("\n");
    }
    sb.append("    return builder.build();\n");
    sb.append("  }\n");
    sb.append("}\n");

    return sb.toString();
  }

  /** Returns the simple (unqualified) class name from a potentially qualified type name. */
  private static String simpleTypeName(String type) {
    final int lastDot = type.lastIndexOf('.');
    return lastDot >= 0 ? type.substring(lastDot + 1) : type;
  }
}
