/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;

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
  String[] ALL_DEFINITION_INDEXES =
    {PROCESS_DEFINITION_INDEX_NAME, DECISION_DEFINITION_INDEX_NAME, EVENT_PROCESS_DEFINITION_INDEX_NAME};
  String TENANT_NOT_DEFINED_VALUE = "null";

  Optional<DefinitionWithTenantIdsDto> getDefinitionWithAvailableTenants(final DefinitionType type,
                                                                         final String key);

  Optional<DefinitionWithTenantIdsDto> getDefinitionWithAvailableTenants(final DefinitionType type,
                                                                         final String key,
                                                                         final List<String> versions,
                                                                         final Supplier<String> latestVersionSupplier);

  List<DefinitionWithTenantIdsDto> getFullyImportedDefinitionsWithTenantIds(final DefinitionType type,
                                                                            final Set<String> keys,
                                                                            final Set<String> tenantIds);

  <T extends DefinitionOptimizeResponseDto> List<T> getFullyImportedDefinitions(final DefinitionType type,
                                                                                final boolean withXml);

  <T extends DefinitionOptimizeResponseDto> Optional<T> getFirstFullyImportedDefinitionFromTenantsIfAvailable(
    final DefinitionType type,
    final String definitionKey,
    final List<String> definitionVersions,
    final List<String> tenantIds);

  <T extends DefinitionOptimizeResponseDto> List<T> getLatestFullyImportedDefinitionsFromTenantsIfAvailable(
    final DefinitionType type,
    final String definitionKey);

  Set<String> getDefinitionEngines(final DefinitionType type, final String definitionKey);

  Map<String, TenantIdWithDefinitionsDto> getDefinitionsGroupedByTenant();

  String getLatestVersionToKey(final DefinitionType type, final String key);

  List<DefinitionVersionResponseDto> getDefinitionVersions(final DefinitionType type,
                                                           final String key,
                                                           final Set<String> tenantIds);

  <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(final DefinitionType type,
                                                                   final boolean fullyImported,
                                                                   final boolean withXml,
                                                                   final boolean includeDeleted);

  <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(final DefinitionType type,
                                                                   final Set<String> definitionKeys,
                                                                   final boolean fullyImported,
                                                                   final boolean withXml,
                                                                   final boolean includeDeleted);

  <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(final DefinitionType type,
                                                                   final BoolQueryBuilder filterQuery,
                                                                   final boolean withXml);
}
