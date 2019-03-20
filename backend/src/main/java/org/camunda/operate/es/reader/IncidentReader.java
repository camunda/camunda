/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.ErrorType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.rest.dto.incidents.IncidentDto;
import org.camunda.operate.rest.dto.incidents.IncidentErrorTypeDto;
import org.camunda.operate.rest.dto.incidents.IncidentFlowNodeDto;
import org.camunda.operate.rest.dto.incidents.IncidentResponseDto;
import org.camunda.operate.rest.exception.NotFoundException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class IncidentReader extends AbstractReader {

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private OperationReader operationReader;

  public List<IncidentEntity> getAllIncidents(String workflowInstanceId) {
    final TermQueryBuilder workflowInstanceIdQ = termQuery(IncidentTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);

    final ConstantScoreQueryBuilder query = constantScoreQuery(workflowInstanceIdQ);

    final SearchRequestBuilder requestBuilder =
      esClient.prepareSearch(incidentTemplate.getAlias())
        .setQuery(query)
        .addSort(IncidentTemplate.CREATION_TIME, SortOrder.ASC);
    return scroll(requestBuilder, IncidentEntity.class);
  }

  public IncidentEntity getIncidentById(String incidentId) {
    final IdsQueryBuilder idsQ = idsQuery().addIds(incidentId);

    final ConstantScoreQueryBuilder query = constantScoreQuery(idsQ);

    final SearchResponse response =
      esClient.prepareSearch(incidentTemplate.getAlias())
        .setQuery(query)
        .get();
    if (response.getHits().totalHits == 1) {
      return ElasticsearchUtil
        .fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, IncidentEntity.class);
    } else if (response.getHits().totalHits > 1) {
      throw new NotFoundException(String.format("Could not find unique incident with id '%s'.", incidentId));
    } else {
      throw new NotFoundException(String.format("Could not find incident with id '%s'.", incidentId));
    }

  }

  public IncidentResponseDto getIncidents(String workflowInstanceId) {

    final TermQueryBuilder workflowInstanceQ = termQuery(IncidentTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);

    final String errorTypesAggName = "errorTypesAgg";
    final String flowNodesAggName = "flowNodesAgg";

    final TermsAggregationBuilder errorTypesAgg =
      terms(errorTypesAggName)
        .field(IncidentTemplate.ERROR_TYPE)
        .size(ErrorType.values().length)
        .order(BucketOrder.key(true));
    final TermsAggregationBuilder flowNodesAgg =
      terms(flowNodesAggName)
        .field(IncidentTemplate.FLOW_NODE_ID)
        .size(ElasticsearchUtil.TERMS_AGG_SIZE)
        .order(BucketOrder.key(true));

    final SearchRequestBuilder requestBuilder =
      esClient.prepareSearch(incidentTemplate.getAlias())
        .setQuery(workflowInstanceQ)
        .addAggregation(errorTypesAgg)
        .addAggregation(flowNodesAgg);

    IncidentResponseDto incidentResponse = new IncidentResponseDto();

    final List<IncidentEntity> incidents = scroll(requestBuilder, IncidentEntity.class, aggs -> {
      ((Terms)aggs.get(errorTypesAggName)).getBuckets().forEach(b -> {
        ErrorType errorType = ErrorType.createFrom(b.getKeyAsString());
        incidentResponse.getErrorTypes().add(new IncidentErrorTypeDto(errorType.name(), errorType.getTitle(), (int)b.getDocCount()));
      });
      ((Terms)aggs.get(flowNodesAggName)).getBuckets().forEach(b ->
        incidentResponse.getFlowNodes().add(new IncidentFlowNodeDto(b.getKeyAsString(), (int)b.getDocCount())));
    });

    final Map<String, List<OperationEntity>> operations = operationReader.getOperationsPerIncidentId(workflowInstanceId);

    incidentResponse.setIncidents(IncidentDto.sortDefault(IncidentDto.createFrom(incidents, operations)));
    incidentResponse.setCount(incidents.size());

    return incidentResponse;
  }



}
