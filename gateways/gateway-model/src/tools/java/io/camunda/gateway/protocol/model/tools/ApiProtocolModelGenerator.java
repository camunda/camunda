/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.protocol.model.tools;

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
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaInterfaceSource;
import org.jboss.forge.roaster.model.source.MethodSource;

/**
 * Java source-file generator for contract mapping POC.
 *
 * <p>Run via: ApiProtocolModelGenerator <repo-root>
 */
public class ApiProtocolModelGenerator {

  private static final String TARGET_PACKAGE = "io.camunda.gateway.protocol.model";
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
    final var repoRoot = Path.of(args[0]);
    final var specDir = repoRoot.resolve("zeebe/gateway-protocol/src/main/proto/v2").normalize();
    final var outBase = repoRoot.resolve("gateways/gateway-model/target/generated-sources");

    if (!Files.isDirectory(specDir)) {
      throw new IllegalStateException("OpenAPI spec directory does not exist: " + specDir);
    }

    final var licenseHeaderFile = Path.of(args[1]);
    final var licenseText = Files.readString(licenseHeaderFile, StandardCharsets.UTF_8);
    final var licenseComment = formatLicenseComment(licenseText);

    final var allSchemas = loadSchemas(specDir);
    final var responseOnlySchemas = discoverResponseOnlySchemas(specDir, allSchemas);

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
            .filter(ApiProtocolModelGenerator::isContractSchema)
            .sorted(Comparator.comparing(SchemaDef::schemaName))
            .toList();

    if (contractSchemas.isEmpty()) {
      throw new IllegalStateException("No contract schemas found in " + specDir);
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
    // JobResult → GeneratedJobContract collides with Job → GeneratedJobContract).
    final var contractDtoNames = contractSchemas.stream()
        .map(s -> dtoClassName(s.schemaName()))
        .collect(Collectors.toSet());
    final var polymorphicSchemas = new LinkedHashMap<String, List<String>>();
    for (var schema : allSchemas.values()) {
      if (isPolymorphicSchema(schema)
          && !"SearchQueryPageRequest".equals(schema.schemaName())
          && !contractDtoNames.contains(dtoClassName(schema.schemaName()))) {
        final var branchNames = schema.node().oneOfRefs().stream()
            .map(ApiProtocolModelGenerator::refToSchemaName)
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

    final var packagePath = outBase.resolve(TARGET_PACKAGE.replace('.', '/'));
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
            renderStrictEnum(licenseComment, schema.fileName(), schema.schemaName(), schema.node().enumValues()),
            StandardCharsets.UTF_8);
        System.out.println("generated enum: " + repoRoot.relativize(enumFile));
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
        final var wrapperName = sealedName + "PlainValue";
        final var deserializerName = sealedName + "Deserializer";
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
                licenseComment, parentSchema.fileName(), parentSchemaName, sealedName, wrapperName,
                primitiveJavaType),
            StandardCharsets.UTF_8);
        System.out.println("generated filter wrapper: " + repoRoot.relativize(wrapperFile));

        // (b) Generate custom deserializer
        final var deserializerFile = packagePath.resolve(deserializerName + ".java");
        Files.writeString(
            deserializerFile,
            renderFilterPropertyDeserializer(
                licenseComment, parentSchema.fileName(), parentSchemaName, sealedName, wrapperName,
                advancedFilterDtoClass, deserializerName, primitiveJavaType, isLongKey,
                enumSchemaName, allSchemas),
            StandardCharsets.UTF_8);
        System.out.println("generated filter deserializer: " + repoRoot.relativize(deserializerFile));

        // (c) Generate sealed interface with @JsonDeserialize and both permitted types
        final var sealedFile = packagePath.resolve(sealedName + ".java");
        Files.writeString(
            sealedFile,
            renderFilterPropertySealedInterface(
                licenseComment, parentSchema.fileName(), parentSchemaName, sealedName,
                advancedFilterDtoClass, wrapperName, deserializerName),
            StandardCharsets.UTF_8);
        System.out.println("generated filter sealed interface: " + repoRoot.relativize(sealedFile));
      } else {
        // Regular polymorphic schema: generate standard sealed interface.
        final var branchDtoClasses = branchNames.stream()
            .map(ApiProtocolModelGenerator::dtoClassName)
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
          final var deserializerName = sealedName + "Deserializer";
          final var deserializerFile = packagePath.resolve(deserializerName + ".java");
          Files.writeString(
              deserializerFile,
              renderPolymorphicDeserializer(
                  licenseComment, parentSchema.fileName(), parentSchemaName, sealedName,
                  deserializerName, branchDiscriminators),
              StandardCharsets.UTF_8);
          System.out.println("generated polymorphic deserializer: " + repoRoot.relativize(deserializerFile));

          final var sealedFile = packagePath.resolve(sealedName + ".java");
          Files.writeString(
              sealedFile,
              renderPolymorphicSealedInterfaceWithDeserializer(
                  licenseComment, parentSchema.fileName(), parentSchemaName, branchDtoClasses, deserializerName),
              StandardCharsets.UTF_8);
          System.out.println("generated sealed interface: " + repoRoot.relativize(sealedFile));
        } else {
          // Fall back to Jackson DEDUCTION (no unique discriminating fields available).
          final var sealedFile = packagePath.resolve(sealedName + ".java");
          Files.writeString(
              sealedFile,
              renderPolymorphicSealedInterface(
                  licenseComment, parentSchema.fileName(), parentSchemaName, branchDtoClasses),
              StandardCharsets.UTF_8);
          System.out.println("generated sealed interface: " + repoRoot.relativize(sealedFile));
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
              licenseComment,
              plan.schema().fileName(),
              plan.schema().schemaName(),
              plan.dtoClass(),
              plan.fields(),
              sealedParent,
              isRequestSchema),
          StandardCharsets.UTF_8);
      System.out.println("generated: " + repoRoot.relativize(dtoFile));
      dtoCount++;
    }
    System.out.println(dtoCount + " strict contract DTOs generated.");

    // Phase 3.5: Generate flat page request DTO and search query request DTOs.
    // Discover search query schemas directly from the spec (schemas extending SearchQueryRequest).
    final var searchQuerySchemas = discoverSearchQuerySchemas(allSchemas);
    System.out.println(searchQuerySchemas.size() + " search query request schemas discovered from spec.");

    final var flatPageFile = packagePath.resolve("SearchQueryPageRequest.java");
    Files.writeString(flatPageFile, renderFlatPageRequestDto(licenseComment), StandardCharsets.UTF_8);
    System.out.println("generated: " + repoRoot.relativize(flatPageFile));

    final var offsetPageFile = packagePath.resolve("OffsetPagination.java");
    Files.writeString(offsetPageFile, renderOffsetPaginationDto(licenseComment), StandardCharsets.UTF_8);
    System.out.println("generated: " + repoRoot.relativize(offsetPageFile));

    int searchRequestDtoCount = 0;
    for (var sqe : searchQuerySchemas) {
      final var requestDtoName = sqe.requestDtoName();
      final var sortContractClass = sqe.sortSchemaName() != null
          ? dtoClassName(sqe.sortSchemaName()) : null;
      final var filterContractFqn = sqe.filterSchemaName() != null
          ? TARGET_PACKAGE + "." + dtoClassName(sqe.filterSchemaName()) : null;
      final var requestDtoFile = packagePath.resolve(requestDtoName + ".java");
      Files.writeString(requestDtoFile, renderSearchQueryRequestDto(
          licenseComment, requestDtoName, sortContractClass, filterContractFqn, sqe.paginationType()), StandardCharsets.UTF_8);
      System.out.println("generated: " + repoRoot.relativize(requestDtoFile));

      // Populate the override map so resolveSchemaType() routes this schema to the request DTO.
      SEARCH_REQUEST_DTO_MAP.put(sqe.schemaName(), requestDtoName);
      searchRequestDtoCount++;
    }
    System.out.println(searchRequestDtoCount + " search query request DTOs generated.");

    // Phase 3.7: Filter mapper generation is disabled in gateway-model.
    // Filter mappers depend on gateway-mapping-http internals (AdvancedSearchFilterUtil,
    // domain filter builders) and belong in the mapping layer, not the model layer.
    // They are generated by a separate generator or hand-written in gateway-mapping-http.

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
      return schemaName;
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

  // Compound keys that extend LongKey in the spec but use non-numeric values at runtime
  // (e.g. "2251799813691760-1"). Must remain String in Java path parameters.
  private static final Set<String> COMPOUND_KEY_EXCLUSIONS =
      Set.of("DecisionEvaluationInstanceKey", "AuditLogKey");

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

  /**
   * Adds fluent setter, getter (@Schema + @JsonProperty), and setter (@JsonProperty)
   * for a nullable field on a Roaster JavaClassSource.
   */
  private static void addNullableFluentSetterGetterSetter(
      JavaClassSource javaClass, String className, String fieldName, String fieldType) {
    // Fluent setter
    var fluent = javaClass.addMethod()
        .setName(fieldName)
        .setReturnType(className)
        .setPublic()
        .setBody("this." + fieldName + " = " + fieldName + ";\nreturn this;");
    fluent.addParameter(fieldType, fieldName).addAnnotation("org.jspecify.annotations.Nullable");

    // Getter (returns Optional<T> for nullable fields)
    var getter = javaClass.addMethod()
        .setName("get" + capitalizeIdentifier(fieldName))
        .setReturnType("Optional<" + fieldType + ">")
        .setPublic()
        .setBody("return Optional.ofNullable(" + fieldName + ");");
    getter.addAnnotation("io.swagger.v3.oas.annotations.media.Schema")
        .setStringValue("name", fieldName);
    getter.addAnnotation("com.fasterxml.jackson.annotation.JsonProperty")
        .setStringValue(fieldName);

    // Setter
    var setter = javaClass.addMethod()
        .setName("set" + capitalizeIdentifier(fieldName))
        .setReturnTypeVoid()
        .setPublic()
        .setBody("this." + fieldName + " = " + fieldName + ";");
    setter.addParameter(fieldType, fieldName).addAnnotation("org.jspecify.annotations.Nullable");
    setter.addAnnotation("com.fasterxml.jackson.annotation.JsonProperty")
        .setStringValue(fieldName);
  }

  /**
   * No-op now that nullable getters return {@code Optional<T>} directly.
   * Kept for source compatibility with callers; simply returns the source unchanged.
   */
  private static String patchNullableGetterReturnTypes(
      String source, List<String> fieldNames, List<String> fieldTypes) {
    return source;
  }

  /** Formats the license text as a Java block comment. */
  private static String formatLicenseComment(String licenseText) {
    final var lines = licenseText.strip().split("\n");
    final var sb = new StringBuilder("/*\n");
    for (var line : lines) {
      sb.append(" * ").append(line).append("\n");
    }
    sb.append(" */");
    return sb.toString();
  }

  /**
   * Formats the license comment with a source location appended.
   */
  private static String licenseWithSource(String licenseComment, String sourceFile, String schemaName) {
    // Insert source line before the closing " */"
    return licenseComment.substring(0, licenseComment.length() - 3)
        + " * Source: zeebe/gateway-protocol/src/main/proto/v2/" + sourceFile
        + "#/components/schemas/" + schemaName + "\n */";
  }

  /** Generates a sealed interface for a polymorphic oneOf schema. */
  private static String renderPolymorphicSealedInterface(
      String licenseComment, String sourceFile, String schemaName, List<String> branchDtoClasses) {
    final var sealedName = dtoClassName(schemaName);
    JavaInterfaceSource iface = Roaster.create(JavaInterfaceSource.class);
    iface.setPackage(TARGET_PACKAGE);
    iface.setName(sealedName);
    iface.addImport("com.fasterxml.jackson.annotation.JsonSubTypes");
    iface.addImport("com.fasterxml.jackson.annotation.JsonTypeInfo");
    iface.addAnnotation("jakarta.annotation.Generated")
        .setStringValue("value", "io.camunda.gateway.protocol.model.tools.ApiProtocolModelGenerator");

    // Roaster doesn't handle nested @JsonSubTypes.Type annotations or sealed interfaces.
    // Build the @JsonTypeInfo + @JsonSubTypes + sealed permits via string patching.
    final var subtypes = branchDtoClasses.stream()
        .map(cls -> "    @JsonSubTypes.Type(" + cls + ".class)")
        .collect(Collectors.joining(",\n"));
    final var permits = String.join(",\n        ", branchDtoClasses);
    var source = iface.toString();
    source = source.replace(
        "@Generated",
        "@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)\n@JsonSubTypes({\n" + subtypes + "\n})\n@Generated");
    source = source.replace(
        "public interface " + sealedName,
        "public sealed interface " + sealedName + " permits\n        " + permits);
    return licenseWithSource(licenseComment, sourceFile, schemaName) + "\n" + source;
  }

  /**
   * Generates a sealed interface with @JsonDeserialize pointing to a custom deserializer,
   * replacing the @JsonTypeInfo(DEDUCTION) approach for polymorphic oneOf schemas where
   * we can provide better error messages.
   */
  private static String renderPolymorphicSealedInterfaceWithDeserializer(
      String licenseComment, String sourceFile, String schemaName, List<String> branchDtoClasses,
      String deserializerName) {
    final var sealedName = dtoClassName(schemaName);
    JavaInterfaceSource iface = Roaster.create(JavaInterfaceSource.class);
    iface.setPackage(TARGET_PACKAGE);
    iface.setName(sealedName);
    iface.addAnnotation("com.fasterxml.jackson.databind.annotation.JsonDeserialize")
        .setLiteralValue("using", deserializerName + ".class");
    iface.addAnnotation("jakarta.annotation.Generated")
        .setStringValue("value", "io.camunda.gateway.protocol.model.tools.ApiProtocolModelGenerator");

    // Roaster doesn't support sealed interfaces natively; patch the output.
    final var permits = String.join(",\n        ", branchDtoClasses);
    var source = iface.toString();
    source = source.replace(
        "public interface " + sealedName,
        "public sealed interface " + sealedName + " permits\n        " + permits);
    return licenseWithSource(licenseComment, sourceFile, schemaName) + "\n" + source;
  }

  /**
   * Generates a custom Jackson deserializer for a polymorphic oneOf sealed interface.
   * Uses tree-model parsing to detect which branch's unique fields are present,
   * and produces a helpful error message when no branch matches.
   */
  private static String renderPolymorphicDeserializer(
      String licenseComment, String sourceFile, String schemaName, String sealedName,
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

    JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
    javaClass.setPackage(TARGET_PACKAGE);
    javaClass.setName(deserializerName);
    javaClass.setFinal(true);
    javaClass.setSuperType("JsonDeserializer<" + sealedName + ">");
    javaClass.addImport("com.fasterxml.jackson.core.JsonParser");
    javaClass.addImport("com.fasterxml.jackson.databind.DeserializationContext");
    javaClass.addImport("com.fasterxml.jackson.databind.JsonDeserializer");
    javaClass.addImport("com.fasterxml.jackson.databind.JsonNode");
    javaClass.addImport("com.fasterxml.jackson.databind.exc.ValueInstantiationException");
    javaClass.addImport("java.io.IOException");
    javaClass.addAnnotation("jakarta.annotation.Generated")
        .setStringValue("value", "io.camunda.gateway.protocol.model.tools.ApiProtocolModelGenerator");

    final var method = javaClass.addMethod()
        .setName("deserialize")
        .setReturnType(sealedName)
        .setPublic()
        .setBody(
            "final JsonNode node = p.readValueAsTree();\n"
            + branchCases
            + "throw ValueInstantiationException.from(p,\n"
            + "    \"At least one of [" + fieldList + "] is required\",\n"
            + "    ctxt.constructType(" + sealedName + ".class),\n"
            + "    new IllegalArgumentException(\"At least one of [" + fieldList + "] is required\"));");
    method.addAnnotation(Override.class);
    method.addParameter("JsonParser", "p").setFinal(true);
    method.addParameter("DeserializationContext", "ctxt").setFinal(true);
    method.addThrows("IOException");

    return licenseWithSource(licenseComment, sourceFile, schemaName) + "\n" + javaClass.toString();
  }

  /** Generates a sealed interface for a filter property oneOf with @JsonDeserialize. */
  private static String renderFilterPropertySealedInterface(
      String licenseComment, String sourceFile, String schemaName, String sealedName,
      String advancedFilterDtoClass, String wrapperName, String deserializerName) {
    JavaInterfaceSource iface = Roaster.create(JavaInterfaceSource.class);
    iface.setPackage(TARGET_PACKAGE);
    iface.setName(sealedName);
    iface.addAnnotation("com.fasterxml.jackson.databind.annotation.JsonDeserialize")
        .setLiteralValue("using", deserializerName + ".class");
    iface.addAnnotation("jakarta.annotation.Generated")
        .setStringValue("value", "io.camunda.gateway.protocol.model.tools.ApiProtocolModelGenerator");

    // Roaster doesn't support sealed interfaces natively; patch the output.
    var source = iface.toString();
    source = source.replace(
        "public interface " + sealedName,
        "public sealed interface " + sealedName + " permits\n        "
            + advancedFilterDtoClass + ",\n        " + wrapperName);
    return licenseWithSource(licenseComment, sourceFile, schemaName) + "\n" + source;
  }

  /** Generates a plain-value wrapper record for the inline primitive branch of a filter property. */
  private static String renderFilterPlainValueWrapper(
      String licenseComment, String sourceFile, String schemaName, String sealedName,
      String wrapperName, String primitiveJavaType) {
    return """
%s
package %s;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;

/**
 * Wrapper for the plain-value branch of the %s oneOf.
 * Represents a direct value match (implicit $eq).
 */
@NullMarked
@JsonDeserialize(using = JsonDeserializer.None.class)
@Generated(value = "io.camunda.gateway.protocol.model.tools.ApiProtocolModelGenerator")
public record %s(%s value) implements %s {
}
"""
    .formatted(licenseWithSource(licenseComment, sourceFile, schemaName),
        TARGET_PACKAGE, schemaName, wrapperName,
        primitiveJavaType, sealedName);
  }

  /** Generates a custom Jackson deserializer for a filter property sealed interface. */
  private static String renderFilterPropertyDeserializer(
      String licenseComment, String sourceFile, String schemaName, String sealedName, String wrapperName,
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

    JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
    javaClass.setPackage(TARGET_PACKAGE);
    javaClass.setName(deserializerName);
    javaClass.setFinal(true);
    javaClass.setSuperType("JsonDeserializer<" + sealedName + ">");
    javaClass.addImport("com.fasterxml.jackson.core.JsonParser");
    javaClass.addImport("com.fasterxml.jackson.databind.DeserializationContext");
    javaClass.addImport("com.fasterxml.jackson.databind.JsonDeserializer");
    javaClass.addImport("com.fasterxml.jackson.databind.exc.InvalidFormatException");
    javaClass.addImport("java.io.IOException");
    if (enumSchemaName != null) {
      javaClass.addImport("java.util.Set");
    }
    javaClass.addAnnotation("jakarta.annotation.Generated")
        .setStringValue("value", "io.camunda.gateway.protocol.model.tools.ApiProtocolModelGenerator");

    // Add VALID_VALUES constant for enum-typed filter properties.
    if (enumSchemaName != null) {
      javaClass.addField()
          .setName("VALID_VALUES")
          .setType("Set<String>")
          .setPrivate()
          .setStatic(true)
          .setFinal(true)
          .setLiteralInitializer("Set.of(" + enumSetStr + ")");
    }

    final var method = javaClass.addMethod()
        .setName("deserialize")
        .setReturnType(sealedName)
        .setPublic()
        .setBody(
            "return switch (p.currentToken()) {\n"
            + plainValueCase + "\n"
            + "  case START_OBJECT -> ctxt.readValue(p, " + advancedFilterDtoClass + ".class);\n"
            + "  default -> throw InvalidFormatException.from(p,\n"
            + "      \"Request property cannot be parsed\", p.getText(), " + primitiveJavaType + ".class);\n"
            + "};");
    method.addAnnotation(Override.class);
    method.addParameter("JsonParser", "p").setFinal(true);
    method.addParameter("DeserializationContext", "ctxt").setFinal(true);
    method.addThrows("IOException");

    return licenseWithSource(licenseComment, sourceFile, schemaName) + "\n" + javaClass.toString();
  }

  private static boolean isEnumSchema(SchemaDef schema) {
    return "string".equals(schema.node().type()) && !schema.node().enumValues().isEmpty();
  }

  private static String strictEnumClassName(String schemaName) {
    return schemaName;
  }

  private static String renderStrictEnum(
      String licenseComment, String sourceFile, String schemaName, List<String> enumValues) {
    final var className = strictEnumClassName(schemaName);
    JavaEnumSource javaEnum = Roaster.create(JavaEnumSource.class);
    javaEnum.setPackage(TARGET_PACKAGE);
    javaEnum.setName(className);
    javaEnum.addAnnotation("jakarta.annotation.Generated")
        .setStringValue("value", "io.camunda.gateway.protocol.model.tools.ApiProtocolModelGenerator");

    // Add enum constants with value parameter
    for (var v : enumValues) {
      javaEnum.addEnumConstant(v).setConstructorArguments("\"" + v + "\"");
    }

    // Private value field
    javaEnum.addField().setName("value").setType("String").setPrivate().setFinal(true);

    // Constructor
    // Roaster doesn't have a direct addConstructor with body for enums, so we add a method-like block
    // We'll use the raw body approach
    var constructor = javaEnum.addMethod()
        .setConstructor(true)
        .setBody("this.value = value;");
    constructor.addParameter("String", "value");

    // getValue with @JsonValue
    javaEnum.addMethod()
        .setName("getValue")
        .setReturnType("String")
        .setPublic()
        .setBody("return value;")
        .addAnnotation("com.fasterxml.jackson.annotation.JsonValue");

    // toString
    javaEnum.addMethod()
        .setName("toString")
        .setReturnType("String")
        .setPublic()
        .setBody("return String.valueOf(value);")
        .addAnnotation(Override.class);

    // fromValue with @JsonCreator
    var fromValue = javaEnum.addMethod()
        .setName("fromValue")
        .setReturnType(className)
        .setPublic()
        .setStatic(true)
        .setBody(
            "for (" + className + " b : " + className + ".values()) {\n"
                + "  if (b.value.equalsIgnoreCase(value)) {\n"
                + "    return b;\n"
                + "  }\n"
                + "}\n"
                + "throw new IllegalArgumentException(\"Unexpected value '\" + value + \"'\");");
    fromValue.addParameter("String", "value");
    fromValue.addAnnotation("com.fasterxml.jackson.annotation.JsonCreator");

    return licenseWithSource(licenseComment, sourceFile, schemaName) + "\n" + javaEnum.toString();
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
    // Use bare schema name — matches the OpenAPI codegen naming in protocol.model.
    return schemaName;
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
          final var javaType =
            normalizeEmittedType(typeOverride != null ? typeOverride : typeInfo.javaType());
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
      // Generate a nested enum in the DTO for fields with inline enum values.
      // This matches the OpenAPI codegen behavior (inner enums like Foo.StateEnum)
      // and ensures Jackson rejects unknown values at deserialization time.
      final var useNestedEnum = hasInlineEnum && !node.enumValues().isEmpty();
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
      String licenseComment, String sourceFile, String schemaName, String dtoClass,
      List<ContractField> fields, String sealedParent, boolean emitConstraints) {
    JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
    javaClass.setPackage(TARGET_PACKAGE);
    javaClass.setName(dtoClass);

    // Class-level annotations
    javaClass.addAnnotation("com.fasterxml.jackson.annotation.JsonInclude")
        .setLiteralValue("value", "com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL");
    javaClass.addAnnotation("org.jspecify.annotations.NullMarked");
    if (sealedParent != null) {
      javaClass.addAnnotation("com.fasterxml.jackson.databind.annotation.JsonDeserialize")
          .setLiteralValue("using", "com.fasterxml.jackson.databind.JsonDeserializer.None.class");
      javaClass.setFinal(true);
      javaClass.addInterface(sealedParent);
    }
    javaClass.addAnnotation("jakarta.annotation.Generated")
        .setStringValue("value", "io.camunda.gateway.protocol.model.tools.ApiProtocolModelGenerator");

    // Add explicit imports for collection types used in field types so Roaster resolves them.
    for (var f : fields) {
      final var ft = f.effectiveJavaType();
      if (ft.contains("java.util.List")) javaClass.addImport("java.util.List");
      if (ft.contains("java.util.Set")) javaClass.addImport("java.util.Set");
      if (ft.contains("java.util.Map")) javaClass.addImport("java.util.Map");
    }

    // Add Optional import if any field is nullable (getters return Optional<T>).
    if (fields.stream().anyMatch(ApiProtocolModelGenerator::isNullAssignableField)) {
      javaClass.addImport("java.util.Optional");
    }

    // Private fields
    for (var f : fields) {
      final var fieldType = simplifyCollectionTypeName(f.effectiveJavaType());
      FieldSource<JavaClassSource> field = javaClass.addField()
          .setName(f.identifier())
          .setType(fieldType)
          .setPrivate();
      if (isNullAssignableField(f)) {
        field.addAnnotation("org.jspecify.annotations.Nullable");
      }
    }

    // No-arg constructor
    javaClass.addMethod()
        .setConstructor(true)
        .setPublic()
        .setBody("super();");

    // Per-field: fluent setter, getter, setter
    for (var f : fields) {
      // Use short name for method signatures since imports are added above.
      final var fieldType = simplifyCollectionTypeName(f.effectiveJavaType());
      final var nullable = isNullAssignableField(f);
      final var getterName = "get" + capitalizeIdentifier(f.identifier());
      final var setterName = "set" + capitalizeIdentifier(f.identifier());

      // Fluent setter
      var fluent = javaClass.addMethod()
          .setName(f.identifier())
          .setReturnType(dtoClass)
          .setPublic()
          .setBody("this." + f.identifier() + " = " + f.identifier() + ";\nreturn this;");
      var fluentParam = fluent.addParameter(fieldType, f.identifier());
      if (nullable) {
        fluentParam.addAnnotation("org.jspecify.annotations.Nullable");
      }

      // Getter with @Schema and @JsonProperty
      var getter = javaClass.addMethod()
          .setName(getterName)
          .setReturnType(nullable ? "Optional<" + fieldType + ">" : fieldType)
          .setPublic()
          .setBody(nullable
              ? "return Optional.ofNullable(" + f.identifier() + ");"
              : "return " + f.identifier() + ";");
      var schemaAnn = getter.addAnnotation("io.swagger.v3.oas.annotations.media.Schema");
      schemaAnn.setStringValue("name", f.name());
      if (f.description() != null) {
        schemaAnn.setStringValue("description", f.description());
      }
      if (f.defaultValue() != null) {
        schemaAnn.setStringValue("defaultValue", f.defaultValue());
      }
      getter.addAnnotation("com.fasterxml.jackson.annotation.JsonProperty")
          .setStringValue(f.name());

      // Setter with @JsonProperty
      var setter = javaClass.addMethod()
          .setName(setterName)
          .setReturnTypeVoid()
          .setPublic()
          .setBody("this." + f.identifier() + " = " + f.identifier() + ";");
      var setterParam = setter.addParameter(fieldType, f.identifier());
      if (nullable) {
        setterParam.addAnnotation("org.jspecify.annotations.Nullable");
      }
      setter.addAnnotation("com.fasterxml.jackson.annotation.JsonProperty")
          .setStringValue(f.name());
    }

    // Render nested enums for fields with inline enum values
    boolean hasNestedEnums = false;
    for (var f : fields) {
      if (!f.inlineEnumValues().isEmpty()) {
        // Roaster doesn't handle nested enums in classes well, so add as raw nested type
        javaClass.addNestedType(renderNestedEnum(f.effectiveJavaType(), f.inlineEnumValues()));
        hasNestedEnums = true;
      }
    }
    if (hasNestedEnums) {
      javaClass.addImport("com.fasterxml.jackson.annotation.JsonCreator");
      javaClass.addImport("com.fasterxml.jackson.annotation.JsonValue");
    }

    // Nullable getters now return Optional<T> directly — no post-processing needed.
    return licenseWithSource(licenseComment, sourceFile, schemaName) + "\n" + javaClass.toString();
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
    final var returnType = isNullAssignableField(field) ? annotateNullable("String") : "String";
    final var inputType = isNullAssignableField(field) ? "@Nullable Object" : "Object";
    final var nullGuard =
        isNullAssignableField(field)
            ? """
    if (value == null) {
      return null;
    }
"""
            : "";
    return """
  public static %s %s(final %s value) {
%s
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
  .formatted(returnType, methodName, inputType, nullGuard, field.name());
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
                  if (isNullAssignableField(field)) {
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

  /**
   * Renders {@code xxxOrDefault()} accessor methods for nullable fields on request DTOs that have
   * a natural zero-value default based on their Java type. This eliminates the repetitive
   * {@code field() != null ? field() : default} pattern in RequestMapper.
   *
   * <p>Only emitted for nullable fields that do NOT already have a spec-level default (those are
   * coerced in the compact constructor, so the field is never null after construction).
   */
  private static String renderOrDefaultAccessors(final List<ContractField> fields) {
    final var accessors =
        fields.stream()
          .filter(f -> isNullAssignableField(f) && f.defaultValue() == null)
            .map(
                field -> {
                  final var type = field.javaType();
                  final var id = field.identifier();
                  final var methodName = id + "OrDefault";
                  if (type.equals("String")) {
                    return """
  /** Returns {@code %s}, or an empty string if absent. */
  public String %s() {
    return %s != null ? %s : "";
  }
"""
                        .formatted(id, methodName, id, id);
                  } else if (type.startsWith("java.util.Map")) {
                    return """
  /** Returns {@code %s}, or an empty map if absent. */
  public %s %s() {
    return %s != null ? %s : java.util.Map.of();
  }
"""
                        .formatted(id, type, methodName, id, id);
                  } else if (type.startsWith("java.util.List")) {
                    return """
  /** Returns {@code %s}, or an empty list if absent. */
  public %s %s() {
    return %s != null ? %s : java.util.List.of();
  }
"""
                        .formatted(id, type, methodName, id, id);
                  } else if (type.equals("Integer") || type.equals("java.lang.Integer")) {
                    return """
  /** Returns {@code %s}, or {@code 0} if absent. */
  public int %s() {
    return %s != null ? %s : 0;
  }
"""
                        .formatted(id, methodName, id, id);
                  } else if (type.equals("Long") || type.equals("java.lang.Long")) {
                    return """
  /** Returns {@code %s}, or {@code 0L} if absent. */
  public long %s() {
    return %s != null ? %s : 0L;
  }
"""
                        .formatted(id, methodName, id, id);
                  }
                  return null;
                })
            .filter(s -> s != null)
            .toList();
    return accessors.isEmpty() ? "" : String.join("\n", accessors);
  }

  private static String renderStructuralCoercionHelper(final ContractField field) {
    final var methodName = "coerce" + capitalizeIdentifier(field.identifier());
    final var inputType = isNullAssignableField(field) ? "@Nullable Object" : "Object";
    final var nullGuard =
        isNullAssignableField(field)
            ? """
    if (value == null) {
      return null;
    }
"""
            : "";
    if (field.hasStrictObjectType()) {
      final var strictType = field.typeInfo().strictDtoClass();
      final var returnType = isNullAssignableField(field) ? annotateNullable(strictType) : strictType;
      return """
  public static %s %s(final %s value) {
%s
    if (value instanceof %s strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        \"%s must be a %s, but was \" + value.getClass().getName());
  }
"""
          .formatted(
              returnType,
              methodName,
              inputType,
              nullGuard,
              strictType,
              field.name(),
              strictType);
    }

    if (field.hasStrictListType()) {
      final var elementInfo = field.typeInfo().elementType();
      final var strictType = elementInfo.strictDtoClass();
      final var returnType =
          isNullAssignableField(field)
              ? annotateNullable("java.util.List<" + strictType + ">")
              : "java.util.List<" + strictType + ">";
      return """
  public static %s %s(final %s value) {
%s
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          \"%s must be a List of %s, but was \" + value.getClass().getName());
    }

    final var result = new ArrayList<%s>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        throw new IllegalArgumentException(
            \"%s must not contain null items.\");
      } else if (item instanceof %s strictItem) {
        result.add(strictItem);

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
      returnType,
      methodName,
      inputType,
      nullGuard,
              field.name(),
              strictType,
              strictType,
      field.name(),
              strictType,
              field.name(),
              strictType);
    }

    return "";
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
              + builderStorageType(f)
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
    final var fieldNullable = isNullAssignableField(field);
    if (!field.requiresCoercion()) {
      final var parameterType =
        fieldNullable ? annotateNullable(field.effectiveJavaType()) : field.effectiveJavaType();
      final var policyType =
        fieldNullable
          ? annotateNullableTypeArgument(field.effectiveJavaType())
          : field.effectiveJavaType();

      final var optionalPolicyOverload =
          policyAwareRequired && fieldNullable
              ? """

    @Override
    public %s %s(final %s %s, final ContractPolicy.FieldPolicy<%s> policy) {
      this.%s = policy.apply(%s, Fields.%s, this);
      return this;
    }
"""
                  .formatted(
                      optionalStepName,
                      field.identifier(),
          parameterType,
                      field.identifier(),
          policyType,
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
              parameterType,
              field.identifier(),
              field.identifier(),
              field.identifier()))
          + optionalPolicyOverload;
    }

    final var stringParameterType =
        fieldNullable ? annotateNullable(field.javaType()) : field.javaType();
    final var stringPolicyType =
        fieldNullable ? annotateNullableTypeArgument(field.javaType()) : field.javaType();

    final var optionalStringPolicyOverload =
        policyAwareRequired && fieldNullable
            ? """

    public Builder %s(final %s %s, final ContractPolicy.FieldPolicy<%s> policy) {
      this.%s = policy.apply(%s, Fields.%s, this);
      return this;
    }
"""
                .formatted(
                    field.identifier(),
                    stringParameterType,
                    field.identifier(),
                    stringPolicyType,
                    field.identifier(),
                    field.identifier(),
                    toConstantName(field.identifier()))
            : "";

    final var optionalObjectPolicyOverload =
      policyAwareRequired && fieldNullable
            ? """

    @Override
    public %s %s(final @Nullable Object %s, final ContractPolicy.FieldPolicy<@Nullable Object> policy) {
      this.%s = policy.apply(%s, Fields.%s, this);
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
          stringParameterType,
            field.identifier(),
            field.identifier(),
            field.identifier(),
          optionalStepName,
            field.identifier(),
            fieldNullable ? "@Nullable Object" : "Object",
            field.identifier(),
            field.identifier(),
            field.identifier()))
        + optionalStringPolicyOverload
        + optionalObjectPolicyOverload;
  }

  private static String renderOptionalStepMethod(
      final ContractField field, final String optionalStepName, final boolean policyAwareRequired) {
    final var fieldNullable = isNullAssignableField(field);
    if (!field.requiresCoercion()) {
      final var parameterType =
          fieldNullable ? annotateNullable(field.effectiveJavaType()) : field.effectiveJavaType();
      final var policyType =
          fieldNullable
              ? annotateNullableTypeArgument(field.effectiveJavaType())
              : field.effectiveJavaType();

      final var optionalPolicyOverload =
          policyAwareRequired && fieldNullable
              ? """

    %s %s(final %s %s, final ContractPolicy.FieldPolicy<%s> policy);
  """
                  .formatted(optionalStepName, field.identifier(), parameterType, field.identifier(), policyType)
              : "";

      return ("""
    %s %s(final %s %s);
  """
          .formatted(optionalStepName, field.identifier(), parameterType, field.identifier()))
          + optionalPolicyOverload;
    }

    final var stringParameterType =
        fieldNullable ? annotateNullable(field.javaType()) : field.javaType();
    final var stringPolicyType =
        fieldNullable ? annotateNullableTypeArgument(field.javaType()) : field.javaType();

    final var optionalStringPolicyOverload =
        policyAwareRequired && fieldNullable
            ? """

    %s %s(final %s %s, final ContractPolicy.FieldPolicy<%s> policy);
  """
                .formatted(
                    optionalStepName,
                    field.identifier(),
                    stringParameterType,
                    field.identifier(),
                    stringPolicyType)
            : "";

    final var optionalObjectPolicyOverload =
        policyAwareRequired && fieldNullable
            ? """

    %s %s(final @Nullable Object %s, final ContractPolicy.FieldPolicy<@Nullable Object> policy);
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
        stringParameterType,
        field.identifier(),
        optionalStepName,
        field.identifier(),
        fieldNullable ? "@Nullable Object" : "Object",
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
    final var requiredValue =
      field.required() && !field.nullable()
        ? "java.util.Objects.requireNonNull("
          + fieldValue
          + ", \""
          + field.name()
          + " must be set\")"
        : fieldValue;
    return field.requiresCoercion()
      ? "coerce" + capitalizeIdentifier(field.identifier()) + "(" + requiredValue + ")"
      : requiredValue;
  }

    private static String builderStorageType(final ContractField field) {
    final var storageType = field.requiresCoercion() ? "Object" : field.effectiveJavaType();
    return annotateNullable(storageType);
    }

    private static boolean isNullAssignableField(final ContractField field) {
      return field.nullable() || !field.required();
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

  private static String annotateNullable(final String type) {
    return "@Nullable " + simplifyCollectionTypeName(type);
  }

  private static String normalizeEmittedType(final String type) {
    return type.replace(TARGET_PACKAGE + ".", "");
  }

  private static String annotateNullableTypeArgument(final String type) {
    return annotateNullable(type);
  }

  private static String simplifyCollectionTypeName(final String type) {
    return type
        .replace("java.util.List", "List")
        .replace("java.util.Set", "Set")
        .replace("java.util.Map", "Map");
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

    final var schemaName = key.schemaName();
    if (resolvingStack.contains(key)) {
      if (hasContractType(schema)) {
        final var strictType = dtoClassName(schemaName);
        return TypeInfo.strictObject(strictType, schemaName);
      }
      return TypeInfo.scalar("Object");
    }

    if (hasContractType(schema)) {
      final var strictType = dtoClassName(schemaName);
      return TypeInfo.strictObject(strictType, schemaName);
    }

    // Filter property schemas (oneOf with inline primitive + $ref) are registered in
    // AVAILABLE_STRICT_CONTRACTS but have no properties on the parent schema node.
    // Resolve them to the sealed interface type with self-deserializing flag.
    if (AVAILABLE_STRICT_CONTRACTS.contains(schemaName)
        && isPolymorphicSchema(schema)
        && isFilterPropertySchema(schema)) {
      final var strictType = dtoClassName(schemaName);
      return TypeInfo.selfDeserializingObject(strictType, schemaName);
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

  private static boolean hasContractType(final SchemaDef schema) {
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
      /** True for types with their own @JsonDeserialize that don't need coercion helpers. */
      boolean selfDeserializing,
      TypeInfo elementType) {

    static TypeInfo scalar(final String javaType) {
      return new TypeInfo(javaType, false, null, null, false, null);
    }

    static TypeInfo strictObject(
        final String strictDtoClass,
        final String protocolJavaType) {
      return new TypeInfo(
          strictDtoClass, true, strictDtoClass, protocolJavaType, false, null);
    }

    /** A strict object type that handles its own deserialization (e.g., filter properties). */
    static TypeInfo selfDeserializingObject(
        final String strictDtoClass,
        final String protocolJavaType) {
      return new TypeInfo(
          strictDtoClass, true, strictDtoClass, protocolJavaType, true, null);
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

  private static String renderFlatPageRequestDto(String licenseComment) {
    final var className = "SearchQueryPageRequest";
    JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
    javaClass.setPackage(TARGET_PACKAGE);
    javaClass.setName(className);
    javaClass.addAnnotation("com.fasterxml.jackson.annotation.JsonInclude")
        .setLiteralValue("value", "com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL");
    javaClass.addAnnotation("org.jspecify.annotations.NullMarked");
    javaClass.addAnnotation("jakarta.annotation.Generated")
        .setStringValue("value", "io.camunda.gateway.protocol.model.tools.ApiProtocolModelGenerator");

    javaClass.addImport("java.util.Optional");

    // Fields: limit, from, after, before
    record PageField(String name, String type) {}
    final var pageFields = List.of(
        new PageField("limit", "Integer"),
        new PageField("from", "Integer"),
        new PageField("after", "String"),
        new PageField("before", "String"));

    for (var pf : pageFields) {
      javaClass.addField().setName(pf.name()).setType(pf.type()).setPrivate()
          .addAnnotation("org.jspecify.annotations.Nullable");
    }

    javaClass.addMethod().setConstructor(true).setPublic().setBody("super();");

    for (var pf : pageFields) {
      addNullableFluentSetterGetterSetter(javaClass, className, pf.name(), pf.type());
    }

    return licenseComment + "\n" + patchNullableGetterReturnTypes(javaClass.toString(),
        List.of("limit", "from", "after", "before"),
        List.of("Integer", "Integer", "String", "String"));
  }

  private static String renderOffsetPaginationDto(String licenseComment) {
    final var className = "OffsetPagination";
    JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
    javaClass.setPackage(TARGET_PACKAGE);
    javaClass.setName(className);
    javaClass.addAnnotation("com.fasterxml.jackson.annotation.JsonInclude")
        .setLiteralValue("value", "com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL");
    javaClass.addAnnotation("org.jspecify.annotations.NullMarked");
    javaClass.addAnnotation("jakarta.annotation.Generated")
        .setStringValue("value", "io.camunda.gateway.protocol.model.tools.ApiProtocolModelGenerator");

    javaClass.addImport("java.util.Optional");

    javaClass.addField().setName("limit").setType("Integer").setPrivate()
        .addAnnotation("org.jspecify.annotations.Nullable");
    javaClass.addField().setName("from").setType("Integer").setPrivate()
        .addAnnotation("org.jspecify.annotations.Nullable");

    javaClass.addMethod().setConstructor(true).setPublic().setBody("super();");

    addNullableFluentSetterGetterSetter(javaClass, className, "limit", "Integer");
    addNullableFluentSetterGetterSetter(javaClass, className, "from", "Integer");

    return licenseComment + "\n" + patchNullableGetterReturnTypes(javaClass.toString(),
        List.of("limit", "from"),
        List.of("Integer", "Integer"));
  }


  private static String renderSearchQueryRequestDto(
      String licenseComment, String className, String sortContractClass,
      String filterContractClass, PaginationType paginationType) {
    JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
    javaClass.setPackage(TARGET_PACKAGE);
    javaClass.setName(className);
    javaClass.addAnnotation("com.fasterxml.jackson.annotation.JsonInclude")
        .setLiteralValue("value", "com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL");
    javaClass.addAnnotation("org.jspecify.annotations.NullMarked");
    javaClass.addAnnotation("jakarta.annotation.Generated")
        .setStringValue("value", "io.camunda.gateway.protocol.model.tools.ApiProtocolModelGenerator");

    final var pageType = paginationType == PaginationType.OFFSET_ONLY
        ? "OffsetPagination" : "SearchQueryPageRequest";
    final var sortType = sortContractClass != null
        ? "List<" + sortContractClass + ">" : "List<Object>";
    javaClass.addImport("java.util.List");
    javaClass.addImport("java.util.Optional");
    final String filterType;
    if (filterContractClass != null) {
      filterType = filterContractClass.contains(".")
          ? filterContractClass.substring(filterContractClass.lastIndexOf('.') + 1)
          : filterContractClass;
    } else {
      filterType = "Object";
    }

    // Fields
    javaClass.addField().setName("page").setType(pageType).setPrivate()
        .addAnnotation("org.jspecify.annotations.Nullable");
    javaClass.addField().setName("sort").setType(sortType).setPrivate()
        .addAnnotation("org.jspecify.annotations.Nullable");
    javaClass.addField().setName("filter").setType(filterType).setPrivate()
        .addAnnotation("org.jspecify.annotations.Nullable");

    javaClass.addMethod().setConstructor(true).setPublic().setBody("super();");

    addNullableFluentSetterGetterSetter(javaClass, className, "page", pageType);
    addNullableFluentSetterGetterSetter(javaClass, className, "sort", sortType);
    addNullableFluentSetterGetterSetter(javaClass, className, "filter", filterType);

    return licenseComment + "\n" + patchNullableGetterReturnTypes(javaClass.toString(),
        List.of("page", "sort", "filter"),
        List.of(pageType, sortType, filterType));
  }

  private static String simpleClassName(String className) {
    final int dotIdx = className.lastIndexOf('.');
    return dotIdx >= 0 ? className.substring(dotIdx + 1) : className;
  }

  private static String lowerFirst(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
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
      String licenseComment,
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
            "    ofNullable(filter.get" + capitalizeIdentifier(id) + "())"
                + ".map(mapToOperations(" + simpleTypeName(opType) + ".class))"
                + ".ifPresent(builder::" + builderBase + "Operations);");
      } else if ("Boolean".equals(f.javaType())) {
        // Boolean field → direct
        fieldMappings.add(
            "    ofNullable(filter.get" + capitalizeIdentifier(id) + "()).ifPresent(builder::" + builderBase + ");");
      } else if (typeInfo != null
          && (typeInfo.strictObjectType() || typeInfo.strictListType())) {
        // Complex nested type (e.g., $or, variables) — skipped, requires hand-written mapper
        continue;
      } else if (f.javaType().startsWith("java.util.List")
          || f.javaType().startsWith("java.util.Set")) {
        // Collection type — skipped, requires hand-written mapper
        continue;
      } else {
        // Simple scalar (String, Integer, etc.) — direct value
        fieldMappings.add(
            "    ofNullable(filter.get" + capitalizeIdentifier(id) + "()).ifPresent(builder::" + builderBase + ");");
      }
    }

    // Render the class using Roaster
    JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
    javaClass.setPackage(TARGET_PACKAGE);
    javaClass.setName(mapperClassName);
    javaClass.setFinal(true);
    javaClass.addImport("io.camunda.gateway.protocol.model.AdvancedSearchFilterUtil.mapToOperations")
        .setStatic(true);
    javaClass.addImport("java.util.Optional.ofNullable").setStatic(true);
    javaClass.addImport(domainFilterFqn);
    javaClass.addImport("io.camunda.search.filter.FilterBuilders");
    if (needsOffsetDateTime) {
      javaClass.addImport("java.time.OffsetDateTime");
    }
    javaClass.addAnnotation("jakarta.annotation.Generated")
        .setStringValue("value", "ApiProtocolModelGenerator");

    // Private constructor
    javaClass.addMethod().setConstructor(true).setPrivate().setBody("");

    // Mapper method
    final var bodyLines = new StringBuilder();
    bodyLines.append("if (filter == null) {\n");
    bodyLines.append("  return FilterBuilders.").append(builderFactoryMethod).append("().build();\n");
    bodyLines.append("}\n");
    bodyLines.append("final var builder = FilterBuilders.").append(builderFactoryMethod).append("();\n");
    for (var mapping : fieldMappings) {
      bodyLines.append(mapping.strip()).append("\n");
    }
    bodyLines.append("return builder.build();");

    final var method = javaClass.addMethod()
        .setName(methodName)
        .setReturnType(domainFilterName)
        .setPublic()
        .setStatic(true)
        .setBody(bodyLines.toString());
    method.addParameter(filterDtoClass, "filter")
        .setFinal(true)
        .addAnnotation("org.jspecify.annotations.Nullable");

    return licenseComment + "\n" + javaClass.toString();
  }

  /** Returns the simple (unqualified) class name from a potentially qualified type name. */
  private static String simpleTypeName(String type) {
    final int lastDot = type.lastIndexOf('.');
    return lastDot >= 0 ? type.substring(lastDot + 1) : type;
  }
}
