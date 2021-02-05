/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.migration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.operate.util.CollectionUtil.*;

import org.camunda.operate.exceptions.MigrationException;
import org.camunda.operate.es.RetryElasticsearchClient;
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

  private static final String DEFAULT_SCRIPT = "ctx._index = params.dstIndex+'_' + (ctx._index.substring(ctx._index.indexOf('_') + 1, ctx._index.length()))";

  private final List<Step> steps;
  private Script script;
  private final String srcIndex;
  private final String dstIndex;

  public ReindexPlan(final String srcIndex,final String dstIndex,final List<Step> steps) {
    this(srcIndex,dstIndex,steps,true);
  }

  public ReindexPlan(final String srcIndex,final String dstIndex,final List<Step> steps,final boolean useDefaultScript) {
    this.srcIndex = srcIndex;
    this.dstIndex = dstIndex;
    this.steps = steps;
    if (useDefaultScript) {
      setScript(DEFAULT_SCRIPT, Map.of("dstIndex", dstIndex));
    }
  }

  public ReindexPlan setScript(final String scriptContent,final Map<String,Object> params) {
    script = new Script(ScriptType.INLINE, "painless", scriptContent, params);
    return this;
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  @Override
  public void executeOn(final RetryElasticsearchClient retryElasticsearchClient) throws MigrationException {
    final ReindexRequest reindexRequest = new ReindexRequest()
          .setSourceIndices(srcIndex + "_*")
          .setDestIndex(dstIndex + "_");

    final Optional<String> pipelineName = createPipelineFromSteps(retryElasticsearchClient);

    pipelineName.ifPresent(reindexRequest::setDestPipeline);
    if (script != null) {
      reindexRequest.setScript(script);
    }

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
}
