/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.service.db.reader.DefinitionReader;
import org.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.os.schema.index.DecisionDefinitionIndexOS;
import org.camunda.optimize.service.os.schema.index.ProcessDefinitionIndexOS;
import org.camunda.optimize.service.os.schema.index.events.EventProcessDefinitionIndexOS;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_XML;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@AllArgsConstructor
@Slf4j
@Component
@Conditional(OpenSearchCondition.class)
public class DefinitionReaderOS implements DefinitionReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  @Override
  public Optional<DefinitionWithTenantIdsDto> getDefinitionWithAvailableTenants(final DefinitionType type,
                                                                                final String key) {
    //todo will be handled in the OPT-7230
    return getDefinitionWithAvailableTenants(type, key, null, null);
  }

  @Override
  public Optional<DefinitionWithTenantIdsDto> getDefinitionWithAvailableTenants(final DefinitionType type,
                                                                                final String key,
                                                                                final List<String> versions,
                                                                                final Supplier<String> latestVersionSupplier) {
    //todo will be handled in the OPT-7230
    if (type == null || key == null) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  @Override
  public List<DefinitionWithTenantIdsDto> getFullyImportedDefinitionsWithTenantIds(final DefinitionType type,
                                                                                   final Set<String> keys,
                                                                                   final Set<String> tenantIds) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> List<T> getFullyImportedDefinitions(final DefinitionType type,
                                                                                       final boolean withXml) {
    return getDefinitions(type, true, withXml, false);
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> Optional<T> getFirstFullyImportedDefinitionFromTenantsIfAvailable(
    final DefinitionType type,
    final String definitionKey,
    final List<String> definitionVersions,
    final List<String> tenantIds) {
    if (definitionKey == null || definitionVersions == null || definitionVersions.isEmpty()) {
      return Optional.empty();
    }
//todo will be handled in the OPT-7230
    return Optional.empty();
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> List<T> getLatestFullyImportedDefinitionsFromTenantsIfAvailable(
    final DefinitionType type,
    final String definitionKey) {
    if (definitionKey == null) {
      return Collections.emptyList();
    }
//todo will be handled in the OPT-7230
    return Collections.emptyList();
  }

  @Override
  public Set<String> getDefinitionEngines(final DefinitionType type, final String definitionKey) {
    //todo will be handled in the OPT-7230
    return new HashSet<>();
  }

  @Override
  public Map<String, TenantIdWithDefinitionsDto> getDefinitionsGroupedByTenant() {
    //todo will be handled in the OPT-7230
    return new HashMap<>();
  }

  @Override
  public String getLatestVersionToKey(final DefinitionType type, final String key) {
    //todo will be handled in the OPT-7230
    return "";
  }

  @Override
  public List<DefinitionVersionResponseDto> getDefinitionVersions(final DefinitionType type,
                                                                  final String key,
                                                                  final Set<String> tenantIds) {
    //todo will be handled in the OPT-7230
    return Collections.emptyList();
  }


  @Override
  public <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(final DefinitionType type,
                                                                          final boolean fullyImported,
                                                                          final boolean withXml,
                                                                          final boolean includeDeleted) {
    //todo will be handled in the OPT-7230
    return Collections.emptyList();
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(final DefinitionType type,
                                                                          final Set<String> definitionKeys,
                                                                          final boolean fullyImported,
                                                                          final boolean withXml,
                                                                          final boolean includeDeleted) {
    //todo will be handled in the OPT-7230
    return Collections.emptyList();
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(final DefinitionType type,
                                                                          final BoolQueryBuilder filterQuery,
                                                                          final boolean withXml) {
    //todo will be handled in the OPT-7230
    return Collections.emptyList();
  }

  private DefinitionType resolveDefinitionTypeFromIndexAlias(String indexName) {
    if (indexName.equals(getOptimizeIndexNameForIndex(new ProcessDefinitionIndexOS()))
      || indexName.equals(getOptimizeIndexNameForIndex(new EventProcessDefinitionIndexOS()))) {
      return DefinitionType.PROCESS;
    } else if (indexName.equals(getOptimizeIndexNameForIndex(new DecisionDefinitionIndexOS()))) {
      return DefinitionType.DECISION;
    } else {
      throw new OptimizeRuntimeException("Unexpected definition index name: " + indexName);
    }
  }

  private boolean resolveIsEventProcessFromIndexAlias(String indexName) {
    return indexName.equals(getOptimizeIndexNameForIndex(new EventProcessDefinitionIndexOS()));
  }

  private String getOptimizeIndexNameForIndex(final DefaultIndexMappingCreator index) {
    return osClient.getIndexNameService().getOptimizeIndexNameWithVersion(index);
  }

  private String[] resolveIndexNameForType(final DefinitionType type) {
    if (type == null) {
      return ALL_DEFINITION_INDEXES;
    }

    switch (type) {
      case PROCESS:
        return new String[]{PROCESS_DEFINITION_INDEX_NAME, EVENT_PROCESS_DEFINITION_INDEX_NAME};
      case DECISION:
        return new String[]{DECISION_DEFINITION_INDEX_NAME};
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }

  private String resolveXmlFieldFromType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return PROCESS_DEFINITION_XML;
      case DECISION:
        return DECISION_DEFINITION_XML;
      default:
        throw new IllegalStateException("Unknown DefinitionType:" + type);
    }
  }

  private String resolveVersionFieldFromType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return PROCESS_DEFINITION_VERSION;
      case DECISION:
        return DECISION_DEFINITION_VERSION;
      default:
        throw new IllegalStateException("Unknown DefinitionType:" + type);
    }
  }

  private String resolveDefinitionKeyFieldFromType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return PROCESS_DEFINITION_KEY;
      case DECISION:
        return DECISION_DEFINITION_KEY;
      default:
        throw new IllegalStateException("Unknown DefinitionType:" + type);
    }
  }

  @SuppressWarnings(UNCHECKED_CAST)
  private <T extends DefinitionOptimizeResponseDto> Class<T> resolveDefinitionClassFromType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return (Class<T>) ProcessDefinitionOptimizeDto.class;
      case DECISION:
        return (Class<T>) DecisionDefinitionOptimizeDto.class;
      default:
        throw new IllegalStateException("Unknown DefinitionType:" + type);
    }
  }

}
