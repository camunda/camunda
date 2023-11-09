/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.db.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndexES;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndexES;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndexES;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndexES;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndexES;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.db.DatabaseConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_TRACE_STATE_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;

@Slf4j
public class MappingMetadataUtil {

  private final DatabaseClient dbClient;

  public MappingMetadataUtil(DatabaseClient dbClient) {
    this.dbClient = dbClient;
  }

  public List<IndexMappingCreator<?>> getAllMappings() {
    List<IndexMappingCreator<?>> allMappings = new ArrayList<>();
    allMappings.addAll(getAllNonDynamicMappings());
    allMappings.addAll(getAllDynamicMappings());
    return allMappings;
  }

  private Collection<? extends IndexMappingCreator<?>> getAllNonDynamicMappings() {
    return dbClient instanceof OptimizeElasticsearchClient ?
      ElasticSearchSchemaManager.getAllNonDynamicMappings() : null;
    // TODO Not implemented for OpenSearch yet, to be done with OPT-7349
  }

  public List<IndexMappingCreator<?>> getAllDynamicMappings() {
    List<IndexMappingCreator<?>> dynamicMappings = new ArrayList<>();
    dynamicMappings.addAll(retrieveAllCamundaActivityEventIndices());
    dynamicMappings.addAll(retrieveAllSequenceCountIndices());
    dynamicMappings.addAll(retrieveAllEventTraceIndices());
    dynamicMappings.addAll(retrieveAllProcessInstanceIndices());
    dynamicMappings.addAll(retrieveAllDecisionInstanceIndices());
    return dynamicMappings;
  }

  public List<String> retrieveProcessInstanceIndexIdentifiers(final boolean eventBased) {
    final Map<String, Set<String>> aliases;
    final String prefix = eventBased ? EVENT_PROCESS_INSTANCE_INDEX_PREFIX : PROCESS_INSTANCE_INDEX_PREFIX;
    try {
      aliases = dbClient.getAliasesForIndex(prefix + "*");
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed retrieving aliases for dynamic index prefix " + prefix, e);
    }
    return aliases.entrySet()
      .stream()
      // Response requires filtering because it might include both event and non event based process instance indices
      // due to the shared alias
      .filter(aliasMetadataPerIndex -> filterProcessInstanceIndexAliases(
        aliasMetadataPerIndex.getValue(),
        eventBased
      ))
      .flatMap(aliasMetadataPerIndex -> aliasMetadataPerIndex.getValue().stream())
      .filter(fullAliasName -> fullAliasName.contains(prefix))
      .map(fullIndexName -> fullIndexName.substring(fullIndexName.lastIndexOf(prefix) + prefix.length()))
      .toList();
  }

  private boolean filterProcessInstanceIndexAliases(final Set<String> aliasMetadataSet,
                                                    final boolean eventBased) {
    if (eventBased) {
      return aliasMetadataSet.stream()
        .anyMatch(aliasMetadata -> aliasMetadata.contains(EVENT_PROCESS_INSTANCE_INDEX_PREFIX));
    } else {
      return aliasMetadataSet.stream()
        .noneMatch(aliasMetadata -> aliasMetadata.contains(EVENT_PROCESS_INSTANCE_INDEX_PREFIX));
    }
  }

  private List<? extends DecisionInstanceIndex<?>> retrieveAllDecisionInstanceIndices() {
    return retrieveAllDynamicIndexKeysForPrefix(DECISION_INSTANCE_INDEX_PREFIX)
      .stream()
      .map(key -> dbClient instanceof OptimizeElasticsearchClient ?
        new DecisionInstanceIndexES(key) :
        null)// TODO Not implemented for OpenSearch yet, to be done with OPT-7349
      .toList();
  }

  private List<? extends CamundaActivityEventIndex<?>> retrieveAllCamundaActivityEventIndices() {
    return retrieveAllDynamicIndexKeysForPrefix(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX)
      .stream()
      .map(key -> dbClient instanceof OptimizeElasticsearchClient ?
        new CamundaActivityEventIndexES(key) :
        null)// TODO Not implemented for OpenSearch yet, to be done with OPT-7349
      .toList();
  }

  private List<? extends EventSequenceCountIndex<?>> retrieveAllSequenceCountIndices() {
    return retrieveAllDynamicIndexKeysForPrefix(EVENT_SEQUENCE_COUNT_INDEX_PREFIX)
      .stream()
      .map(key -> dbClient instanceof OptimizeElasticsearchClient ?
        new EventSequenceCountIndexES(key) :
        null)// TODO Not implemented for OpenSearch yet, to be done with OPT-7349
      .toList();
  }

  private List<? extends EventTraceStateIndex<?>> retrieveAllEventTraceIndices() {
    return retrieveAllDynamicIndexKeysForPrefix(EVENT_TRACE_STATE_INDEX_PREFIX)
      .stream()
      .map(key -> dbClient instanceof OptimizeElasticsearchClient ?
        new EventTraceStateIndexES(key) :
        null)// TODO Not implemented for OpenSearch yet, to be done with OPT-7349
      .toList();
  }

  private List<? extends ProcessInstanceIndex<?>> retrieveAllProcessInstanceIndices() {
    return retrieveProcessInstanceIndexIdentifiers(false)
      .stream()
      .map(key -> dbClient instanceof OptimizeElasticsearchClient ?
        new ProcessInstanceIndexES(key) :
        null)// TODO Not implemented for OpenSearch yet, to be done with OPT-7349
      .toList();
  }

  private List<String> retrieveAllDynamicIndexKeysForPrefix(final String dynamicIndexPrefix) {
    try {
      return dbClient.getAllIndicesForAlias(dynamicIndexPrefix + "*")
        .stream()
        .map(fullAliasName ->
               fullAliasName.substring(fullAliasName.lastIndexOf(dynamicIndexPrefix) + dynamicIndexPrefix.length()))
        .toList();
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed retrieving aliases for dynamic index prefix " + dynamicIndexPrefix, e);
    }
  }

}
