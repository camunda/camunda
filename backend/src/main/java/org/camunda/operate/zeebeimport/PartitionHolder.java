/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

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
import io.zeebe.client.api.commands.Topology;

@Component
public class PartitionHolder {

  public static final long WAIT_TIME_IN_MS = 500L;

  public static final int MAX_RETRY = 2 * 30; 

  private static final Logger logger = LoggerFactory.getLogger(PartitionHolder.class);

  public static final String PARTITION_ID_FIELD_NAME = "metadata." + ImportPositionIndex.PARTITION_ID;

  private Set<Integer> partitionIds = new HashSet<>();

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
  public Set<Integer> getPartitionIds() {
    return getPartitionIdsWithWaitingTimeAndRetries(WAIT_TIME_IN_MS, MAX_RETRY);
  }
  
  public Set<Integer> getPartitionIdsWithWaitingTimeAndRetries(long waitingTimeInMilliseconds, int maxRetries) {
    int retries = 0;
    while (partitionIds.isEmpty() && retries <= maxRetries) {
      if (retries > 0) {
        sleepFor(waitingTimeInMilliseconds);
      }
      retries++;

      // partitionIds are only "present" when the sets are not empty.
      Optional<Set<Integer>> zeebePartitionIds = getPartitionIdsFromZeebe();
      Optional<Set<Integer>> zeebeElasticSearchPartitionIds = getPartitionsFromElasticsearch();

      if (zeebePartitionIds.isPresent() && zeebeElasticSearchPartitionIds.isPresent() && 
          zeebePartitionIds.get().equals(zeebeElasticSearchPartitionIds.get())) {
        logger.debug("PartitionIds from both sources are present and equal");
        partitionIds = zeebePartitionIds.get();
      } else if (zeebePartitionIds.isPresent()) {
        logger.debug("PartitionIds from zeebe client are present");
        partitionIds = zeebePartitionIds.get();
      } else if (zeebeElasticSearchPartitionIds.isPresent() && !zeebePartitionIds.isPresent()) {
        logger.debug("PartitionIds from elasticsearch are present");
        partitionIds.clear();
        return zeebeElasticSearchPartitionIds.get();
      }else {
        logger.debug("PartitionIds are not present or not equal. Try ({}) next round.");
      }
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
  
  protected Optional<Set<Integer>> getPartitionIdsFromZeebe(){
    logger.debug("Requesting partition ids from Zeebe client");
    try {
      final Topology topology = zeebeClient.newTopologyRequest().send().join();
      int partitionCount = topology.getPartitionsCount();
      if(partitionCount>0) {
        return Optional.of(new HashSet<Integer>(CollectionUtil.fromTo(1,partitionCount)));
      }
    } catch (Throwable t) { 
      logger.warn("Error occurred when requesting partition ids from Zeebe client: " + t.getMessage(), t);
    }
    return Optional.empty();
  }

  protected Optional<Set<Integer>> getPartitionsFromElasticsearch() {
    logger.debug("Requesting partition ids from elasticsearch");
    final String aggName = "partitions";
    SearchRequest searchRequest = new SearchRequest(ImportValueType.DEPLOYMENT.getAliasName(operateProperties.getZeebeElasticsearch().getPrefix()))
        .source(new SearchSourceBuilder()
            .aggregation(terms(aggName)
                .field(PARTITION_ID_FIELD_NAME)
                .size(ElasticsearchUtil.TERMS_AGG_SIZE)));
    try {
      final SearchResponse searchResponse = zeebeEsClient.search(searchRequest, RequestOptions.DEFAULT);
      final HashSet<Integer> partitionIds = ((Terms) searchResponse.getAggregations().get(aggName)).getBuckets().stream()
          .collect(HashSet::new, (set, bucket) -> set.add((Integer)bucket.getKeyAsNumber()), (set1, set2) -> set1.addAll(set2));
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
