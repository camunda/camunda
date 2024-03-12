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
package io.camunda.operate.schema.opensearch;

import static io.camunda.operate.util.CollectionUtil.map;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.conditions.OpensearchCondition;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpensearchCondition.class)
@Scope(SCOPE_PROTOTYPE)
public class OpensearchPipelineReindexPlan extends PipelineReindexPlan implements ReindexPlan {

  private final RichOpenSearchClient richOpenSearchClient;
  private final MigrationProperties migrationProperties;
  private Script script;

  @Autowired
  public OpensearchPipelineReindexPlan(
      final RichOpenSearchClient richOpenSearchClient,
      final MigrationProperties migrationProperties) {
    super();
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
