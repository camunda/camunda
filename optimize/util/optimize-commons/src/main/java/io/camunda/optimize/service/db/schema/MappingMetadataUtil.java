/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_TRACE_STATE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.events.EventSequenceCountIndexES;
import io.camunda.optimize.service.db.es.schema.index.events.EventTraceStateIndexES;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.os.schema.index.DecisionInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.events.EventSequenceCountIndexOS;
import io.camunda.optimize.service.db.os.schema.index.events.EventTraceStateIndexOS;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex;
import io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MappingMetadataUtil {

  private final DatabaseClient dbClient;
  private final boolean isElasticSearchClient;

  public MappingMetadataUtil(final DatabaseClient dbClient) {
    this.dbClient = dbClient;
    isElasticSearchClient = dbClient instanceof OptimizeElasticsearchClient;
  }

  public List<IndexMappingCreator<?>> getAllMappings(final String indexPrefix) {
    final List<IndexMappingCreator<?>> allMappings = new ArrayList<>();
    allMappings.addAll(getAllNonDynamicMappings());
    allMappings.addAll(getAllDynamicMappings(indexPrefix));
    return allMappings;
  }

  private Collection<? extends IndexMappingCreator<?>> getAllNonDynamicMappings() {
    return isElasticSearchClient
        ? ElasticSearchSchemaManager.getAllNonDynamicMappings()
        : OpenSearchSchemaManager.getAllNonDynamicMappings();
  }

  public List<IndexMappingCreator<?>> getAllDynamicMappings(final String indexPrefix) {
    final List<IndexMappingCreator<?>> dynamicMappings = new ArrayList<>();
    dynamicMappings.addAll(retrieveAllSequenceCountIndices());
    dynamicMappings.addAll(retrieveAllEventTraceIndices());
    dynamicMappings.addAll(retrieveAllProcessInstanceIndices(indexPrefix));
    dynamicMappings.addAll(retrieveAllDecisionInstanceIndices());
    return dynamicMappings;
  }

  public List<String> retrieveProcessInstanceIndexIdentifiers(
      final String configuredIndexPrefix, final boolean eventBased) {
    final Map<String, Set<String>> aliases;
    final String fullIndexPrefix =
        configuredIndexPrefix
            + "-"
            + (eventBased ? EVENT_PROCESS_INSTANCE_INDEX_PREFIX : PROCESS_INSTANCE_INDEX_PREFIX);
    try {
      aliases = dbClient.getAliasesForIndexPattern(fullIndexPrefix + "*");
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Failed retrieving aliases for dynamic index prefix " + fullIndexPrefix, e);
    }
    return aliases.entrySet().stream()
        // Response requires filtering because it might include both event and non-event based
        // process instance indices
        // due to the shared alias
        .filter(
            aliasMetadataPerIndex ->
                filterProcessInstanceIndexAliases(aliasMetadataPerIndex.getValue(), eventBased))
        .flatMap(aliasMetadataPerIndex -> aliasMetadataPerIndex.getValue().stream())
        .filter(fullAliasName -> fullAliasName.contains(fullIndexPrefix))
        .map(
            fullAliasName ->
                fullAliasName.substring(
                    fullAliasName.indexOf(fullIndexPrefix) + fullIndexPrefix.length()))
        .toList();
  }

  private boolean filterProcessInstanceIndexAliases(
      final Set<String> aliasMetadataSet, final boolean eventBased) {
    if (eventBased) {
      return aliasMetadataSet.stream()
          .anyMatch(aliasMetadata -> aliasMetadata.contains(EVENT_PROCESS_INSTANCE_INDEX_PREFIX));
    } else {
      return aliasMetadataSet.stream()
          .noneMatch(aliasMetadata -> aliasMetadata.contains(EVENT_PROCESS_INSTANCE_INDEX_PREFIX));
    }
  }

  private List<? extends DecisionInstanceIndex<?>> retrieveAllDecisionInstanceIndices() {
    return retrieveAllDynamicIndexKeysForPrefix(DECISION_INSTANCE_INDEX_PREFIX).stream()
        .map(
            key ->
                isElasticSearchClient
                    ? new DecisionInstanceIndexES(key)
                    : new DecisionInstanceIndexOS(key))
        .toList();
  }

  private List<? extends EventSequenceCountIndex<?>> retrieveAllSequenceCountIndices() {
    return retrieveAllDynamicIndexKeysForPrefix(EVENT_SEQUENCE_COUNT_INDEX_PREFIX).stream()
        .map(
            key ->
                isElasticSearchClient
                    ? new EventSequenceCountIndexES(key)
                    : new EventSequenceCountIndexOS(key))
        .toList();
  }

  private List<? extends EventTraceStateIndex<?>> retrieveAllEventTraceIndices() {
    return retrieveAllDynamicIndexKeysForPrefix(EVENT_TRACE_STATE_INDEX_PREFIX).stream()
        .map(
            key ->
                isElasticSearchClient
                    ? new EventTraceStateIndexES(key)
                    : new EventTraceStateIndexOS(key))
        .toList();
  }

  private List<? extends ProcessInstanceIndex<?>> retrieveAllProcessInstanceIndices(
      final String indexPrefix) {
    return retrieveProcessInstanceIndexIdentifiers(indexPrefix, false).stream()
        .map(
            key ->
                isElasticSearchClient
                    ? new ProcessInstanceIndexES(key)
                    : new ProcessInstanceIndexOS(key))
        .toList();
  }

  private List<String> retrieveAllDynamicIndexKeysForPrefix(final String dynamicIndexPrefix) {
    try {
      return dbClient.getAllIndicesForAlias(dynamicIndexPrefix + "*").stream()
          .map(
              fullAliasName ->
                  fullAliasName.substring(
                      fullAliasName.indexOf(dynamicIndexPrefix) + dynamicIndexPrefix.length()))
          .toList();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Failed retrieving aliases for dynamic index prefix " + dynamicIndexPrefix, e);
    }
  }
}
