/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.schema.migration.elasticsearch;

import static io.camunda.operate.schema.templates.IncidentTemplate.KEY;
import static io.camunda.operate.schema.templates.IncidentTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_KEY;
import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * This migration plan scrolls the srcIndex, get additional data from list-view index and reindex
 * the batch of source data combining data from source index and list-view.
 */
@Component
@Conditional(ElasticsearchCondition.class)
@Scope(SCOPE_PROTOTYPE)
public class ElasticsearchReindexWithQueryAndScriptPlan implements ReindexWithQueryAndScriptPlan {

  private static final Logger logger =
      LoggerFactory.getLogger(ElasticsearchReindexWithQueryAndScriptPlan.class);
  private List<Step> steps = List.of();
  private String srcIndex;
  private String dstIndex;

  @Autowired private MigrationProperties migrationProperties;
  private String listViewIndexName;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private RetryElasticsearchClient retryElasticsearchClient;

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

  private Script buildScript(
      final String scriptContent, final Map<String, Tuple<String, String>> bpmnProcessIdsMap)
      throws JsonProcessingException {
    Map<String, Object> paramsMap =
        Map.of("dstIndex", dstIndex, "bpmnProcessIds", bpmnProcessIdsMap);
    Map<String, Object> jsonMap =
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
    String processInstanceKeyField = "processInstanceKey";
    SearchRequest searchRequest =
        new SearchRequest(srcIndex + "_*")
            .source(
                new SearchSourceBuilder()
                    .fetchField(processInstanceKeyField)
                    .sort(processInstanceKeyField)
                    .size(migrationProperties.getScriptParamsCount()));
    Set<Long> processInstanceKeys = new HashSet<>();
    try {
      scroll(
          searchRequest,
          rethrowConsumer(
              hits -> {
                Set<Long> currentProcessInstanceKeys =
                    Arrays.stream(hits.getHits())
                        .map(sh -> (Long) sh.getSourceAsMap().get(processInstanceKeyField))
                        .collect(Collectors.toSet());
                if (processInstanceKeys.size() + currentProcessInstanceKeys.size()
                    >= migrationProperties.getScriptParamsCount()) {
                  int remainingSize =
                      migrationProperties.getScriptParamsCount() - processInstanceKeys.size();
                  Set<Long> subSet =
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
    } catch (Exception e) {
      throw new MigrationException(e.getMessage(), e);
    }
  }

  @Override
  public void validateMigrationResults(final SchemaManager schemaManager)
      throws MigrationException {
    long srcCount = schemaManager.getNumberOfDocumentsFor(srcIndex + "_*");
    long dstCount = schemaManager.getNumberOfDocumentsFor(dstIndex + "_*");
    if (srcCount != dstCount) {
      throw new MigrationException(
          String.format(
              "Exception occurred when migrating %s. Number of documents in source indices: %s, number of documents in destination indices: %s",
              srcIndex, srcCount, dstCount));
    }
  }

  private void reindexPart(RestHighLevelClient esClient, Set<Long> processInstanceKeys)
      throws MigrationException, JsonProcessingException {
    Map<String, Tuple<String, String>> bpmnProcessIdsMap =
        getBpmnProcessIds(processInstanceKeys, esClient);
    logger.debug(
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
    String content = steps.get(0).getContent(); // we checked before that only one step is present
    reindexRequest.setScript(
        buildScript(PRESERVE_INDEX_SUFFIX_SCRIPT + content, bpmnProcessIdsMap));

    retryElasticsearchClient.reindex(reindexRequest, false);
  }

  private Map<String, Tuple<String, String>> getBpmnProcessIds(
      Set<Long> processInstanceKeys, RestHighLevelClient esClient) throws MigrationException {
    SearchRequest searchRequest =
        new SearchRequest(listViewIndexName + "*")
            .source(
                new SearchSourceBuilder()
                    .query(termsQuery(KEY, processInstanceKeys))
                    .fetchSource(new String[] {KEY, BPMN_PROCESS_ID, PROCESS_KEY}, null)
                    .size(migrationProperties.getScriptParamsCount()));
    try {
      Map<String, Tuple<String, String>> result = new HashMap<>();
      scroll(
          searchRequest,
          hits -> {
            Arrays.stream(hits.getHits())
                .forEach(
                    sh -> {
                      Map<String, Object> sourceAsMap = sh.getSourceAsMap();
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
    } catch (IOException e) {
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
