/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.archiver;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import org.camunda.operate.Metrics;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.WorkflowInstanceDependant;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.ReindexException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.zeebe.PartitionHolder;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.reindex.AbstractBulkByScrollRequest;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import io.micrometer.core.annotation.Timed;
import static org.camunda.operate.util.ElasticsearchUtil.INTERNAL_SCROLL_KEEP_ALIVE_MS;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES;

@Component
@DependsOn("schemaManager")
public class Archiver {

  private static final String INDEX_NAME_PATTERN = "%s%s";
  private static final Logger logger = LoggerFactory.getLogger(Archiver.class);

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private List<WorkflowInstanceDependant> workflowInstanceDependantTemplates;

  @Autowired
  private Metrics metrics;

  @Autowired
  private ListViewTemplate workflowInstanceTemplate;

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  private ThreadPoolTaskScheduler archiverExecutor;

  @PostConstruct
  public void startArchiving() {
    if (operateProperties.getArchiver().isRolloverEnabled()) {
      logger.info("INIT: Start archiving data...");

      //split the list of partitionIds to parallelize
      List<Integer> partitionIds = partitionHolder.getPartitionIds();
      int threadsCount = operateProperties.getArchiver().getThreadsCount();
      if (threadsCount > partitionIds.size()) {
        logger.warn("Too many archiver threads are configured, not all of them will be in use. Number of threads: {}, number of partitions to parallelize by: {}",
            threadsCount, partitionIds.size());
      }

      for (int i=0; i < threadsCount; i++) {
        List<Integer> partitionIdsSubset = CollectionUtil.splitAndGetSublist(partitionIds, threadsCount, i);
        if (!partitionIdsSubset.isEmpty()) {
          ArchiverJob archiverJob = beanFactory.getBean(ArchiverJob.class, partitionIdsSubset);
          archiverExecutor.execute(archiverJob);
        }
      }
    }
  }

  @Bean("archiverThreadPoolExecutor")
  public ThreadPoolTaskScheduler getTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(operateProperties.getArchiver().getThreadsCount());
    scheduler.setThreadNamePrefix("archiver_");
    scheduler.initialize();
    return scheduler;
  }


  public int archiveNextBatch(ArchiverJob.ArchiveBatch archiveBatch) throws ReindexException {
    if (archiveBatch != null) {
      logger.debug("Following workflow instances are found for archiving: {}", archiveBatch);
      try {
        //1st remove dependent data
        for (WorkflowInstanceDependant template: workflowInstanceDependantTemplates) {
          moveDocuments(template.getMainIndexName(), WorkflowInstanceDependant.WORKFLOW_INSTANCE_KEY, archiveBatch.getFinishDate(),
              archiveBatch.getWorkflowInstanceKeys());
        }

        //then remove workflow instances themselves
        moveDocuments(workflowInstanceTemplate.getMainIndexName(), ListViewTemplate.WORKFLOW_INSTANCE_KEY, archiveBatch.getFinishDate(),
            archiveBatch.getWorkflowInstanceKeys());
        metrics.recordCounts(Metrics.COUNTER_NAME_ARCHIVED, archiveBatch.getWorkflowInstanceKeys().size());
        return archiveBatch.getWorkflowInstanceKeys().size();
      } catch (ReindexException e) {
        logger.error(e.getMessage(), e);
        throw e;
      }
    } else {
      logger.debug("Nothing to archive");
      return 0;
    }
  }

  private void moveDocuments(String sourceIndexName, String idFieldName, String finishDate, List<Long> workflowInstanceKeys) throws ReindexException {

    String destinationIndexName = getDestinationIndexName(sourceIndexName, finishDate);

    reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, workflowInstanceKeys);

    deleteDocuments(sourceIndexName, idFieldName, workflowInstanceKeys);

  }

  public String getDestinationIndexName(String sourceIndexName, String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }

  private long deleteDocuments(String sourceIndexName, String idFieldName, List<Long> workflowInstanceKeys) throws ReindexException {
    DeleteByQueryRequest request =
        new DeleteByQueryRequest(sourceIndexName)
            .setBatchSize(workflowInstanceKeys.size())
            .setQuery(termsQuery(idFieldName, workflowInstanceKeys));
    request = applyDefaultSettings(request);
    try {
      final BulkByScrollResponse response = runDelete(request);
      return checkResponse(response, sourceIndexName, "delete");
    } catch (ReindexException ex) {
      throw ex;
    } catch (Exception e) {
      final String message = String.format("Exception occurred, while deleting the documents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Timed(value = Metrics.TIMER_NAME_ARCHIVER_DELETE_QUERY, description = "Archiver: delete query latency")
  private BulkByScrollResponse runDelete(DeleteByQueryRequest request) throws IOException {
    return esClient.deleteByQuery(request, RequestOptions.DEFAULT);
  }

  private <T extends AbstractBulkByScrollRequest<T>> T applyDefaultSettings(T request) {
    return request.setScroll(TimeValue.timeValueMillis(INTERNAL_SCROLL_KEEP_ALIVE_MS))
            .setAbortOnVersionConflict(false)
            .setSlices(AUTO_SLICES);
  }

  private long reindexDocuments(String sourceIndexName, String destinationIndexName, String idFieldName, List<Long> workflowInstanceKeys)
      throws ReindexException {

    ReindexRequest reindexRequest = new ReindexRequest()
        .setSourceIndices(sourceIndexName)
        .setSourceBatchSize(workflowInstanceKeys.size())
        .setDestIndex(destinationIndexName)
        .setSourceQuery(termsQuery(idFieldName, workflowInstanceKeys));

    reindexRequest = applyDefaultSettings(reindexRequest);

    try {
      BulkByScrollResponse response = runReindex(reindexRequest);

      return checkResponse(response, sourceIndexName, "reindex");
    } catch (ReindexException ex) {
      throw ex;
    } catch (Exception e) {
      final String message = String.format("Exception occurred, while reindexing the documents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Timed(value = Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY, description = "Archiver: reindex query latency")
  private BulkByScrollResponse runReindex(ReindexRequest reindexRequest) throws IOException {
    return esClient.reindex(reindexRequest, RequestOptions.DEFAULT);
  }

  private long checkResponse(BulkByScrollResponse response, String sourceIndexName, String operation) throws ReindexException {
    final List<BulkItemResponse.Failure> bulkFailures = response.getBulkFailures();
    if (bulkFailures.size() > 0) {
      logger.error("Failures occurred when performing operation: {} on source index {}. See details below.", operation, sourceIndexName);
      bulkFailures.stream().forEach(f -> logger.error(f.toString()));
      throw new ReindexException(String.format("Operation %s failed", operation));
    } else {
      logger.debug("Operation {} succeded on source index {}. Response: {}", operation, sourceIndexName, response.toString());
      return response.getTotal();
    }
  }

}
