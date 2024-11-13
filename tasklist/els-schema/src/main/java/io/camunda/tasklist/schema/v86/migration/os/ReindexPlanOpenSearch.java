/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.migration.os;

import static io.camunda.tasklist.util.CollectionUtil.map;

import io.camunda.tasklist.exceptions.MigrationException;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import io.camunda.tasklist.schema.v86.migration.Step;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A plan implemented as reindex request in opensearch.<br>
 * Supports script setting.<br>
 * Steps that will be added are opensearch ingest processors.<br>
 * The steps will be applied in the order they were added.<br>
 */
public class ReindexPlanOpenSearch {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReindexPlanOpenSearch.class);

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

  public ReindexPlanOpenSearch buildScript(Script script) {
    this.script = script;
    return this;
  }

  public String getSrcIndex() {
    return srcIndex;
  }

  public ReindexPlanOpenSearch setSrcIndex(String srcIndex) {
    this.srcIndex = srcIndex;
    return this;
  }

  public String getDstIndex() {
    return dstIndex;
  }

  public ReindexPlanOpenSearch setDstIndex(String dstIndex) {
    this.dstIndex = dstIndex;
    return this;
  }

  public ReindexPlanOpenSearch buildScript(
      final String scriptContent, final Map<String, JsonData> params) {
    script =
        Script.of(s -> s.inline(is -> is.lang("painless").source(scriptContent).params(params)));
    return this;
  }

  public List<Step> getSteps() {
    return steps;
  }

  public ReindexPlanOpenSearch setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }

  public void executeOn(final RetryOpenSearchClient retryOpenSearchClient)
      throws MigrationException {
    final Optional<String> pipelineName = createPipelineFromSteps(retryOpenSearchClient);
    final var reindexRequestBuilder =
        new org.opensearch.client.opensearch.core.ReindexRequest.Builder()
            .source(s -> s.index(srcIndex + "_*").size(reindexBatchSize))
            .slices((long) slices) // Useful if there more than 1 shard per index
            .waitForCompletion(false)
            .dest(
                d -> {
                  pipelineName.ifPresent(d::pipeline);
                  return d.index(dstIndex + "_");
                });

    if (script == null) {
      buildScript(DEFAULT_SCRIPT, Map.of("dstIndex", JsonData.of(dstIndex)));
    }
    reindexRequestBuilder.script(script);

    try {
      retryOpenSearchClient.reindex(reindexRequestBuilder.build());
    } finally {
      pipelineName.ifPresent(retryOpenSearchClient::removePipeline);
    }
  }

  private Optional<String> createPipelineFromSteps(
      final RetryOpenSearchClient retryOpenSearchClient) throws MigrationException {
    if (steps.isEmpty()) {
      return Optional.empty();
    }
    final String name = srcIndex + "-to-" + dstIndex + "-pipeline";
    final boolean added = retryOpenSearchClient.addPipeline(name, getPipelineDefinitions());
    if (added) {
      return Optional.of(name);
    } else {
      throw new MigrationException(String.format("Couldn't create '%s' pipeline.", name));
    }
  }

  private List<String> getPipelineDefinitions() {
    return map(steps, Step::getContent);
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

  public ReindexPlanOpenSearch setBatchSize(int reindexBatchSize) {
    this.reindexBatchSize = reindexBatchSize;
    return this;
  }

  public ReindexPlanOpenSearch setSlices(int slices) {
    this.slices = slices;
    return this;
  }

  public static ReindexPlanOpenSearch create() {
    return new ReindexPlanOpenSearch();
  }
}
