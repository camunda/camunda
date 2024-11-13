/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver.os;

import static io.camunda.tasklist.util.OpenSearchUtil.SCROLL_KEEP_ALIVE_MS;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.archiver.ProcessInstanceArchiverJob;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.v86.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.v86.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.v86.indices.VariableIndex;
import io.camunda.tasklist.schema.v86.templates.TaskTemplate;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * This archiver job will delete all data related with process instances after they are finished:
 * flow node instances, "runtime" variables, process instances.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
@Conditional(OpenSearchCondition.class)
public class ProcessInstanceArchiverJobOpenSearch extends AbstractArchiverJobOpenSearch
    implements ProcessInstanceArchiverJob {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceArchiverJobOpenSearch.class);

  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;

  @Autowired private VariableIndex variableIndex;

  @Autowired private ProcessInstanceIndex processInstanceIndex;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("tasklistOsAsyncClient")
  private OpenSearchAsyncClient openSearchAsyncClient;

  @Autowired private Metrics metrics;

  public ProcessInstanceArchiverJobOpenSearch(final List<Integer> partitionIds) {
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
    final HitsMetadata<?> hits = response.hits();
    if (!hits.hits().isEmpty()) {
      final List<String> ids = hits.hits().stream().map(Hit::id).collect(Collectors.toList());
      return new ArchiveBatch(ids);
    } else {
      return null;
    }
  }

  private SearchRequest createFinishedProcessInstanceSearchRequest() {
    final List<FieldValue> partitions =
        getPartitionIds().stream().map(m -> FieldValue.of(m)).collect(Collectors.toList());
    final Query.Builder endDateQ = new Query.Builder();
    endDateQ.range(
        r ->
            r.field(ProcessInstanceIndex.END_DATE)
                .lte(JsonData.of(tasklistProperties.getArchiver().getArchivingTimepoint())));

    final Query.Builder partitionQ = new Query.Builder();
    partitionQ.terms(
        t -> t.field(TaskTemplate.PARTITION_ID).terms(terms -> terms.value(partitions)));

    final Query q =
        new Query.Builder()
            .constantScore(cs -> cs.filter(OpenSearchUtil.joinWithAnd(endDateQ, partitionQ)))
            .build();

    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .query(q)
            .size(tasklistProperties.getArchiver().getRolloverBatchSize())
            .sort(s -> s.field(f -> f.field(ProcessInstanceIndex.END_DATE).order(SortOrder.Asc)))
            .requestCache(false)
            .scroll(Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS)))
            .build();

    LOGGER.debug("Query finished process instances for archiving request: \n{}", q.toString());
    return searchRequest;
  }

  private Timer getArchiverQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
  }
}
