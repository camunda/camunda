/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.HashSet;
import java.util.Set;
import org.camunda.operate.es.schema.indices.ImportPositionIndex;
import org.camunda.operate.property.OperateProperties;
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
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class PartitionHolder {

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

  public Set<Integer> getPartitionIds() {
    if (partitionIds.size() == 0) {
      initPartitionListFromZeebe();
    }
    //if still not initialized, try to read from Elasticsearch, but not cache, as it can change with the time
    if (partitionIds.size() == 0) {
      return getPartitionsFromElasticsearch();
    }
    return partitionIds;
  }

  private void initPartitionListFromZeebe() {
    try {
      final Topology topology = zeebeClient.newTopologyRequest().send().join();
      final int partitionsCount = topology.getPartitionsCount();
      //generate list of partition ids
      for (int i = 1; i<= partitionsCount; i++) {
        partitionIds.add(i);
      }
      if (partitionIds.size() == 0) {
        logger.warn("Partitions are not found. Import from Zeebe won't load any data.");
      } else {
        logger.debug("Following partition ids were found: {}", partitionIds);
      }
    } catch (Exception ex) { //TODO check exception class
      logger.warn("Error occurred when requesting partition ids from Zeebe: " + ex.getMessage(), ex);
      //ignore, if Zeebe is not available
    }
  }

  private Set<Integer> getPartitionsFromElasticsearch() {
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
          .collect(HashSet::new, (set, bucket) -> set.add(Integer.valueOf(bucket.getKeyAsString())), (set1, set2) -> set1.addAll(set2));
      logger.debug("Following partition ids were found: {}", partitionIds);
      return partitionIds;
    } catch (Exception ex) {
      logger.warn("Error occurred when requesting partition ids from Elasticsearch: " + ex.getMessage(), ex);
      return new HashSet<>();
    }
  }


}
