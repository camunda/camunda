/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.opensearch;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.*;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PROCESS_KEY;

import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.ReindexWithQueryAndScriptPlan;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.Tuple;
import java.util.*;
import java.util.stream.Collectors;
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

public class OpensearchReindexWithQueryAndScriptPlan implements ReindexWithQueryAndScriptPlan {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpensearchReindexWithQueryAndScriptPlan.class);
  private final MigrationProperties migrationProperties;
  private final RichOpenSearchClient richOpenSearchClient;
  private List<Step> steps = List.of();
  private String srcIndex;
  private String dstIndex;
  private String listViewIndexName;

  public OpensearchReindexWithQueryAndScriptPlan(
      final RichOpenSearchClient richOpenSearchClient,
      final MigrationProperties migrationProperties) {
    this.richOpenSearchClient = richOpenSearchClient;
    this.migrationProperties = migrationProperties;
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
      final String scriptContent, final Map<String, Tuple<String, String>> bpmnProcessIdsMap) {
    final Map<String, JsonData> paramsMap =
        Map.of("dstIndex", JsonData.of(dstIndex), "bpmnProcessIds", JsonData.of(bpmnProcessIdsMap));
    return new Script.Builder()
        .inline(InlineScript.of(s -> s.lang("painless").source(scriptContent).params(paramsMap)))
        .build();
  }

  private Map<String, Tuple<String, String>> getBpmnProcessIds(
      final Set<Long> processInstanceKeys) {
    final var request =
        searchRequestBuilder(listViewIndexName + "*")
            .query(longTerms(KEY, processInstanceKeys))
            .source(sourceInclude(KEY, BPMN_PROCESS_ID, PROCESS_KEY))
            .size(migrationProperties.getScriptParamsCount());
    record Result(String key, String bpmnProcessId, String processKey) {}
    final Map<String, Tuple<String, String>> results = new HashMap<>();
    richOpenSearchClient
        .doc()
        .scrollWith(
            request,
            Result.class,
            hits ->
                hits.forEach(
                    hit -> {
                      final var source = hit.source();
                      if (source != null) {
                        results.put(
                            source.key(), new Tuple<>(source.bpmnProcessId(), source.processKey()));
                      }
                    }));
    return results;
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  @Override
  public void executeOn(final SchemaManager schemaManager) throws MigrationException {
    // iterate over process instance ids
    final String processInstanceKeyField = "processInstanceKey";
    final var searchRequest =
        searchRequestBuilder(srcIndex + "_*")
            .source(sourceInclude(processInstanceKeyField))
            .sort(sortOptions(processInstanceKeyField, SortOrder.Asc))
            .size(migrationProperties.getScriptParamsCount());
    final Set<Long> processInstanceKeys = new HashSet<>();
    try {
      richOpenSearchClient
          .doc()
          .scrollWith(
              searchRequest,
              Long.class,
              rethrowConsumer(
                  hits -> {
                    final Set<Long> currentProcessInstanceKeys =
                        hits.stream().map(Hit::source).collect(Collectors.toSet());
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

  private void reindexPart(final Set<Long> processInstanceKeys) {
    final Map<String, Tuple<String, String>> bpmnProcessIdsMap =
        getBpmnProcessIds(processInstanceKeys);
    LOGGER.debug(
        "Migrate srcIndex: {}, processInstanceKeys: {}, bpmnProcessIdsMap: {}",
        srcIndex,
        processInstanceKeys,
        bpmnProcessIdsMap);
    final String content = steps.get(0).getContent();
    final var reindexRequest =
        new ReindexRequest.Builder()
            .source(
                Source.of(
                    b ->
                        b.index(srcIndex)
                            .query(longTerms(PROCESS_INSTANCE_KEY, processInstanceKeys))
                            .size(migrationProperties.getReindexBatchSize())))
            .dest(Destination.of(b -> b.index(dstIndex + "_")))
            .script(buildScript(PRESERVE_INDEX_SUFFIX_SCRIPT + content, bpmnProcessIdsMap));
    if (migrationProperties.getSlices() > 0) {
      reindexRequest.slices((long) migrationProperties.getSlices());
    }
    richOpenSearchClient.index().reindexWithRetries(reindexRequest.build(), false);
  }

  @Override
  public String toString() {
    return "OpensearchReindexWithQueryAndScriptPlan [steps="
        + steps
        + ",  srcIndex="
        + srcIndex
        + ", dstIndex="
        + dstIndex
        + "]";
  }
}
