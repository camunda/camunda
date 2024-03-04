/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.schema.migration.elasticsearch;

import static io.camunda.operate.util.CollectionUtil.*;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.conditions.ElasticsearchCondition;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * A plan implemented as reindex request in elasticsearch.<br>
 * Supports script setting.<br>
 * Steps that will be added are elasticsearch ingest processors.<br>
 * The steps will be applied in the order they were added.<br>
 */
@Component
@Conditional(ElasticsearchCondition.class)
@Scope(SCOPE_PROTOTYPE)
public class ElasticsearchPipelineReindexPlan extends PipelineReindexPlan implements ReindexPlan {

  private final RetryElasticsearchClient retryElasticsearchClient;

  private final MigrationProperties migrationProperties;
  private Script script;

  @Autowired
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
