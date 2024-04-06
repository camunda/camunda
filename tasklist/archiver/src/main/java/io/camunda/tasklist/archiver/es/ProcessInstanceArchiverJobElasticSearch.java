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
package io.camunda.tasklist.archiver.es;

import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.archiver.ProcessInstanceArchiverJob;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.micrometer.core.instrument.Timer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * This archiver job will delete all data related with process instances after they are finished:
 * flow node instances, "runtime" variables, process instances.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
@Conditional(ElasticSearchCondition.class)
public class ProcessInstanceArchiverJobElasticSearch extends AbstractArchiverJobElasticSearch
    implements ProcessInstanceArchiverJob {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceArchiverJobElasticSearch.class);

  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;

  @Autowired private VariableIndex variableIndex;

  @Autowired private ProcessInstanceIndex processInstanceIndex;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private Metrics metrics;

  public ProcessInstanceArchiverJobElasticSearch(final List<Integer> partitionIds) {
    super(partitionIds);
  }

  @Override
  public CompletableFuture<Map.Entry<String, Integer>> archiveBatch(ArchiveBatch archiveBatch) {
    final CompletableFuture<Map.Entry<String, Integer>> archiveBatchFuture;
    if (archiveBatch != null) {
      LOGGER.debug("Following batch operations are found for archiving: {}", archiveBatch);
      archiveBatchFuture = new CompletableFuture<>();

      final var deleteVariablesFuture =
          archiverUtil.deleteDocuments(
              variableIndex.getFullQualifiedName(),
              VariableIndex.PROCESS_INSTANCE_ID,
              archiveBatch.getIds());

      final var deleteFlowNodesFuture =
          archiverUtil.deleteDocuments(
              flowNodeInstanceIndex.getFullQualifiedName(),
              FlowNodeInstanceIndex.PROCESS_INSTANCE_ID,
              archiveBatch.getIds());

      final var deleteProcessInstanceFuture =
          archiverUtil.deleteDocuments(
              processInstanceIndex.getFullQualifiedName(),
              ProcessInstanceIndex.ID,
              archiveBatch.getIds());

      CompletableFuture.allOf(
              deleteVariablesFuture, deleteFlowNodesFuture, deleteProcessInstanceFuture)
          .thenAccept(
              (v) ->
                  archiveBatchFuture.complete(
                      Map.entry("PROCESS_INSTANCE_ARCHIVER", archiveBatch.getIds().size())))
          .exceptionally(
              (t) -> {
                archiveBatchFuture.completeExceptionally(t);
                return null;
              });
    } else {
      LOGGER.debug("Nothing to archive");
      archiveBatchFuture = CompletableFuture.completedFuture(Map.entry(NOTHING_TO_ARCHIVE, 0));
    }

    return archiveBatchFuture;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    final var nextBatchFuture = new CompletableFuture<ArchiveBatch>();
    final var searchRequest = createFinishedProcessInstanceSearchRequest();

    final var startTimer = Timer.start();
    sendSearchRequest(searchRequest)
        .whenComplete(
            (response, e) -> {
              final var timer = getArchiverQueryTimer();
              startTimer.stop(timer);

              final var result = handleSearchResponse(response, e);
              result.ifRightOrLeft(
                  nextBatchFuture::complete, nextBatchFuture::completeExceptionally);
            });

    return nextBatchFuture;
  }

  protected Either<Throwable, ArchiveBatch> handleSearchResponse(
      final SearchResponse searchResponse, final Throwable error) {
    if (error != null) {
      final var message =
          String.format(
              "Exception occurred, while obtaining finished process instances: %s",
              error.getMessage());
      return Either.left(new TasklistRuntimeException(message, error));
    }

    final var batch = createArchiveBatch(searchResponse);
    return Either.right(batch);
  }

  protected ArchiveBatch createArchiveBatch(final SearchResponse response) {
    final var hits = response.getHits();
    if (hits.getHits().length > 0) {
      final var ids =
          Arrays.asList(hits.getHits()).stream().map(SearchHit::getId).collect(Collectors.toList());
      return new ArchiveBatch(ids);
    } else {
      return null;
    }
  }

  private SearchRequest createFinishedProcessInstanceSearchRequest() {
    final QueryBuilder endDateQ =
        rangeQuery(ProcessInstanceIndex.END_DATE)
            .lte(tasklistProperties.getArchiver().getArchivingTimepoint());
    final TermsQueryBuilder partitionQ = termsQuery(TaskTemplate.PARTITION_ID, getPartitionIds());
    final ConstantScoreQueryBuilder q =
        constantScoreQuery(ElasticsearchUtil.joinWithAnd(endDateQ, partitionQ));

    final SearchRequest searchRequest =
        new SearchRequest(processInstanceIndex.getFullQualifiedName())
            .source(
                new SearchSourceBuilder()
                    .query(q)
                    .fetchSource(false)
                    .size(tasklistProperties.getArchiver().getRolloverBatchSize())
                    .sort(ProcessInstanceIndex.END_DATE, SortOrder.ASC))
            .requestCache(false); // we don't need to cache this, as each time we need new data

    LOGGER.debug("Query finished process instances for archiving request: \n{}", q.toString());
    return searchRequest;
  }

  private Timer getArchiverQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
  }
}
