/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.camunda.operate.Metrics;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.Either;
import io.camunda.operate.util.ElasticsearchUtil;
import io.micrometer.core.instrument.Timer;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.dateHistogram;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;
import static org.elasticsearch.search.aggregations.PipelineAggregatorBuilders.bucketSort;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Component
@Scope(SCOPE_PROTOTYPE)
public class ProcessInstancesArchiverJob extends AbstractArchiverJob {

  private static final Logger logger = LoggerFactory.getLogger(ProcessInstancesArchiverJob.class);

  private List<Integer> partitionIds;

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ListViewTemplate processInstanceTemplate;

  @Autowired
  private List<ProcessInstanceDependant> processInstanceDependantTemplates;

  @Autowired
  private Metrics metrics;

  protected ProcessInstancesArchiverJob() {
  }

  public ProcessInstancesArchiverJob(List<Integer> partitionIds) {
    this.partitionIds = partitionIds;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    final var batchFuture = new CompletableFuture<ArchiveBatch>();
    final var aggregation = createFinishedInstancesAggregation(DATES_AGG, INSTANCES_AGG);
    final var searchRequest = createFinishedInstancesSearchRequest(aggregation);

    final var startTimer = Timer.start();
    sendSearchRequest(searchRequest)
      .whenComplete((response, e) -> {
        final var timer = getArchiverQueryTimer();
        startTimer.stop(timer);

        final var result = handleSearchResponse(response, e);
        result.ifRightOrLeft(batchFuture::complete, batchFuture::completeExceptionally);
      });

    return batchFuture;
  }

  private SearchRequest createFinishedInstancesSearchRequest(AggregationBuilder agg) {
    final QueryBuilder endDateQ = rangeQuery(ListViewTemplate.END_DATE).lte(operateProperties.getArchiver().getArchivingTimepoint());
    final TermQueryBuilder isProcessInstanceQ = termQuery(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
    final TermsQueryBuilder partitionQ = termsQuery(ListViewTemplate.PARTITION_ID, partitionIds);
    final ConstantScoreQueryBuilder q = constantScoreQuery(ElasticsearchUtil.joinWithAnd(endDateQ, isProcessInstanceQ, partitionQ));

    final SearchRequest searchRequest = new SearchRequest(processInstanceTemplate.getFullQualifiedName())
        .source(new SearchSourceBuilder()
            .query(q)
            .aggregation(agg)
            .fetchSource(false)
            .size(0)
            .sort(ListViewTemplate.END_DATE, SortOrder.ASC))
        .requestCache(false);  //we don't need to cache this, as each time we need new data

    logger.debug("Finished process instances for archiving request: \n{}\n and aggregation: \n{}", q.toString(), agg.toString());
    return searchRequest;
  }

  private AggregationBuilder createFinishedInstancesAggregation(String datesAggName, String instancesAggName) {
    return dateHistogram(datesAggName)
        .field(ListViewTemplate.END_DATE)
        .calendarInterval(new DateHistogramInterval(operateProperties.getArchiver().getRolloverInterval()))
        .format(operateProperties.getArchiver().getElsRolloverDateFormat())
        .keyed(true)      //get result as a map (not an array)
        //we want to get only one bucket at a time
        .subAggregation(
            bucketSort("datesSortedAgg", Arrays.asList(new FieldSortBuilder("_key")))
                .size(1)
        )
        //we need process instance ids, also taking into account batch size
        .subAggregation(
            topHits(instancesAggName)
                .size(operateProperties.getArchiver().getRolloverBatchSize())
                .sort(ListViewTemplate.ID, SortOrder.ASC)
                .fetchSource(ListViewTemplate.ID, null)
        );
  }

  private Either<Throwable, ArchiveBatch> handleSearchResponse(final SearchResponse searchResponse, final Throwable error) {
    if (error != null) {
      final var message = String.format("Exception occurred, while obtaining finished process instances: %s", error.getMessage());
      return Either.left(new OperateRuntimeException(message, error));
    }

    final var batch = createArchiveBatch(searchResponse, DATES_AGG, INSTANCES_AGG);
    return Either.right(batch);
  }

  private Timer getArchiverQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
  }

  @Override
  public CompletableFuture<Integer> archiveBatch(ProcessInstancesArchiverJob.ArchiveBatch archiveBatch) {
    final CompletableFuture<Integer> archiveBatchFuture;

    if (archiveBatch != null) {
      logger.debug("Following process instances are found for archiving: {}", archiveBatch);

      archiveBatchFuture = new CompletableFuture<Integer>();
      final var finishDate = archiveBatch.getFinishDate();
      final var processInstanceKeys = archiveBatch.getIds();

      moveDependableDocuments(finishDate, processInstanceKeys)
        .thenCompose((v) -> {
          return moveProcessInstanceDocuments(finishDate, processInstanceKeys);
        })
        .thenAccept((i) -> {
          metrics.recordCounts(Metrics.COUNTER_NAME_ARCHIVED, i);
          archiveBatchFuture.complete(i);
        })
        .exceptionally((t) -> {
          archiveBatchFuture.completeExceptionally(t);
          return null;
        });

    } else {
      logger.debug("Nothing to archive");
      archiveBatchFuture = CompletableFuture.completedFuture(0);
    }

    return archiveBatchFuture;
  }

  private CompletableFuture<Void> moveDependableDocuments(final String finishDate, final List<Object> processInstanceKeys) {
    final var dependableFutures = new ArrayList<CompletableFuture<Void>>();
    final Archiver archiver = beanFactory.getBean(Archiver.class);

    for (ProcessInstanceDependant template: processInstanceDependantTemplates) {
      final var moveDocumentsFuture = archiver.moveDocuments(template.getFullQualifiedName(),
          ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
          finishDate,
          processInstanceKeys);
      dependableFutures.add(moveDocumentsFuture);
    }

    return CompletableFuture.allOf(dependableFutures.toArray(new CompletableFuture[dependableFutures.size()]));
  }

  private CompletableFuture<Integer> moveProcessInstanceDocuments(final String finishDate, final List<Object> processInstanceKeys) {
    final var future = new CompletableFuture<Integer>();
    final Archiver archiver = beanFactory.getBean(Archiver.class);

    archiver.moveDocuments(processInstanceTemplate.getFullQualifiedName(),
        ListViewTemplate.PROCESS_INSTANCE_KEY,
        finishDate,
        processInstanceKeys)
      .thenAccept((ignore) -> future.complete(processInstanceKeys.size()))
      .exceptionally((t) -> {
        future.completeExceptionally(t);
        return null;
      });

    return future;
  }

}
