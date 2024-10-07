/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.migration.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PROCESS_KEY;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.ReindexWithQueryAndScriptPlan;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.util.Tuple;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * This migration plan scrolls the srcIndex, get additional data from list-view index and reindex
 * the batch of source data combining data from source index and list-view.
 */
public class ElasticsearchReindexWithQueryAndScriptPlan implements ReindexWithQueryAndScriptPlan {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchReindexWithQueryAndScriptPlan.class);
  private List<Step> steps = List.of();
  private String srcIndex;
  private String dstIndex;

  private final MigrationProperties migrationProperties;
  private String listViewIndexName;

  private final ObjectMapper objectMapper;

  private final RestHighLevelClient esClient;

  private final RetryElasticsearchClient retryElasticsearchClient;

  public ElasticsearchReindexWithQueryAndScriptPlan(
      final MigrationProperties migrationProperties,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper,
      final RestHighLevelClient esClient,
      final RetryElasticsearchClient retryElasticsearchClient) {
    this.migrationProperties = migrationProperties;
    this.objectMapper = objectMapper;
    this.esClient = esClient;
    this.retryElasticsearchClient = retryElasticsearchClient;
  }

  @Override
  public ReindexWithQueryAndScriptPlan setSrcIndex(final String srcIndex) {
    this.srcIndex = srcIndex;
    return this;
  }

  @Override
  public ReindexWithQueryAndScriptPlan setDstIndex(final String dstIndex) {
    this.dstIndex = dstIndex;
    return this;
  }

  @Override
  public ReindexWithQueryAndScriptPlan setSteps(final List<Step> steps) {
    this.steps = steps;
    return this;
  }

  @Override
  public ReindexWithQueryAndScriptPlan setListViewIndexName(final String listViewIndexName) {
    this.listViewIndexName = listViewIndexName;
    return this;
  }

  private Script buildScript(
      final String scriptContent, final Map<String, Tuple<String, String>> bpmnProcessIdsMap)
      throws JsonProcessingException {
    final Map<String, Object> paramsMap =
        Map.of("dstIndex", dstIndex, "bpmnProcessIds", bpmnProcessIdsMap);
    final Map<String, Object> jsonMap =
        objectMapper.readValue(objectMapper.writeValueAsString(paramsMap), HashMap.class);
    return new Script(ScriptType.INLINE, "painless", scriptContent, jsonMap);
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  @Override
  public void executeOn(final SchemaManager schemaManager) throws MigrationException {
    // iterate over process instance ids
    final String processInstanceKeyField = "processInstanceKey";
    final SearchRequest searchRequest =
        new SearchRequest(srcIndex + "_*")
            .source(
                new SearchSourceBuilder()
                    .fetchField(processInstanceKeyField)
                    .sort(processInstanceKeyField)
                    .size(migrationProperties.getScriptParamsCount()));
    final Set<Long> processInstanceKeys = new HashSet<>();
    try {
      scroll(
          searchRequest,
          rethrowConsumer(
              hits -> {
                final Set<Long> currentProcessInstanceKeys =
                    Arrays.stream(hits.getHits())
                        .map(sh -> (Long) sh.getSourceAsMap().get(processInstanceKeyField))
                        .collect(Collectors.toSet());
                if (processInstanceKeys.size() + currentProcessInstanceKeys.size()
                    >= migrationProperties.getScriptParamsCount()) {
                  final int remainingSize =
                      migrationProperties.getScriptParamsCount() - processInstanceKeys.size();
                  final Set<Long> subSet =
                      currentProcessInstanceKeys.stream()
                          .limit(remainingSize)
                          .collect(Collectors.toSet());
                  currentProcessInstanceKeys.removeAll(subSet);
                  processInstanceKeys.addAll(subSet);

                  reindexPart(esClient, processInstanceKeys);

                  processInstanceKeys.clear();
                  processInstanceKeys.addAll(currentProcessInstanceKeys);
                } else {
                  processInstanceKeys.addAll(currentProcessInstanceKeys);
                }
              }),
          esClient,
          migrationProperties.getScrollKeepAlive());
      // last iteration
      if (processInstanceKeys.size() > 0) {
        reindexPart(esClient, processInstanceKeys);
      }
    } catch (final Exception e) {
      throw new MigrationException(e.getMessage(), e);
    }
  }

  @Override
  public void validateMigrationResults(final SchemaManager schemaManager)
      throws MigrationException {
    final long srcCount = schemaManager.getNumberOfDocumentsFor(srcIndex + "_*");
    final long dstCount = schemaManager.getNumberOfDocumentsFor(dstIndex + "_*");
    if (srcCount != dstCount) {
      throw new MigrationException(
          String.format(
              "Exception occurred when migrating %s. Number of documents in source indices: %s, number of documents in destination indices: %s",
              srcIndex, srcCount, dstCount));
    }
  }

  private void reindexPart(final RestHighLevelClient esClient, final Set<Long> processInstanceKeys)
      throws MigrationException, JsonProcessingException {
    final Map<String, Tuple<String, String>> bpmnProcessIdsMap =
        getBpmnProcessIds(processInstanceKeys, esClient);
    LOGGER.debug(
        "Migrate srcIndex: {}, processInstanceKeys: {}, bpmnProcessIdsMap: {}",
        srcIndex,
        processInstanceKeys,
        bpmnProcessIdsMap);

    final ReindexRequest reindexRequest =
        new ReindexRequest()
            .setSourceIndices(srcIndex + "_*")
            .setDestIndex(dstIndex + "_")
            .setSlices(
                migrationProperties.getSlices()) // Useful if there are more than 1 shard per index
            .setSourceQuery(termsQuery(PROCESS_INSTANCE_KEY, processInstanceKeys))
            .setSourceBatchSize(migrationProperties.getReindexBatchSize());

    // create script
    final String content =
        steps.get(0).getContent(); // we checked before that only one step is present
    reindexRequest.setScript(
        buildScript(PRESERVE_INDEX_SUFFIX_SCRIPT + content, bpmnProcessIdsMap));

    retryElasticsearchClient.reindex(reindexRequest, false);
  }

  private Map<String, Tuple<String, String>> getBpmnProcessIds(
      final Set<Long> processInstanceKeys, final RestHighLevelClient esClient)
      throws MigrationException {
    final SearchRequest searchRequest =
        new SearchRequest(listViewIndexName + "*")
            .source(
                new SearchSourceBuilder()
                    .query(termsQuery(KEY, processInstanceKeys))
                    .fetchSource(new String[] {KEY, BPMN_PROCESS_ID, PROCESS_KEY}, null)
                    .size(migrationProperties.getScriptParamsCount()));
    try {
      final Map<String, Tuple<String, String>> result = new HashMap<>();
      scroll(
          searchRequest,
          hits -> {
            Arrays.stream(hits.getHits())
                .forEach(
                    sh -> {
                      final Map<String, Object> sourceAsMap = sh.getSourceAsMap();
                      result.put(
                          String.valueOf(sourceAsMap.get(KEY)),
                          new Tuple<>(
                              (String) sourceAsMap.get(BPMN_PROCESS_ID),
                              String.valueOf(sourceAsMap.get(PROCESS_KEY))));
                    });
          },
          esClient,
          migrationProperties.getScrollKeepAlive());
      return result;
    } catch (final IOException e) {
      throw new MigrationException(e.getMessage(), e);
    }
  }

  @Override
  public String toString() {
    return "ElasticsearchReindexWithQueryAndScriptPlan [steps="
        + steps
        + ",  srcIndex="
        + srcIndex
        + ", dstIndex="
        + dstIndex
        + "]";
  }
}
