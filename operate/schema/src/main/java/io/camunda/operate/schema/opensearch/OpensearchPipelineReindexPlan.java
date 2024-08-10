/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.opensearch;

import static io.camunda.operate.util.CollectionUtil.map;

import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.PipelineReindexPlan;
import io.camunda.operate.schema.migration.ReindexPlan;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.InlineScript;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.reindex.Destination;
import org.opensearch.client.opensearch.core.reindex.Source;

public class OpensearchPipelineReindexPlan extends PipelineReindexPlan implements ReindexPlan {

  private final RichOpenSearchClient richOpenSearchClient;
  private final MigrationProperties migrationProperties;
  private Script script;

  public OpensearchPipelineReindexPlan(
      final RichOpenSearchClient richOpenSearchClient,
      final MigrationProperties migrationProperties) {
    this.richOpenSearchClient = richOpenSearchClient;
    this.migrationProperties = migrationProperties;
  }

  @Override
  public void executeOn(SchemaManager schemaManager) throws MigrationException {
    final Optional<String> pipelineName = createPipelineFromSteps(schemaManager);
    if (script == null) {
      buildScript(PRESERVE_INDEX_SUFFIX_SCRIPT, Map.of("dstIndex", dstIndex));
    }
    final var reindexRequest =
        new ReindexRequest.Builder()
            .source(
                Source.of(
                    b -> b.index(srcIndex + "_*").size(migrationProperties.getReindexBatchSize())))
            .dest(
                Destination.of(
                    b -> {
                      b.index(dstIndex + "_");
                      pipelineName.ifPresent(b::pipeline);
                      return b;
                    }));
    if (script != null) {
      reindexRequest.script(script);
    }
    if (migrationProperties.getSlices() > 0) {
      reindexRequest.slices((long) migrationProperties.getSlices());
    }
    try {
      richOpenSearchClient.index().reindexWithRetries(reindexRequest.build());
    } finally {
      pipelineName.ifPresent(schemaManager::removePipeline);
    }
  }

  @Override
  protected String getPipelineDefinition() {
    final List<String> stepsAsJSON = map(steps, Step::getContent);
    return "{ \"processors\": [" + String.join(", ", stepsAsJSON) + "] }";
  }

  private void buildScript(String scriptContent, Map<String, Object> params) {
    final Map<String, JsonData> paramsMap = new HashMap<>();
    for (var param : params.entrySet()) {
      paramsMap.put(param.getKey(), JsonData.of(param.getValue()));
    }
    script =
        new Script.Builder()
            .inline(
                InlineScript.of(s -> s.lang("painless").source(scriptContent).params(paramsMap)))
            .build();
  }

  @Override
  public String toString() {
    return "OpensearchReindexPlan [steps="
        + steps
        + ",  srcIndex="
        + srcIndex
        + ", dstIndex="
        + dstIndex
        + "]";
  }
}
