/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ErrorType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.entities.detailview.ActivityInstanceForDetailViewEntity;
import org.camunda.operate.es.schema.templates.ActivityInstanceTemplate;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.es.schema.templates.VariableTemplate;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeDto;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeRequestDto;
import org.camunda.operate.rest.dto.detailview.DetailViewActivityInstanceDto;
import org.camunda.operate.rest.dto.detailview.VariablesRequestDto;
import org.camunda.operate.rest.dto.incidents.IncidentDto;
import org.camunda.operate.rest.dto.incidents.IncidentErrorTypeDto;
import org.camunda.operate.rest.dto.incidents.IncidentFlowNodeDto;
import org.camunda.operate.rest.dto.incidents.IncidentResponseDto;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class DetailViewReader {

  private static final Logger logger = LoggerFactory.getLogger(DetailViewReader.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ActivityInstanceTemplate activityInstanceTemplate;

  @Autowired
  private VariableTemplate variableTemplate;

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private OperateProperties operateProperties;

  public List<VariableEntity> getVariables(VariablesRequestDto variableRequest) {
    final TermQueryBuilder workflowInstanceIdQ = termQuery(VariableTemplate.WORKFLOW_INSTANCE_ID, variableRequest.getWorkflowInstanceId());
    final TermQueryBuilder scopeIdQ = termQuery(VariableTemplate.SCOPE_ID, variableRequest.getScopeId());

    final ConstantScoreQueryBuilder query = constantScoreQuery(joinWithAnd(workflowInstanceIdQ, scopeIdQ));

    final SearchRequestBuilder requestBuilder =
      esClient.prepareSearch(variableTemplate.getAlias())
        .setQuery(query)
        .addSort(VariableTemplate.NAME, SortOrder.ASC);
    return scroll(requestBuilder, VariableEntity.class);

  }

  public List<IncidentEntity> getAllIncidents(String workflowInstanceId) {
    final TermQueryBuilder workflowInstanceIdQ = termQuery(IncidentTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);

    final ConstantScoreQueryBuilder query = constantScoreQuery(workflowInstanceIdQ);

    final SearchRequestBuilder requestBuilder =
      esClient.prepareSearch(incidentTemplate.getAlias())
        .setQuery(query)
        .addSort(IncidentTemplate.CREATION_TIME, SortOrder.ASC);
    return scroll(requestBuilder, IncidentEntity.class);

  }

  public ActivityInstanceTreeDto getActivityInstanceTree(ActivityInstanceTreeRequestDto requestDto) {

    List<ActivityInstanceForDetailViewEntity> activityInstances = getAllActivityInstances(requestDto.getWorkflowInstanceId());

    final Map<String, DetailViewActivityInstanceDto> nodes = DetailViewActivityInstanceDto.createMapFrom(activityInstances);

    ActivityInstanceTreeDto tree = new ActivityInstanceTreeDto();

    for (DetailViewActivityInstanceDto node: nodes.values()) {
      if (node.getParentId() != null) {
        if (node.getParentId().equals(requestDto.getWorkflowInstanceId())) {
          tree.getChildren().add(node);
        } else {
          nodes.get(node.getParentId()).getChildren().add(node);
        }
        if (node.getState().equals(ActivityState.INCIDENT)) {
          propagateState(node, nodes);
        }
      }
    }

    return tree;
  }

  private void propagateState(DetailViewActivityInstanceDto currentNode, Map<String, DetailViewActivityInstanceDto> allNodes) {
    if (currentNode.getParentId() != null) {
      final DetailViewActivityInstanceDto parent = allNodes.get(currentNode.getParentId());
      if (parent != null) {
        parent.setState(ActivityState.INCIDENT);
        propagateState(parent, allNodes);
      }
    }
  }

  public List<ActivityInstanceForDetailViewEntity> getAllActivityInstances(String workflowInstanceId) {

    final TermQueryBuilder workflowInstanceIdQ = termQuery(ActivityInstanceTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);

    final SearchRequestBuilder requestBuilder =
      esClient.prepareSearch(activityInstanceTemplate.getAlias())
        .setQuery(constantScoreQuery(workflowInstanceIdQ))
        .addSort(ActivityInstanceTemplate.POSITION, SortOrder.ASC);
    return scroll(requestBuilder, ActivityInstanceForDetailViewEntity.class);
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
        .size(operateProperties.getElasticsearch().getTerms().getMaxFlowNodesInOneWorkflow())
        .order(BucketOrder.key(true));

    final SearchRequestBuilder requestBuilder =
      esClient.prepareSearch(incidentTemplate.getAlias())
      .setQuery(workflowInstanceQ)
      .addAggregation(errorTypesAgg)
      .addAggregation(flowNodesAgg);

    IncidentResponseDto incidentResponse = new IncidentResponseDto();

    final List<IncidentEntity> incidents = scroll(requestBuilder, IncidentEntity.class, searchResponse -> {
      ((Terms)searchResponse.getAggregations().get(errorTypesAggName)).getBuckets().forEach(b -> {
        ErrorType errorType = ErrorType.createFrom(b.getKeyAsString());
        incidentResponse.getErrorTypes().add(new IncidentErrorTypeDto(errorType.name(), errorType.getTitle(), (int)b.getDocCount()));
      });
      ((Terms)searchResponse.getAggregations().get(flowNodesAggName)).getBuckets().forEach(b ->
        incidentResponse.getFlowNodes().add(new IncidentFlowNodeDto(b.getKeyAsString(), (int)b.getDocCount())));
    });

    incidentResponse.setIncidents(IncidentDto.createFrom(incidents));
    incidentResponse.setCount(incidents.size());

    return incidentResponse;
  }

  protected <T extends OperateEntity> List<T> scroll(SearchRequestBuilder builder, Class<T> clazz) {
    return ElasticsearchUtil.scroll(builder, clazz, objectMapper, esClient);
  }

  protected <T extends OperateEntity> List<T> scroll(SearchRequestBuilder builder, Class<T> clazz,
    Consumer<SearchResponse> responseProcessor) {
    return ElasticsearchUtil.scroll(builder, clazz, objectMapper, esClient, responseProcessor);
  }
}
