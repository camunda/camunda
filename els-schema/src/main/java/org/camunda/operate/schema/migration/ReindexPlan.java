/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.migration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.operate.util.CollectionUtil.*;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.elasticsearch.action.ingest.DeletePipelineRequest;
import org.elasticsearch.action.ingest.PutPipelineRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.bytes.ByteBufferReference;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
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

  private final List<Step> steps;
  private Script script;
  private final String srcIndex;
  private final String dstIndex;
 
  private final static String DEFAULT_SCRIPT = "ctx._index = params.dstIndex+'_' + (ctx._index.substring(ctx._index.indexOf('_') + 1, ctx._index.length()))";

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
  public void executeOn(final RestHighLevelClient esClient) {
    final ReindexRequest reindexRequest = new ReindexRequest()
          .setSourceIndices(srcIndex + "_*")
          .setDestIndex(dstIndex + "_");

    final Optional<String> pipelineName = createPipelineFromSteps(esClient);

    if(pipelineName.isPresent()) {
      reindexRequest.setDestPipeline(pipelineName.get());
    }
    if (script != null) {
      reindexRequest.setScript(script);
    }

    BulkByScrollResponse response;
    try {
      response = esClient.reindex(reindexRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      throw new OperateRuntimeException(String.format("Reindex from %s to %s failed: ", srcIndex, dstIndex), e);
    }finally {
      if (pipelineName.isPresent()) {
        deletePipeline(esClient, pipelineName.get());
      }
    }
      
    if(response.getBulkFailures().isEmpty()) {
       logger.info("Reindexed {} documents from {} to {}", response.getStatus().getSuccessfullyProcessed(), srcIndex, dstIndex);
    }else {
       throw new OperateRuntimeException(String.format("Reindex from %s to %s : %s failures", srcIndex, dstIndex, response.getBulkFailures().size()));
    }
  }

  private boolean deletePipeline(final RestHighLevelClient esClient,final String pipelineName) {
    try {
      return esClient.ingest()
          .deletePipeline(new DeletePipelineRequest(pipelineName), RequestOptions.DEFAULT)
          .isAcknowledged();
    } catch (IOException e) {
      logger.error("Delete pipeline '{}' failed: ", pipelineName, e);
    }
    return false;
  }

  private Optional<String> createPipelineFromSteps(final RestHighLevelClient esClient) {
    if (steps.isEmpty()) {
      return Optional.empty();
    }
    final String pipelineDefinition = getPipelineDefinition();
    final BytesReference content = new ByteBufferReference(ByteBuffer.wrap(pipelineDefinition.getBytes()));
    
    String pipelineName = srcIndex + "-to-" + dstIndex + "-pipeline";
    AcknowledgedResponse response;
    try {
       response = esClient.ingest()
            .putPipeline(new PutPipelineRequest(pipelineName, content, XContentType.JSON),RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OperateRuntimeException(String.format("Create pipeline '%s' failed: ", pipelineName), e);
    }
    
    if (response.isAcknowledged()) {
        return Optional.of(pipelineName);
    } else {
      throw new OperateRuntimeException(String.format("Create pipeline '%s' wasn't acknowledged.", pipelineName));
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
