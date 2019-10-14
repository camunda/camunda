/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.camunda.operate.es.schema.indices.ImportPositionIndex;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.Topology;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class PartitionHolder {

  public static final long WAIT_TIME_IN_MS = 500L;

  public static final int MAX_RETRY = 2 * 30;

  private static final Logger logger = LoggerFactory.getLogger(PartitionHolder.class);

  public static final String PARTITION_ID_FIELD_NAME = ImportPositionIndex.PARTITION_ID;

  private List<Integer> partitionIds = new ArrayList<>();

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ZeebeClient zeebeClient;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  /**
   * Retrieves PartitionIds with waiting time of {@value #WAIT_TIME_IN_MS} milliseconds and retries for {@value #MAX_RETRY} times.
   */
  public List<Integer> getPartitionIds() {
    return getPartitionIdsWithWaitingTimeAndRetries(WAIT_TIME_IN_MS, MAX_RETRY);
  }
  
  public List<Integer> getPartitionIdsWithWaitingTimeAndRetries(long waitingTimeInMilliseconds, int maxRetries) {
    int retries = 0;
    while (partitionIds.isEmpty() && retries <= maxRetries) {
      if (retries > 0) {
        sleepFor(waitingTimeInMilliseconds);
      }
      retries++;
      Optional<List<Integer>> zeebePartitionIds = getPartitionIdsFromZeebe();
      if (zeebePartitionIds.isPresent()) {
        partitionIds = extractCurrentNodePartitions(zeebePartitionIds.get());
      } else {
        if (retries <= maxRetries) {
          logger.debug("Partition ids can't be fetched from Zeebe. Try next round ({}).", retries);
        } else {
          logger.debug("Partition ids can't be fetched from Zeebe.");
        }
      }
    }
    if (partitionIds.isEmpty()) {
      Optional<List<Integer>> zeebeElasticSearchPartitionIds = getPartitionsFromElasticsearch();
      if (zeebeElasticSearchPartitionIds.isEmpty()) {
        logger.warn("Partition ids can not be fetched. Importer won't be able to start.");
      } else {
        return extractCurrentNodePartitions(zeebeElasticSearchPartitionIds.get());
      }
    }
    return partitionIds;
  }

  /**
   * Modifies the passed collection!
   * @param partitionIds
   */
  protected List<Integer> extractCurrentNodePartitions(List<Integer> partitionIds) {
    Integer[] configuredIds = operateProperties.getClusterNode().getPartitionIds();
    if (configuredIds != null && configuredIds.length > 0) {
      partitionIds.retainAll(Arrays.asList(configuredIds));
    } else if (operateProperties.getClusterNode().getNodeCount() != null &&
              operateProperties.getClusterNode().getCurrentNodeId() != null){
      Integer nodeCount = operateProperties.getClusterNode().getNodeCount();
      Integer nodeId = operateProperties.getClusterNode().getCurrentNodeId();

      partitionIds = CollectionUtil.splitAndGetSublist(partitionIds, nodeCount, nodeId);
    }
    return partitionIds;
  }

  protected void sleepFor(long milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException e) {
      // Ignore interruption
    }
  }
  
  protected Optional<List<Integer>> getPartitionIdsFromZeebe(){
    logger.debug("Requesting partition ids from Zeebe client");
    try {
      final Topology topology = zeebeClient.newTopologyRequest().send().join();
      int partitionCount = topology.getPartitionsCount();
      if(partitionCount>0) {
        return Optional.of(CollectionUtil.fromTo(1, partitionCount));
      }
    } catch (Throwable t) { 
      logger.warn("Error occurred when requesting partition ids from Zeebe client: " + t.getMessage(), t);
    }
    return Optional.empty();
  }

  protected Optional<List<Integer>> getPartitionsFromElasticsearch() {
    logger.debug("Requesting partition ids from elasticsearch");
    final String aggName = "partitions";
    SearchRequest searchRequest = new SearchRequest(ImportValueType.DEPLOYMENT.getAliasName(operateProperties.getZeebeElasticsearch().getPrefix()))
        .source(new SearchSourceBuilder()
            .aggregation(terms(aggName)
                .field(PARTITION_ID_FIELD_NAME)
                .size(ElasticsearchUtil.TERMS_AGG_SIZE)));
    try {
      final SearchResponse searchResponse = zeebeEsClient.search(searchRequest, RequestOptions.DEFAULT);
      final List<Integer> partitionIds = ((Terms) searchResponse.getAggregations().get(aggName)).getBuckets().stream()
          .collect(ArrayList::new, (list, bucket) -> list.add(bucket.getKeyAsNumber().intValue()), (list1, list2) -> list1.addAll(list2));
      logger.debug("Following partition ids were found: {}", partitionIds);
      if(!partitionIds.isEmpty()) {
        return Optional.of(partitionIds);
      }
    } catch (Exception ex) {
      logger.warn("Error occurred when requesting partition ids from Elasticsearch: " + ex.getMessage(), ex);
    }
    return Optional.empty();
  }

}
