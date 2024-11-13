/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.migration.es;

import static io.camunda.tasklist.util.CollectionUtil.map;

import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.exceptions.MigrationException;
import io.camunda.tasklist.schema.v86.migration.Step;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A plan implemented as reindex request in elasticsearch.<br>
 * Supports script setting.<br>
 * Steps that will be added are elasticsearch ingest processors.<br>
 * The steps will be applied in the order they were added.<br>
 */
public class ReindexPlanElasticSearch {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReindexPlanElasticSearch.class);

  private static final String DEFAULT_SCRIPT =
      "ctx._index = params.dstIndex+'_' + (ctx._index.substring(ctx._index.indexOf('_') + 1, ctx._index.length()))";

  private List<Step> steps = List.of();
  private Script script;
  private String srcIndex;
  private String dstIndex;

  private int reindexBatchSize = 1_000; // 10_000 maximum
  private int slices;

  public Script getScript() {
    return script;
  }

  public ReindexPlanElasticSearch buildScript(Script script) {
    this.script = script;
    return this;
  }

  public String getSrcIndex() {
    return srcIndex;
  }

  public ReindexPlanElasticSearch setSrcIndex(String srcIndex) {
    this.srcIndex = srcIndex;
    return this;
  }

  public String getDstIndex() {
    return dstIndex;
  }

  public ReindexPlanElasticSearch setDstIndex(String dstIndex) {
    this.dstIndex = dstIndex;
    return this;
  }

  public ReindexPlanElasticSearch buildScript(
      final String scriptContent, final Map<String, Object> params) {
    script = new Script(ScriptType.INLINE, "painless", scriptContent, params);
    return this;
  }

  public List<Step> getSteps() {
    return steps;
  }

  public ReindexPlanElasticSearch setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }

  public void executeOn(final RetryElasticsearchClient retryElasticsearchClient)
      throws MigrationException {
    final ReindexRequest reindexRequest =
        new ReindexRequest()
            .setSourceIndices(srcIndex + "_*")
            .setDestIndex(dstIndex + "_")
            .setSlices(slices) // Useful if there more than 1 shard per index
            .setSourceBatchSize(reindexBatchSize);

    final Optional<String> pipelineName = createPipelineFromSteps(retryElasticsearchClient);

    pipelineName.ifPresent(reindexRequest::setDestPipeline);
    if (script == null) {
      buildScript(DEFAULT_SCRIPT, Map.of("dstIndex", dstIndex));
    }
    reindexRequest.setScript(script);

    try {
      retryElasticsearchClient.reindex(reindexRequest);
    } finally {
      pipelineName.ifPresent(retryElasticsearchClient::removePipeline);
    }
  }

  private Optional<String> createPipelineFromSteps(
      final RetryElasticsearchClient retryElasticsearchClient) throws MigrationException {
    if (steps.isEmpty()) {
      return Optional.empty();
    }
    final String name = srcIndex + "-to-" + dstIndex + "-pipeline";
    final boolean added = retryElasticsearchClient.addPipeline(name, getPipelineDefinition());
    if (added) {
      return Optional.of(name);
    } else {
      throw new MigrationException(String.format("Couldn't create '%s' pipeline.", name));
    }
  }

  private String getPipelineDefinition() {
    final List<String> stepsAsJSON = map(steps, Step::getContent);
    return "{ \"processors\": [" + String.join(", ", stepsAsJSON) + "] }";
  }

  @Override
  public String toString() {
    return "ReindexPlanElasticSearch [steps="
        + steps
        + ",  srcIndex="
        + srcIndex
        + ", dstIndex="
        + dstIndex
        + "]";
  }

  public ReindexPlanElasticSearch setBatchSize(int reindexBatchSize) {
    this.reindexBatchSize = reindexBatchSize;
    return this;
  }

  public ReindexPlanElasticSearch setSlices(int slices) {
    this.slices = slices;
    return this;
  }

  public static ReindexPlanElasticSearch create() {
    return new ReindexPlanElasticSearch();
  }
}
