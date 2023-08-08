/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration.elasticsearch;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.camunda.operate.util.CollectionUtil.*;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.ReindexPlan;
import io.camunda.operate.schema.migration.Step;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * A plan implemented as reindex request in elasticsearch.<br>
 * Supports script setting.<br>
 * Steps that will be added are elasticsearch ingest processors.<br>
 * The steps will be applied in the order they were added.<br>
 */

@Profile("!opensearch")
@Component
@Scope(SCOPE_PROTOTYPE)
public class ElasticsearchReindexPlan implements ReindexPlan {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchReindexPlan.class);

  private List<Step> steps = List.of();
  private String srcIndex;
  private String dstIndex;

  @Autowired
  private RetryElasticsearchClient retryElasticsearchClient;

  @Autowired
  private MigrationProperties migrationProperties;
  private Script script;

  @Override
  public ReindexPlan setSrcIndex(String srcIndex) {
    this.srcIndex = srcIndex;
    return this;
  }

  @Override
  public ReindexPlan setDstIndex(String dstIndex) {
    this.dstIndex = dstIndex;
    return this;
  }


  @Override
  public ReindexPlan buildScript(final String scriptContent, final Map<String, Object> params) {
    script = new Script(ScriptType.INLINE, "painless", scriptContent, params);
    return this;
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  @Override
  public ReindexPlan setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }

  @Override
  public void executeOn(final SchemaManager schemaManager) throws MigrationException {
    final Optional<String> pipelineName = createPipelineFromSteps(schemaManager);

    final ReindexRequest reindexRequest = new ReindexRequest()
          .setSourceIndices(srcIndex + "_*")
          .setDestIndex(dstIndex + "_")
          .setSlices(migrationProperties.getSlices()) // Useful if there are more than 1 shard per index
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

  private Optional<String> createPipelineFromSteps(final SchemaManager schemaManager) throws MigrationException {
    if (steps.isEmpty()) {
      return Optional.empty();
    }
    final String name = srcIndex + "-to-" + dstIndex + "-pipeline";
    boolean added = schemaManager.addPipeline(name, getPipelineDefinition());
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
    return "ElasticsearchReindexPlan [steps=" + steps + ",  srcIndex=" + srcIndex + ", dstIndex=" + dstIndex + "]";
  }
}
