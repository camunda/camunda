/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_XML;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import io.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public interface DefinitionReader {
  String VERSION_AGGREGATION = "versions";
  String VERSION_TAG_AGGREGATION = "versionTags";
  String TENANT_AGGREGATION = "tenants";
  String ENGINE_AGGREGATION = "engines";
  String TOP_HITS_AGGREGATION = "topHits";
  String DEFINITION_KEY_FILTER_AGGREGATION = "definitionKeyFilter";
  String DEFINITION_TYPE_AGGREGATION = "definitionType";
  String DEFINITION_KEY_AGGREGATION = "definitionKey";
  String DEFINITION_KEY_AND_TYPE_AGGREGATION = "definitionKeyAndType";
  String DEFINITION_KEY_AND_TYPE_AND_TENANT_AGGREGATION = "definitionKeyAndTypeAndTenant";
  String NAME_AGGREGATION = "definitionName";
  String[] ALL_DEFINITION_INDEXES = {PROCESS_DEFINITION_INDEX_NAME, DECISION_DEFINITION_INDEX_NAME};
  String TENANT_NOT_DEFINED_VALUE = "null";

  Optional<DefinitionWithTenantIdsDto> getDefinitionWithAvailableTenants(
      final DefinitionType type, final String key);

  Optional<DefinitionWithTenantIdsDto> getDefinitionWithAvailableTenants(
      final DefinitionType type,
      final String key,
      final List<String> versions,
      final Supplier<String> latestVersionSupplier);

  List<DefinitionWithTenantIdsDto> getFullyImportedDefinitionsWithTenantIds(
      final DefinitionType type, final Set<String> keys, final Set<String> tenantIds);

  <T extends DefinitionOptimizeResponseDto> List<T> getFullyImportedDefinitions(
      final DefinitionType type, final boolean withXml);

  <T extends DefinitionOptimizeResponseDto>
      Optional<T> getFirstFullyImportedDefinitionFromTenantsIfAvailable(
          final DefinitionType type,
          final String definitionKey,
          final List<String> definitionVersions,
          final List<String> tenantIds);

  <T extends DefinitionOptimizeResponseDto>
      List<T> getLatestFullyImportedDefinitionsFromTenantsIfAvailable(
          final DefinitionType type, final String definitionKey);

  Set<String> getDefinitionEngines(final DefinitionType type, final String definitionKey);

  Map<String, TenantIdWithDefinitionsDto> getDefinitionsGroupedByTenant();

  String getLatestVersionToKey(final DefinitionType type, final String key);

  List<DefinitionVersionResponseDto> getDefinitionVersions(
      final DefinitionType type, final String key, final Set<String> tenantIds);

  <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(
      final DefinitionType type,
      final boolean fullyImported,
      final boolean withXml,
      final boolean includeDeleted);

  <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(
      final DefinitionType type,
      final Set<String> definitionKeys,
      final boolean fullyImported,
      final boolean withXml,
      final boolean includeDeleted);

  default String[] resolveIndexNameForType(final DefinitionType type) {
    if (type == null) {
      return ALL_DEFINITION_INDEXES;
    }

    return switch (type) {
      case PROCESS -> new String[] {PROCESS_DEFINITION_INDEX_NAME};
      case DECISION -> new String[] {DECISION_DEFINITION_INDEX_NAME};
      default -> throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    };
  }

  default String resolveXmlFieldFromType(final DefinitionType type) {
    return switch (type) {
      case PROCESS -> PROCESS_DEFINITION_XML;
      case DECISION -> DECISION_DEFINITION_XML;
      default -> throw new IllegalStateException("Unknown DefinitionType:" + type);
    };
  }

  default String resolveVersionFieldFromType(final DefinitionType type) {
    return switch (type) {
      case PROCESS -> PROCESS_DEFINITION_VERSION;
      case DECISION -> DECISION_DEFINITION_VERSION;
      default -> throw new IllegalStateException("Unknown DefinitionType:" + type);
    };
  }

  default String resolveDefinitionKeyFieldFromType(final DefinitionType type) {
    return switch (type) {
      case PROCESS -> PROCESS_DEFINITION_KEY;
      case DECISION -> DECISION_DEFINITION_KEY;
      default -> throw new IllegalStateException("Unknown DefinitionType:" + type);
    };
  }

  @SuppressWarnings(UNCHECKED_CAST)
  default <T extends DefinitionOptimizeResponseDto> Class<T> resolveDefinitionClassFromType(
      final DefinitionType type) {
    if (Objects.isNull(type)) {
      return (Class<T>) DefinitionOptimizeResponseDto.class;
    }
    return switch (type) {
      case PROCESS -> (Class<T>) ProcessDefinitionOptimizeDto.class;
      case DECISION -> (Class<T>) DecisionDefinitionOptimizeDto.class;
      default -> throw new IllegalStateException("Unknown DefinitionType:" + type);
    };
  }
}
