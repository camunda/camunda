/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.ReindexPlan;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.InlineScript;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.reindex.Destination;
import org.opensearch.client.opensearch.core.reindex.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.camunda.operate.util.CollectionUtil.map;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Component
@Conditional(OpensearchCondition.class)
@Scope(SCOPE_PROTOTYPE)
public class OpensearchReindexPlan implements ReindexPlan {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchReindexPlan.class);

  private List<Step> steps = List.of();
  private String srcIndex;
  private String dstIndex;

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

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
  public ReindexPlan setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }
  @Override
  public List<Step> getSteps() {
    return steps;
  }
  @Override
  public void executeOn(SchemaManager schemaManager) throws MigrationException {
    final Optional<String> pipelineName = createPipelineFromSteps(schemaManager);
    if(script == null){
      buildScript(PRESERVE_INDEX_SUFFIX_SCRIPT, Map.of("dstIndex", dstIndex));
    }
    var reindexRequest = new ReindexRequest.Builder()
        .source(Source.of(b -> b
            .index(srcIndex + "_*")
            .size(migrationProperties.getReindexBatchSize())))
        .dest(Destination.of(b -> {
          b.index(dstIndex + "_");
          pipelineName.ifPresent(b::pipeline);
          return b;
        }));
    if(script!=null){
      reindexRequest.script(script);
    }
    if(migrationProperties.getSlices() > 0) {
      reindexRequest.slices((long) migrationProperties.getSlices());
    }
    try {
      richOpenSearchClient.index().reindexWithRetries(reindexRequest.build());
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

  private void buildScript(String scriptContent, Map<String, Object> params) {
    Map<String, JsonData> paramsMap = new HashMap<>();
    for(var param: params.entrySet()){
      paramsMap.put(param.getKey(), JsonData.of(param.getValue()));
    }
    script = new Script.Builder().inline(InlineScript.of(s ->
        s.lang("painless")
            .source(scriptContent)
            .params(paramsMap))).build();
  }

  @Override
  public String toString() {
    return "OpensearchReindexPlan [steps=" + steps + ",  srcIndex=" + srcIndex + ", dstIndex=" + dstIndex + "]";
  }

}
