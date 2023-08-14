/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.archiver.os;

import static io.camunda.tasklist.util.OpenSearchUtil.SCROLL_KEEP_ALIVE_MS;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.archiver.ProcessInstanceArchiverJob;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
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
@Conditional(OpenSearchCondition.class)
public class ProcessInstanceArchiverJobOpenSearch extends AbstractArchiverJobOpenSearch
    implements ProcessInstanceArchiverJob {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceArchiverJobOpenSearch.class);

  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;

  @Autowired private VariableIndex variableIndex;

  @Autowired private ProcessInstanceIndex processInstanceIndex;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private OpenSearchAsyncClient openSearchAsyncClient;

  @Autowired private Metrics metrics;

  public ProcessInstanceArchiverJobOpenSearch(final List<Integer> partitionIds) {
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

    final var startTimer = Timer.start();
    sendSearchRequest(searchRequest)
        .thenCompose(this::fetchFinishedProcessInstanceIds)
        .thenApply(this::createArchiveBatch)
        .thenAccept(nextBatchFuture::complete)
        .exceptionally(
            (exception) -> {
              final var message =
                  String.format(
                      "Exception occurred, while obtaining finished batch operations: %s",
                      exception.getMessage());
              nextBatchFuture.completeExceptionally(
                  new TasklistRuntimeException(message, exception));
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
    final CompletableFuture<List<String>> scrollFuture;
    final var result = new ArrayList<String>();

    final var scrollId = response.scrollId();
    final var hits = response.hits();

    if (hits.hits().size() > 0) {
      scrollFuture = new CompletableFuture<>();
      /*final var ids222 =
      Arrays.asList(hits.getHits()).stream().map(SearchHit::getId).collect(Collectors.toList());*/
      final List<String> ids = new ArrayList<>();
      for (Object hit : hits.hits()) {
        ids.add(((Hit) hit).id());
      }
      result.addAll(ids);

      final var scrollRequest =
          new ScrollRequest.Builder()
              .scrollId(scrollId)
              .scroll(Time.of(t -> t.time(String.valueOf(SCROLL_KEEP_ALIVE_MS))))
              .build();

      OpenSearchUtil.scrollAsync(scrollRequest, archiverExecutor, openSearchAsyncClient)
          .thenCompose(this::fetchFinishedProcessInstanceIds)
          .thenAccept(
              (resultingIds) -> {
                result.addAll(resultingIds);
                scrollFuture.complete(result);
              })
          .exceptionally(
              (e) -> {
                scrollFuture.completeExceptionally(e);
                return null;
              });

    } else {
      scrollFuture = CompletableFuture.completedFuture(result);
    }

    return scrollFuture;
  }

  protected ArchiveBatch createArchiveBatch(final List<String> ids) {
    if (ids != null && ids.size() > 0) {
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
            .scroll(Time.of(t -> t.time(String.valueOf(SCROLL_KEEP_ALIVE_MS))))
            .build();

    LOGGER.debug("Query finished process instances for archiving request: \n{}", q.toString());
    return searchRequest;
  }

  private Timer getArchiverQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
  }
}
