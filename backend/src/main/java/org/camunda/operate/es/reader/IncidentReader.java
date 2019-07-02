/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import io.zeebe.protocol.record.value.ErrorType;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.incidents.IncidentDto;
import org.camunda.operate.rest.dto.incidents.IncidentErrorTypeDto;
import org.camunda.operate.rest.dto.incidents.IncidentFlowNodeDto;
import org.camunda.operate.rest.dto.incidents.IncidentResponseDto;
import org.camunda.operate.rest.exception.NotFoundException;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IncidentReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(IncidentReader.class);

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private OperationReader operationReader;

  @Autowired
  private OperateProperties operateProperties;

  public List<IncidentEntity> getAllIncidents(Long workflowInstanceId) {
    final TermQueryBuilder workflowInstanceIdQ = termQuery(IncidentTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);

    final ConstantScoreQueryBuilder query = constantScoreQuery(workflowInstanceIdQ);

    final SearchRequest searchRequest = new SearchRequest(incidentTemplate.getAlias())
        .source(new SearchSourceBuilder().query(query).sort(IncidentTemplate.CREATION_TIME, SortOrder.ASC));

    try {
      return scroll(searchRequest, IncidentEntity.class);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Returns map of incident ids per workflow instance id.
   * @param workflowInstanceIds
   * @return
   */
  public Map<Long, List<String>> getIncidentIdsPerWorkflowInstance(List<Long> workflowInstanceIds) {
    final QueryBuilder workflowInstanceIdsQ = constantScoreQuery(termsQuery(IncidentTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceIds));
    final int batchSize = operateProperties.getElasticsearch().getBatchSize();

    final SearchRequest searchRequest = new SearchRequest(incidentTemplate.getAlias())
        .source(new SearchSourceBuilder()
            .query(workflowInstanceIdsQ)
            .fetchSource(IncidentTemplate.WORKFLOW_INSTANCE_ID, null)
            .size(batchSize));

    Map<Long, List<String>> result = new HashMap<>();
    try {
      ElasticsearchUtil.scrollWith(searchRequest, esClient, searchHits -> {
        for (SearchHit hit : searchHits.getHits()) {
          CollectionUtil.addToMap(result, Long.valueOf(hit.getSourceAsMap().get(IncidentTemplate.WORKFLOW_INSTANCE_ID).toString()), hit.getId());
        }
      }, null, null);
      return result;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  public IncidentEntity getIncidentById(String incidentId) {
    final IdsQueryBuilder idsQ = idsQuery().addIds(incidentId);

    final ConstantScoreQueryBuilder query = constantScoreQuery(idsQ);

    final SearchRequest searchRequest = new SearchRequest(incidentTemplate.getAlias()).source(new SearchSourceBuilder().query(query));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().totalHits == 1) {
        return ElasticsearchUtil.fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, IncidentEntity.class);
      } else if (response.getHits().totalHits > 1) {
        throw new NotFoundException(String.format("Could not find unique incident with id '%s'.", incidentId));
      } else {
        throw new NotFoundException(String.format("Could not find incident with id '%s'.", incidentId));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incident: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }

  }

  public IncidentResponseDto getIncidents(Long workflowInstanceId) {
    final TermQueryBuilder workflowInstanceQ = termQuery(IncidentTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);

    final String errorTypesAggName = "errorTypesAgg";
    final String flowNodesAggName = "flowNodesAgg";

    final TermsAggregationBuilder errorTypesAgg = terms(errorTypesAggName).field(IncidentTemplate.ERROR_TYPE).size(
        ErrorType.values().length)
        .order(BucketOrder.key(true));
    final TermsAggregationBuilder flowNodesAgg = terms(flowNodesAggName).field(IncidentTemplate.FLOW_NODE_ID).size(ElasticsearchUtil.TERMS_AGG_SIZE)
        .order(BucketOrder.key(true));

    final SearchRequest searchRequest = new SearchRequest(incidentTemplate.getAlias())
        .source(new SearchSourceBuilder().query(constantScoreQuery(workflowInstanceQ)).aggregation(errorTypesAgg).aggregation(flowNodesAgg));

    IncidentResponseDto incidentResponse = new IncidentResponseDto();
    try {
      final List<IncidentEntity> incidents = scroll(searchRequest, IncidentEntity.class, aggs -> {
        ((Terms) aggs.get(errorTypesAggName)).getBuckets().forEach(b -> {
          ErrorType errorType = ErrorType.valueOf(b.getKeyAsString());
          incidentResponse.getErrorTypes().add(new IncidentErrorTypeDto(IncidentEntity.getErrorTypeTitle(errorType), (int) b.getDocCount()));
        });
        ((Terms) aggs.get(flowNodesAggName)).getBuckets()
            .forEach(b -> incidentResponse.getFlowNodes().add(new IncidentFlowNodeDto(b.getKeyAsString(), (int) b.getDocCount())));
      });

      final Map<String, List<OperationEntity>> operations = operationReader.getOperationsPerIncidentId(workflowInstanceId);

      incidentResponse.setIncidents(IncidentDto.sortDefault(IncidentDto.createFrom(incidents, operations)));
      incidentResponse.setCount(incidents.size());

      return incidentResponse;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

}
