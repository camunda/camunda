/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver;

import static io.camunda.operate.util.ElasticsearchUtil.INTERNAL_SCROLL_KEEP_ALIVE_MS;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES_VALUE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import io.camunda.operate.Metrics;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.zeebe.PartitionHolder;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.reindex.AbstractBulkByScrollRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@DependsOn("schemaStartup")
public class Archiver {

  private static final String INDEX_NAME_PATTERN = "%s%s";
  private static final Logger logger = LoggerFactory.getLogger(Archiver.class);

  private boolean shutdown = false;

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  private ThreadPoolTaskScheduler archiverExecutor;

  @Autowired
  private Metrics metrics;

  @Autowired
  private ObjectMapper objectMapper;

  @PostConstruct
  public void startArchiving() {
    if (operateProperties.getArchiver().isRolloverEnabled()) {
      logger.info("INIT: Start archiving data...");

      //split the list of partitionIds to parallelize
      List<Integer> partitionIds = partitionHolder.getPartitionIds();
      logger.info("Starting archiver for partitions: {}", partitionIds);
      int threadsCount = operateProperties.getArchiver().getThreadsCount();
      if (threadsCount > partitionIds.size()) {
        logger.warn("Too many archiver threads are configured, not all of them will be in use. Number of threads: {}, number of partitions to parallelize by: {}",
            threadsCount, partitionIds.size());
      }

      for (int i=0; i < threadsCount; i++) {
        List<Integer> partitionIdsSubset = CollectionUtil.splitAndGetSublist(partitionIds, threadsCount, i);
        if (!partitionIdsSubset.isEmpty()) {
          ProcessInstancesArchiverJob archiverJob = beanFactory.getBean(ProcessInstancesArchiverJob.class, partitionIdsSubset);
          archiverExecutor.execute(archiverJob);
        }
        if (partitionIdsSubset.contains(1)) {
          BatchOperationArchiverJob batchOperationArchiverJob = beanFactory.getBean(BatchOperationArchiverJob.class);
          archiverExecutor.execute(batchOperationArchiverJob);
        }
      }
    }
  }

  public void moveDocuments(String sourceIndexName, String idFieldName, String finishDate, List<Object> ids) throws ArchiverException {

    String destinationIndexName = getDestinationIndexName(sourceIndexName, finishDate);

    reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, ids);

    deleteDocuments(sourceIndexName, idFieldName, ids);

  }

  private Long deleteWithTimer(Callable<Long> callable) throws Exception {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_DELETE_QUERY)
        .recordCallable(callable);
  }

  private Long reindexWithTimer(Callable<Long> callable) throws Exception {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY)
        .recordCallable(callable);
  }


  public String getDestinationIndexName(String sourceIndexName, String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }

  private long deleteDocuments(String sourceIndexName, String idFieldName, List<Object> processInstanceKeys) throws ArchiverException {
    try {
      final String query = termsQuery(idFieldName, processInstanceKeys).toString();
      return deleteWithTimer(
          () -> delete(query, sourceIndexName));
    } catch (ArchiverException ex) {
      throw ex;
    } catch (Exception e) {
      final String message = String.format("Exception occurred, while deleting the documents: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private long delete(final String query, String sourceIndexName)
      throws IOException, ArchiverException {

    final Request deleteWithTaskRequest = new Request(HttpPost.METHOD_NAME,
        String.format("/%s/_delete_by_query", sourceIndexName));
    deleteWithTaskRequest.setJsonEntity(String.format("{\"query\": %s }", query));
    deleteWithTaskRequest.addParameter("wait_for_completion", "false");
    deleteWithTaskRequest.addParameter("slices", AUTO_SLICES_VALUE);
    deleteWithTaskRequest.addParameter("conflicts", "proceed");

    final Response response = esClient.getLowLevelClient().performRequest(deleteWithTaskRequest);

    if (!(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)) {
      final HttpEntity entity = response.getEntity();
      throw new ArchiverException(String
          .format("Exception occurred when performing deletion. Status code: %s, error: %s",
              response.getStatusLine().getStatusCode(),
              entity == null ? "" : EntityUtils.toString(entity)));
    }

    Map<String, Object> bodyMap = objectMapper
        .readValue(response.getEntity().getContent(), Map.class);
    String taskId = (String) bodyMap.get("task");
    logger.debug("Deletion started for index {}. Task id {}", sourceIndexName, taskId);
    //wait till task is completed
    return ElasticsearchUtil.waitAndCheckTaskResult(taskId, sourceIndexName, "delete", esClient);
  }

  private long reindexDocuments(String sourceIndexName, String destinationIndexName,
      String idFieldName, List<Object> processInstanceKeys)
      throws ArchiverException {

    ReindexRequest reindexRequest = new ReindexRequest()
        .setSourceIndices(sourceIndexName)
        .setSourceBatchSize(processInstanceKeys.size())
        .setDestIndex(destinationIndexName)
        .setSourceQuery(termsQuery(idFieldName, processInstanceKeys));

    reindexRequest = applyDefaultSettings(reindexRequest);

    try {
      ReindexRequest finalReindexRequest = reindexRequest;
      return reindexWithTimer(
          () -> ElasticsearchUtil.reindex(finalReindexRequest, sourceIndexName, esClient));
    } catch (ArchiverException ex) {
      throw ex;
    } catch (Exception e) {
      final String message = String
          .format("Exception occurred, while reindexing the documents: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private <T extends AbstractBulkByScrollRequest<T>> T applyDefaultSettings(T request) {
    return request.setScroll(TimeValue.timeValueMillis(INTERNAL_SCROLL_KEEP_ALIVE_MS))
        .setAbortOnVersionConflict(false)
        .setSlices(AUTO_SLICES);
  }

  @PreDestroy
  private void shutdown() {
    shutdown = true;
  }

}
