/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import io.camunda.operate.Metrics;
import io.camunda.operate.archiver.util.Either;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.property.OperateProperties;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
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
import io.micrometer.core.instrument.Timer;

import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.dateHistogram;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;
import static org.elasticsearch.search.aggregations.PipelineAggregatorBuilders.bucketSort;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Component
@Scope(SCOPE_PROTOTYPE)
public class BatchOperationArchiverJob extends AbstractArchiverJob {

  private static final Logger logger = LoggerFactory.getLogger(BatchOperationArchiverJob.class);

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private Metrics metrics;

  @Override
  public CompletableFuture<Integer> archiveBatch(ArchiveBatch archiveBatch) {
    final CompletableFuture<Integer> archiveBatchFuture;
    final Archiver archiver = beanFactory.getBean(Archiver.class);

    if (archiveBatch != null) {
      logger.debug("Following batch operations are found for archiving: {}", archiveBatch);

      archiveBatchFuture = new CompletableFuture<>();
      archiver.moveDocuments(batchOperationTemplate.getFullQualifiedName(),
          BatchOperationTemplate.ID,
          archiveBatch.getFinishDate(),
          archiveBatch.getIds())
        .whenComplete((v, e) -> {
          if (e != null) {
            archiveBatchFuture.completeExceptionally(e);
            return;
          }
          archiveBatchFuture.complete(archiveBatch.getIds().size());
        });
    } else {
      logger.debug("Nothing to archive");
      archiveBatchFuture = CompletableFuture.completedFuture(0);
    }

    return archiveBatchFuture;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    final var batchFuture = new CompletableFuture<ArchiveBatch>();
    final var aggregation = createFinishedBatchOperationsAggregation(DATES_AGG, INSTANCES_AGG);
    final var searchRequest = createFinishedBatchOperationsSearchRequest(aggregation);

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

  private SearchRequest createFinishedBatchOperationsSearchRequest(AggregationBuilder agg) {
    final QueryBuilder endDateQ = rangeQuery(BatchOperationTemplate.END_DATE).lte(operateProperties.getArchiver().getArchivingTimepoint());
    final ConstantScoreQueryBuilder q = constantScoreQuery(endDateQ);

    final SearchRequest searchRequest = new SearchRequest(batchOperationTemplate.getFullQualifiedName())
        .source(new SearchSourceBuilder()
            .query(q)
            .aggregation(agg)
            .fetchSource(false)
            .size(0)
            .sort(BatchOperationTemplate.END_DATE, SortOrder.ASC))
        .requestCache(false);  //we don't need to cache this, as each time we need new data

    logger.debug("Finished batch operations for archiving request: \n{}\n and aggregation: \n{}", q.toString(), agg.toString());
    return searchRequest;
  }

  private AggregationBuilder createFinishedBatchOperationsAggregation(String datesAggName, String instancesAggName) {
    return dateHistogram(datesAggName)
        .field(BatchOperationTemplate.END_DATE)
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
                .sort(BatchOperationTemplate.ID, SortOrder.ASC)
                .fetchSource(BatchOperationTemplate.ID, null)
        );
  }

  private Either<Throwable, ArchiveBatch> handleSearchResponse(final SearchResponse searchResponse, final Throwable error) {
    if (error != null) {
      final var message = String.format("Exception occurred, while obtaining finished batch operations: %s", error.getMessage());
      return Either.left(new OperateRuntimeException(message, error));
    }

    final var batch = createArchiveBatch(searchResponse, DATES_AGG, INSTANCES_AGG);
    return Either.right(batch);
  }

  private Timer getArchiverQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
  }

}
