/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.es.RetryElasticsearchClient;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.util.Tuple;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static io.camunda.operate.schema.templates.IncidentTemplate.KEY;
import static io.camunda.operate.schema.templates.IncidentTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_KEY;
import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

/**
 * This migration plan scrolls the srcIndex, get additional
 * data from list-view index and reindex the batch of source data combining data from
 * source index and list-view.
 */
public class ReindexWithQueryAndScriptPlan implements Plan {

  private static final Logger logger = LoggerFactory.getLogger(ReindexWithQueryAndScriptPlan.class);
  private List<Step> steps = List.of();
  private String srcIndex;
  private String dstIndex;
  private int reindexBatchSize = 1_000; // 10_000 maximum
  private int slices;
  private MigrationProperties migrationProperties;
  private String listViewIndexName;
  private ObjectMapper objectMapper;

  public String getSrcIndex() {
    return srcIndex;
  }

  public String getDstIndex() {
    return dstIndex;
  }

  public ReindexWithQueryAndScriptPlan setSrcIndex(String srcIndex) {
    this.srcIndex = srcIndex;
    return this;
  }

  public ReindexWithQueryAndScriptPlan setDstIndex(String dstIndex) {
    this.dstIndex = dstIndex;
    return this;
  }

  public Script buildScript(final String scriptContent, final Map<String, Tuple<String, String>> bpmnProcessIdsMap)
      throws JsonProcessingException {
    Map<String, Object> paramsMap = Map.of("dstIndex", dstIndex, "bpmnProcessIds", bpmnProcessIdsMap);
    Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(paramsMap), HashMap.class);
    return new Script(ScriptType.INLINE, "painless", scriptContent, jsonMap);
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  public ReindexWithQueryAndScriptPlan setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }

  public MigrationProperties getMigrationProperties() {
    return migrationProperties;
  }

  public ReindexWithQueryAndScriptPlan setMigrationProperties(MigrationProperties migrationProperties) {
    this.migrationProperties = migrationProperties;
    return this;
  }

  public String getListViewIndexName() {
    return listViewIndexName;
  }

  public ReindexWithQueryAndScriptPlan setListViewIndexName(String listViewIndexName) {
    this.listViewIndexName = listViewIndexName;
    return this;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public ReindexWithQueryAndScriptPlan setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    return this;
  }

  @Override
  public void executeOn(final RetryElasticsearchClient retryElasticsearchClient) throws MigrationException {
    //iterate over process instance ids
    String processInstanceKeyField = "processInstanceKey";
    SearchRequest searchRequest = new SearchRequest(srcIndex + "_*")
        .source(new SearchSourceBuilder().fetchField(processInstanceKeyField)
            .sort(processInstanceKeyField)
            .size(migrationProperties.getScriptParamsCount()));
    RestHighLevelClient esClient = retryElasticsearchClient.getEsClient();
    Set<Long> processInstanceKeys = new HashSet<>();
    try {
      scroll(searchRequest, rethrowConsumer(hits -> {
        Set<Long> currentProcessInstanceKeys = Arrays.stream(hits.getHits())
            .map(sh -> (Long) sh.getSourceAsMap().get(processInstanceKeyField)).collect(Collectors.toSet());
        if (processInstanceKeys.size() + currentProcessInstanceKeys.size() >= migrationProperties.getScriptParamsCount()) {
          int remainingSize = migrationProperties.getScriptParamsCount() - processInstanceKeys.size();
          Set<Long> subSet = currentProcessInstanceKeys.stream().limit(remainingSize).collect(Collectors.toSet());
          currentProcessInstanceKeys.removeAll(subSet);
          processInstanceKeys.addAll(subSet);

          reindexPart(retryElasticsearchClient, esClient, processInstanceKeys);

          processInstanceKeys.clear();
          processInstanceKeys.addAll(currentProcessInstanceKeys);
        } else {
          processInstanceKeys.addAll(currentProcessInstanceKeys);
        }
      }), esClient, migrationProperties.getScrollKeepAlive());
      //last iteration
      if (processInstanceKeys.size() > 0) {
        reindexPart(retryElasticsearchClient, esClient, processInstanceKeys);
      }
    } catch (Exception e) {
      throw new MigrationException(e.getMessage(), e);
    }
  }

  private void reindexPart(RetryElasticsearchClient retryElasticsearchClient, RestHighLevelClient esClient,
      Set<Long> processInstanceKeys) throws MigrationException, JsonProcessingException {
    Map<String, Tuple<String, String>> bpmnProcessIdsMap = getBpmnProcessIds(processInstanceKeys, esClient);
    logger.debug("Migrate srcIndex: {}, processInstanceKeys: {}, bpmnProcessIdsMap: {}", srcIndex, processInstanceKeys, bpmnProcessIdsMap);

    final ReindexRequest reindexRequest = new ReindexRequest().setSourceIndices(srcIndex + "_*").setDestIndex(dstIndex + "_").setSlices(slices) // Useful if there are more than 1 shard per index
        .setSourceQuery(termsQuery(PROCESS_INSTANCE_KEY, processInstanceKeys)).setSourceBatchSize(reindexBatchSize);

    //create script
    String content = steps.get(0).getContent();     //we checked before that only one step is present
    reindexRequest.setScript(buildScript(PRESERVE_INDEX_SUFFIX_SCRIPT + content, bpmnProcessIdsMap));

    retryElasticsearchClient.reindex(reindexRequest, false);
  }

  @Override
  public void validateMigrationResults(final RetryElasticsearchClient retryElasticsearchClient)
      throws MigrationException {
    long srcCount = retryElasticsearchClient.getNumberOfDocumentsFor(srcIndex + "_*");
    long dstCount = retryElasticsearchClient.getNumberOfDocumentsFor(dstIndex + "_*");
    if (srcCount != dstCount) {
      throw new MigrationException(String.format(
          "Exception occurred when migrating %s. Number of documents in source indices: %s, number of documents in destination indices: %s",
          srcIndex, srcCount, dstCount));
    }
  }

  private Map<String, Tuple<String, String>> getBpmnProcessIds(Set<Long> processInstanceKeys, RestHighLevelClient esClient) throws MigrationException{
    SearchRequest searchRequest = new SearchRequest(listViewIndexName + "*").source(
        new SearchSourceBuilder().query(termsQuery(KEY, processInstanceKeys))
            .fetchSource(new String[]{KEY, BPMN_PROCESS_ID, PROCESS_KEY}, null).size(migrationProperties.getScriptParamsCount()));
    try {
      Map<String, Tuple<String, String>> result = new HashMap<>();
      scroll(searchRequest, hits -> {
        Arrays.stream(hits.getHits()).forEach(sh -> {
          Map<String, Object> sourceAsMap = sh.getSourceAsMap();
          result.put(String.valueOf(sourceAsMap.get(KEY)),
              new Tuple<>((String) sourceAsMap.get(BPMN_PROCESS_ID), String.valueOf(sourceAsMap.get(PROCESS_KEY))));
        });
      }, esClient, migrationProperties.getScrollKeepAlive());
      return result;
    } catch (IOException e) {
      throw new MigrationException(e.getMessage(), e);
    }
  }

  @Override
  public String toString() {
    return "ReindexWithQueryAndScriptPlan [steps=" + steps + ",  srcIndex=" + srcIndex + ", dstIndex=" + dstIndex + "]";
  }

  public ReindexWithQueryAndScriptPlan setBatchSize(int reindexBatchSize) {
    this.reindexBatchSize = reindexBatchSize;
    return this;
  }

  public ReindexWithQueryAndScriptPlan setSlices(int slices) {
    this.slices = slices;
    return this;
  }
}
