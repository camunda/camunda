/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.migration.elasticsearch;

import static io.camunda.operate.util.CollectionUtil.*;

import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.PipelineReindexPlan;
import io.camunda.operate.schema.migration.ReindexPlan;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;

/**
 * A plan implemented as reindex request in elasticsearch.<br>
 * Supports script setting.<br>
 * Steps that will be added are elasticsearch ingest processors.<br>
 * The steps will be applied in the order they were added.<br>
 */
public class ElasticsearchPipelineReindexPlan extends PipelineReindexPlan implements ReindexPlan {

  private final RetryElasticsearchClient retryElasticsearchClient;

  private final MigrationProperties migrationProperties;
  private Script script;

  public ElasticsearchPipelineReindexPlan(
      final RetryElasticsearchClient retryElasticsearchClient,
      final MigrationProperties migrationProperties) {
    this.retryElasticsearchClient = retryElasticsearchClient;
    this.migrationProperties = migrationProperties;
  }

  public void buildScript(final String scriptContent, final Map<String, Object> params) {
    script = new Script(ScriptType.INLINE, "painless", scriptContent, params);
  }

  @Override
  public void executeOn(final SchemaManager schemaManager) throws MigrationException {
    final Optional<String> pipelineName = createPipelineFromSteps(schemaManager);

    final ReindexRequest reindexRequest =
        new ReindexRequest()
            .setSourceIndices(srcIndex + "_*")
            .setDestIndex(dstIndex + "_")
            .setSlices(
                migrationProperties.getSlices()) // Useful if there are more than 1 shard per index
            .setSourceBatchSize(migrationProperties.getReindexBatchSize());

    pipelineName.ifPresent(reindexRequest::setDestPipeline);
    if (script == null) {
      buildScript(PRESERVE_INDEX_SUFFIX_SCRIPT, Map.of("dstIndex", dstIndex));
    }
    reindexRequest.setScript(script);
    try {
      retryElasticsearchClient.reindex(reindexRequest);
    } finally {
      pipelineName.ifPresent(schemaManager::removePipeline);
    }
  }

  @Override
  public String getPipelineDefinition() {
    final List<String> stepsAsJSON = map(steps, Step::getContent);
    return "{ \"processors\": [" + String.join(", ", stepsAsJSON) + "] }";
  }

  @Override
  public String toString() {
    return "ElasticsearchReindexPlan [steps="
        + steps
        + ",  srcIndex="
        + srcIndex
        + ", dstIndex="
        + dstIndex
        + "]";
  }
}
