/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.archiver;

import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * This archiver job will delete all data related with process instances after they are finished:
 * flow node instances, "runtime" variables, process instances.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class ProcessInstanceArchiverJob extends AbstractArchiverJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessInstanceArchiverJob.class);

  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;

  @Autowired private VariableIndex variableIndex;

  @Autowired private ProcessInstanceIndex processInstanceIndex;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private Metrics metrics;

  public ProcessInstanceArchiverJob(final List<Integer> partitionIds) {
    super(partitionIds);
  }

  @Override
  public CompletableFuture<Integer> archiveBatch(ArchiveBatch archiveBatch) {
    final CompletableFuture<Integer> archiveBatchFuture;
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
          .thenAccept((v) -> archiveBatchFuture.complete(archiveBatch.getIds().size()))
          .exceptionally(
              (t) -> {
                archiveBatchFuture.completeExceptionally(t);
                return null;
              });
    } else {
      LOGGER.debug("Nothing to archive");
      archiveBatchFuture = CompletableFuture.completedFuture(0);
    }

    return archiveBatchFuture;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    final var nextBatchFuture = new CompletableFuture<ArchiveBatch>();
    final var searchRequest = createFinishedProcessInstanceSearchRequest();
    searchRequest.scroll(TimeValue.timeValueMillis(ElasticsearchUtil.SCROLL_KEEP_ALIVE_MS));

    final var startTimer = Timer.start();
    sendSearchRequest(searchRequest)
        .thenCompose(this::fetchFinishedProcessInstanceIds)
        .thenApply(this::createArchiveBatch)
        .thenAccept(nextBatchFuture::complete)
        .exceptionally(
            (t) -> {
              final var message =
                  String.format(
                      "Exception occurred, while obtaining finished batch operations: %s",
                      t.getMessage());
              nextBatchFuture.completeExceptionally(new TasklistRuntimeException(message, t));
              return null;
            })
        .thenAccept(
            (ignore) -> {
              final var timer = getArchiverQueryTimer();
              startTimer.stop(timer);
            });

    return nextBatchFuture;
  }

  protected CompletableFuture<List<String>> fetchFinishedProcessInstanceIds(
      final SearchResponse response) {
    final CompletableFuture<List<String>> srcollFuture;
    final var result = new ArrayList<String>();

    final var scrollId = response.getScrollId();
    final var hits = response.getHits();

    if (hits.getHits().length > 0) {
      srcollFuture = new CompletableFuture<>();
      final var ids =
          Arrays.asList(hits.getHits()).stream().map(SearchHit::getId).collect(Collectors.toList());
      result.addAll(ids);

      final var scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueMillis(ElasticsearchUtil.SCROLL_KEEP_ALIVE_MS));

      ElasticsearchUtil.scrollAsync(scrollRequest, archiverExecutor, esClient)
          .thenCompose(this::fetchFinishedProcessInstanceIds)
          .thenAccept(
              (resultingIds) -> {
                result.addAll(resultingIds);
                srcollFuture.complete(result);
              })
          .exceptionally(
              (e) -> {
                srcollFuture.completeExceptionally(e);
                return null;
              });

    } else {
      srcollFuture = CompletableFuture.completedFuture(result);
    }

    return srcollFuture;
  }

  protected ArchiveBatch createArchiveBatch(final List<String> ids) {
    if (ids != null && ids.size() > 0) {
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
    final ConstantScoreQueryBuilder q = constantScoreQuery(joinWithAnd(endDateQ, partitionQ));

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
