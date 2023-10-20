/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.ReindexWithQueryAndScriptPlan;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.Tuple;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.InlineScript;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.reindex.Destination;
import org.opensearch.client.opensearch.core.reindex.Source;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static io.camunda.operate.schema.templates.IncidentTemplate.KEY;
import static io.camunda.operate.schema.templates.IncidentTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_KEY;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.*;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;

@Component
@Conditional(OpensearchCondition.class)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class OpensearchReindexWithQueryAndScriptPlan implements ReindexWithQueryAndScriptPlan {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchReindexWithQueryAndScriptPlan.class);
  private List<Step> steps = List.of();
  private String srcIndex;
  private String dstIndex;

  @Autowired
  private MigrationProperties migrationProperties;
  private String listViewIndexName;

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Override
  public ReindexWithQueryAndScriptPlan setSrcIndex(String srcIndex) {
    this.srcIndex = srcIndex;
    return this;
  }

  @Override
  public ReindexWithQueryAndScriptPlan setDstIndex(String dstIndex) {
    this.dstIndex = dstIndex;
    return this;
  }

  private Script buildScript(final String scriptContent, final Map<String, Tuple<String, String>> bpmnProcessIdsMap) {
    Map<String, JsonData> paramsMap = Map.of("dstIndex", JsonData.of(dstIndex), "bpmnProcessIds", JsonData.of(bpmnProcessIdsMap));
    return new Script.Builder().inline(InlineScript.of( s ->
        s.lang("painless")
            .source(scriptContent)
            .params(paramsMap))).build();
  }

  private Map<String, Tuple<String, String>> getBpmnProcessIds(Set<Long> processInstanceKeys)  {
      var request = searchRequestBuilder(listViewIndexName + "*")
          .query(longTerms(KEY, processInstanceKeys))
          .source(sourceInclude(KEY, BPMN_PROCESS_ID, PROCESS_KEY))
          .size(migrationProperties.getScriptParamsCount());
      record Result(String key,String bpmnProcessId,String processKey){}
      Map<String, Tuple<String, String>> results = new HashMap<>();
      richOpenSearchClient.doc().scrollWith(request, Result.class, hits -> hits.forEach(hit -> {
        var source = hit.source();
        if (source != null) {
          results.put(source.key(), new Tuple<>(source.bpmnProcessId(), source.processKey()));
        }
      }));
      return results;
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  @Override
  public ReindexWithQueryAndScriptPlan setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }

  @Override
  public ReindexWithQueryAndScriptPlan setListViewIndexName(String listViewIndexName) {
    this.listViewIndexName = listViewIndexName;
    return this;
  }

  @Override
  public void executeOn(final SchemaManager schemaManager) throws MigrationException {
    //iterate over process instance ids
    String processInstanceKeyField = "processInstanceKey";
    var searchRequest = searchRequestBuilder(srcIndex + "_*")
        .source(sourceInclude(processInstanceKeyField))
        .sort(sortOptions(processInstanceKeyField, SortOrder.Asc))
        .size(migrationProperties.getScriptParamsCount());
    Set<Long> processInstanceKeys = new HashSet<>();
    try {
    richOpenSearchClient.doc().scrollWith(searchRequest, Long.class, rethrowConsumer( hits -> {
      Set<Long> currentProcessInstanceKeys = hits.stream().map(Hit::source).collect(Collectors.toSet());
      if (processInstanceKeys.size() + currentProcessInstanceKeys.size() >= migrationProperties.getScriptParamsCount()) {
        int remainingSize = migrationProperties.getScriptParamsCount() - processInstanceKeys.size();
        Set<Long> subSet = currentProcessInstanceKeys.stream().limit(remainingSize).collect(Collectors.toSet());
        currentProcessInstanceKeys.removeAll(subSet);
        processInstanceKeys.addAll(subSet);

        reindexPart(processInstanceKeys);

        processInstanceKeys.clear();
        processInstanceKeys.addAll(currentProcessInstanceKeys);
      } else {
        processInstanceKeys.addAll(currentProcessInstanceKeys);
      }
    }));
    if (!processInstanceKeys.isEmpty()) {
      reindexPart(processInstanceKeys);
    }
    } catch (Exception e) {
      throw new MigrationException(e.getMessage(), e);
    }
  }

  private void reindexPart(Set<Long> processInstanceKeys) {
    Map<String, Tuple<String, String>> bpmnProcessIdsMap = getBpmnProcessIds(processInstanceKeys);
    logger.debug("Migrate srcIndex: {}, processInstanceKeys: {}, bpmnProcessIdsMap: {}", srcIndex, processInstanceKeys, bpmnProcessIdsMap);
    String content = steps.get(0).getContent();
    var reindexRequest = new ReindexRequest.Builder()
        .source(Source.of(b -> b
            .index(srcIndex)
            .query(longTerms(PROCESS_INSTANCE_KEY, processInstanceKeys))
            .size(migrationProperties.getReindexBatchSize())))
        .dest(Destination.of(b -> b.index(dstIndex + "_")))
    .script(buildScript(PRESERVE_INDEX_SUFFIX_SCRIPT + content, bpmnProcessIdsMap));
    if( migrationProperties.getSlices() > 0) {
      reindexRequest.slices((long) migrationProperties.getSlices());
    }
    richOpenSearchClient.index().reindexWithRetries(reindexRequest.build(), false);
  }

  @Override
  public void validateMigrationResults(final SchemaManager schemaManager)
      throws MigrationException {
    long srcCount = schemaManager.getNumberOfDocumentsFor(srcIndex + "_*");
    long dstCount = schemaManager.getNumberOfDocumentsFor(dstIndex + "_*");
    if (srcCount != dstCount) {
      throw new MigrationException(String.format(
          "Exception occurred when migrating %s. Number of documents in source indices: %s, number of documents in destination indices: %s",
          srcIndex, srcCount, dstCount));
    }
  }

  @Override
  public String toString() {
    return "OpensearchReindexWithQueryAndScriptPlan [steps=" + steps + ",  srcIndex=" + srcIndex + ", dstIndex=" + dstIndex + "]";
  }
}
