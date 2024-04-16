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
package io.camunda.tasklist.schema.migration.os;

import static io.camunda.tasklist.util.CollectionUtil.map;

import io.camunda.tasklist.exceptions.MigrationException;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import io.camunda.tasklist.schema.migration.Step;
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
