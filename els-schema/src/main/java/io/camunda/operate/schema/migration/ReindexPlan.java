/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.camunda.operate.util.CollectionUtil.*;

import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.es.RetryElasticsearchClient;
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
public class ReindexPlan implements Plan {

  private static final Logger logger = LoggerFactory.getLogger(ReindexPlan.class);

  private List<Step> steps = List.of();
  private Script script;
  private String srcIndex;
  private String dstIndex;

  private int reindexBatchSize = 1_000; // 10_000 maximum
  private int slices;

  public Script getScript() {
    return script;
  }

  public ReindexPlan buildScript(Script script) {
    this.script = script;
    return this;
  }

  public String getSrcIndex() {
    return srcIndex;
  }

  public String getDstIndex() {
    return dstIndex;
  }

  public ReindexPlan setSrcIndex(String srcIndex) {
    this.srcIndex = srcIndex;
    return this;
  }

  public ReindexPlan setDstIndex(String dstIndex) {
    this.dstIndex = dstIndex;
    return this;
  }

  public ReindexPlan buildScript(final String scriptContent, final Map<String,Object> params) {
    script = new Script(ScriptType.INLINE, "painless", scriptContent, params);
    return this;
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  public ReindexPlan setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }

  @Override
  public void executeOn(final RetryElasticsearchClient retryElasticsearchClient) throws MigrationException {
    final ReindexRequest reindexRequest = new ReindexRequest()
          .setSourceIndices(srcIndex + "_*")
          .setDestIndex(dstIndex + "_")
          .setSlices(slices) // Useful if there are more than 1 shard per index
          .setSourceBatchSize(reindexBatchSize);

    final Optional<String> pipelineName = createPipelineFromSteps(retryElasticsearchClient);

    pipelineName.ifPresent(reindexRequest::setDestPipeline);
    if (script == null) {
      buildScript(PRESERVE_INDEX_SUFFIX_SCRIPT, Map.of("dstIndex", dstIndex));
    }
    reindexRequest.setScript(script);

    try {
      retryElasticsearchClient.reindex(reindexRequest);
    } finally {
      pipelineName.ifPresent(retryElasticsearchClient::removePipeline);
    }
  }

  private Optional<String> createPipelineFromSteps(final RetryElasticsearchClient retryElasticsearchClient) throws MigrationException {
    if (steps.isEmpty()) {
      return Optional.empty();
    }
    final String name = srcIndex + "-to-" + dstIndex + "-pipeline";
    boolean added = retryElasticsearchClient.addPipeline(name, getPipelineDefinition());
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
    return "ReindexPlan [steps=" + steps + ",  srcIndex=" + srcIndex + ", dstIndex=" + dstIndex + "]";
  }

  public ReindexPlan setBatchSize(int reindexBatchSize) {
    this.reindexBatchSize = reindexBatchSize;
    return this;
  }

  public ReindexPlan setSlices(int slices) {
    this.slices = slices;
    return this;
  }
}
