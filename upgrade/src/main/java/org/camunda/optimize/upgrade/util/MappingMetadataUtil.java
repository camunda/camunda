/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.AlertIndex;
import org.camunda.optimize.service.es.schema.index.BusinessKeyIndex;
import org.camunda.optimize.service.es.schema.index.CollectionIndex;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.DashboardShareIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.es.schema.index.LicenseIndex;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.es.schema.index.OnboardingStateIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.ReportShareIndex;
import org.camunda.optimize.service.es.schema.index.SettingsIndex;
import org.camunda.optimize.service.es.schema.index.TenantIndex;
import org.camunda.optimize.service.es.schema.index.TerminatedUserSessionIndex;
import org.camunda.optimize.service.es.schema.index.VariableUpdateInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex;
import org.camunda.optimize.service.es.schema.index.index.ImportIndexIndex;
import org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.cluster.metadata.AliasMetadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_PREFIX;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MappingMetadataUtil {
  public static List<IndexMappingCreator> getAllMappings(final OptimizeElasticsearchClient esClient) {
    List<IndexMappingCreator> allMappings = new ArrayList<>();
    allMappings.addAll(getAllNonDynamicMappings());
    allMappings.addAll(getAllDynamicMappings(esClient));
    return allMappings;
  }

  private static List<IndexMappingCreator> getAllNonDynamicMappings() {
    return Arrays.asList(
      new AlertIndex(),
      new BusinessKeyIndex(),
      new CollectionIndex(),
      new DashboardIndex(),
      new DashboardShareIndex(),
      new DecisionDefinitionIndex(),
      new LicenseIndex(),
      new MetadataIndex(),
      new OnboardingStateIndex(),
      new ProcessDefinitionIndex(),
      new ProcessInstanceIndex(),
      new ReportShareIndex(),
      new SettingsIndex(),
      new TenantIndex(),
      new TerminatedUserSessionIndex(),
      new VariableUpdateInstanceIndex(),
      new EventIndex(),
      new EventProcessDefinitionIndex(),
      new EventProcessMappingIndex(),
      new EventProcessPublishStateIndex(),
      new ImportIndexIndex(),
      new TimestampBasedImportIndex(),
      new CombinedReportIndex(),
      new SingleDecisionReportIndex(),
      new SingleProcessReportIndex()
    );
  }

  public static List<DecisionInstanceIndex> retrieveAllDecisionInstanceIndices(
    final OptimizeElasticsearchClient esClient) {
    return retrieveAllDynamicIndexKeysForPrefix(esClient, DECISION_INSTANCE_INDEX_PREFIX)
      .stream()
      .map(DecisionInstanceIndex::new)
      .collect(toList());
  }

  public static List<EventProcessInstanceIndex> retrieveAllEventProcessInstanceIndices(
    final OptimizeElasticsearchClient esClient) {
    return retrieveAllDynamicIndexKeysForPrefix(esClient, EVENT_PROCESS_INSTANCE_INDEX_PREFIX)
      .stream()
      .map(EventProcessInstanceIndex::new)
      .collect(toList());
  }

  private static List<CamundaActivityEventIndex> retrieveAllCamundaActivityEventIndices(
    final OptimizeElasticsearchClient esClient) {
    return retrieveAllDynamicIndexKeysForPrefix(esClient, CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX)
      .stream()
      .map(CamundaActivityEventIndex::new)
      .collect(toList());
  }

  private static List<EventSequenceCountIndex> retrieveAllSequenceCountIndices(
    final OptimizeElasticsearchClient esClient) {
    return retrieveAllDynamicIndexKeysForPrefix(esClient, EVENT_SEQUENCE_COUNT_INDEX_PREFIX)
      .stream()
      .map(EventSequenceCountIndex::new)
      .collect(toList());
  }

  private static List<EventTraceStateIndex> retrieveAllEventTraceIndices(
    final OptimizeElasticsearchClient esClient) {
    return retrieveAllDynamicIndexKeysForPrefix(esClient, EVENT_TRACE_STATE_INDEX_PREFIX)
      .stream()
      .map(EventTraceStateIndex::new)
      .collect(toList());
  }

  private static List<IndexMappingCreator> getAllDynamicMappings(final OptimizeElasticsearchClient esClient) {
    List<IndexMappingCreator> dynamicMappings = new ArrayList<>();
    dynamicMappings.addAll(retrieveAllCamundaActivityEventIndices(esClient));
    dynamicMappings.addAll(retrieveAllSequenceCountIndices(esClient));
    dynamicMappings.addAll(retrieveAllEventTraceIndices(esClient));
    dynamicMappings.addAll(retrieveAllDecisionInstanceIndices(esClient));
    return dynamicMappings;
  }

  private static List<String> retrieveAllDynamicIndexKeysForPrefix(final OptimizeElasticsearchClient esClient,
                                                                   final String dynamicIndexPrefix) {
    final GetAliasesResponse aliases;
    try {
      aliases = esClient.getAlias(
        new GetAliasesRequest(dynamicIndexPrefix + "*"), RequestOptions.DEFAULT
      );
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed retrieving aliases for dynamic index prefix " + dynamicIndexPrefix, e);
    }
    return aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetadata::alias))
      .map(fullAliasName ->
             fullAliasName.substring(fullAliasName.lastIndexOf(dynamicIndexPrefix) + dynamicIndexPrefix.length()))
      .collect(toList());
  }
}
