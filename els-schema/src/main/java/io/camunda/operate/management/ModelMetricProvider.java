/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.management;

import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.ProcessIndex;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

import static org.elasticsearch.search.aggregations.AggregationBuilders.cardinality;

@Component
public class ModelMetricProvider {

  private static final String DISTINCT_FIELD_COUNTS = "distinctFieldCounts";

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ProcessIndex processIndex;

  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  private Metrics metrics;

  @Autowired
  private OperateProperties operateProperties;

  private Long lastBPMNModelCount = 0L;
  private Long lastDMNModelCount = 0L;

  @PostConstruct
  private void registerMetrics(){
    logger.info("Register BPMN/DMN model metrics.");
    final String organizationId = operateProperties.getCloud().getOrganizationId();
    if (StringUtils.hasText(organizationId)) {
      metrics.registerGaugeSupplier(Metrics.GAUGE_BPMN_MODEL_COUNT, this::getBPMNModelCount,
          Metrics.TAG_KEY_ORGANIZATIONID, organizationId);
      metrics.registerGaugeSupplier(Metrics.GAUGE_DMN_MODEL_COUNT, this::getDMNModelCount,
          Metrics.TAG_KEY_ORGANIZATIONID, organizationId);
    } else {
      metrics.registerGaugeSupplier(Metrics.GAUGE_BPMN_MODEL_COUNT, this::getBPMNModelCount);
      metrics.registerGaugeSupplier(Metrics.GAUGE_DMN_MODEL_COUNT, this::getDMNModelCount);
    }
  }

  public Long getBPMNModelCount(){
    final Optional<Long> optionalCount = getDistinctCountFor(processIndex.getAlias(), ProcessIndex.BPMN_PROCESS_ID);
    optionalCount.ifPresent(val -> lastBPMNModelCount = val);
    return lastBPMNModelCount;
  }

  public Long getDMNModelCount(){
    final Optional<Long> optionalCount = getDistinctCountFor(decisionIndex.getAlias(), DecisionIndex.DECISION_ID);
    optionalCount.ifPresent(val -> lastDMNModelCount = val);
    return lastDMNModelCount;
  }

  public Optional<Long> getDistinctCountFor(final String indexAlias,final String fieldName){
    logger.debug("Called distinct count for field {} in index alias {}.", fieldName, indexAlias);
    final SearchRequest searchRequest = new SearchRequest(indexAlias)
        .source(new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery()).size(0)
            .aggregation(
                cardinality(DISTINCT_FIELD_COUNTS)
                    .precisionThreshold(1_000)
                    .field(fieldName)));
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Cardinality distinctFieldCounts  = searchResponse.getAggregations().get(DISTINCT_FIELD_COUNTS);
      return Optional.of(distinctFieldCounts.getValue());
    } catch (Exception e) {
      logger.error(String.format("Error in distinct count for field %s in index alias %s.", fieldName, indexAlias), e);
      return Optional.empty();
    }
  }
}
