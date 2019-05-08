/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisOutcomeDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.service.ProcessDefinitionService;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;


@AllArgsConstructor
@Component
@Slf4j
public class BranchAnalysisReader {

  private RestHighLevelClient esClient;
  private ProcessDefinitionService definitionService;
  private ProcessQueryFilterEnhancer queryFilterEnhancer;

  public BranchAnalysisDto branchAnalysis(String userId, BranchAnalysisQueryDto request) {
    ValidationHelper.validate(request);
    log.debug("Performing branch analysis on process definition with key [{}] and version [{}]",
      request.getProcessDefinitionKey(),
      request.getProcessDefinitionVersion()
    );
    
    final BranchAnalysisDto result = new BranchAnalysisDto();
    getBpmnModelInstance(userId, request.getProcessDefinitionKey(), request.getProcessDefinitionVersion())
      .ifPresent(bpmnModelInstance -> {
        final List<FlowNode> gatewayOutcomes = fetchGatewayOutcomes(bpmnModelInstance, request.getGateway());
        final Set<String> activityIdsWithMultipleIncomingSequenceFlows =
          extractFlowNodesWithMultipleIncomingSequenceFlows(bpmnModelInstance);

        for (FlowNode activity : gatewayOutcomes) {
          Set<String> activitiesToExcludeFromBranchAnalysis =
            extractActivitiesToExclude(gatewayOutcomes, activityIdsWithMultipleIncomingSequenceFlows, activity.getId(), request.getEnd());
          BranchAnalysisOutcomeDto branchAnalysis = branchAnalysis(activity, request, activitiesToExcludeFromBranchAnalysis);
          result.getFollowingNodes().put(branchAnalysis.getActivityId(), branchAnalysis);
        }

        result.setEndEvent(request.getEnd());
        result.setTotal(calculateActivityCount(request.getEnd(), request, Collections.emptySet()));
      });

    return result;
  }

  private Set<String> extractActivitiesToExclude(List<FlowNode> gatewayOutcomes,
                                                 Set<String> activityIdsWithMultipleIncomingSequenceFlows,
                                                 String currentActivityId,
                                                 String endEventActivityId) {
    Set<String> activitiesToExcludeFromBranchAnalysis = new HashSet<>();
    for (FlowNode gatewayOutgoingNode : gatewayOutcomes) {
      String activityId = gatewayOutgoingNode.getId();
      if (!activityIdsWithMultipleIncomingSequenceFlows.contains(activityId)) {
        activitiesToExcludeFromBranchAnalysis.add(gatewayOutgoingNode.getId());
      }
    }
    activitiesToExcludeFromBranchAnalysis.remove(currentActivityId);
    activitiesToExcludeFromBranchAnalysis.remove(endEventActivityId);
    return activitiesToExcludeFromBranchAnalysis;
  }

  private BranchAnalysisOutcomeDto branchAnalysis(FlowNode flowNode, BranchAnalysisQueryDto request, Set<String> activitiesToExclude) {

    BranchAnalysisOutcomeDto result = new BranchAnalysisOutcomeDto();
    result.setActivityId(flowNode.getId());
    result.setActivityCount(calculateActivityCount(flowNode.getId(), request, activitiesToExclude));
    result.setActivitiesReached(calculateReachedEndEventActivityCount(flowNode.getId(), request, activitiesToExclude));

    return result;
  }

  private long calculateReachedEndEventActivityCount(String activityId, BranchAnalysisQueryDto request, Set<String> activitiesToExclude) {
    BoolQueryBuilder query = boolQuery()
      .must(termQuery(PROCESS_DEFINITION_KEY, request.getProcessDefinitionKey()))
      .must(termQuery(PROCESS_DEFINITION_VERSION, request.getProcessDefinitionVersion()))
      .must(createMustMatchActivityIdQuery(request.getGateway()))
      .must(createMustMatchActivityIdQuery(activityId))
      .must(createMustMatchActivityIdQuery(request.getEnd())
      );
    excludeActivities(activitiesToExclude, query);

    return executeQuery(request, query);
  }

  private long calculateActivityCount(String activityId, BranchAnalysisQueryDto request, Set<String> activitiesToExclude) {
    BoolQueryBuilder query = boolQuery()
      .must(termQuery(PROCESS_DEFINITION_KEY, request.getProcessDefinitionKey()))
      .must(termQuery(PROCESS_DEFINITION_VERSION, request.getProcessDefinitionVersion()))
      .must(createMustMatchActivityIdQuery(request.getGateway()))
      .must(createMustMatchActivityIdQuery(activityId));
    excludeActivities(activitiesToExclude, query);

    return executeQuery(request, query);
  }

  private void excludeActivities(Set<String> activitiesToExclude, BoolQueryBuilder query) {
    for (String excludeActivityId : activitiesToExclude) {
      query
        .mustNot(createMustMatchActivityIdQuery(excludeActivityId));
    }
  }

  private NestedQueryBuilder createMustMatchActivityIdQuery(String activityId) {
    return nestedQuery(
      ProcessInstanceType.EVENTS,
      termQuery("events.activityId", activityId),
      ScoreMode.None
    );
  }

  private long executeQuery(BranchAnalysisQueryDto request, BoolQueryBuilder query) {
    queryFilterEnhancer.addFilterToQuery(query, request.getFilter());

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(0)
      .fetchSource(false);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
        .types(PROC_INSTANCE_TYPE)
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to perform branch analysis on process definition with key [%s] and version [%s}]",
        request.getProcessDefinitionKey(),
        request.getProcessDefinitionVersion()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return searchResponse.getHits().getTotalHits();
  }

  private List<FlowNode> fetchGatewayOutcomes(BpmnModelInstance bpmnModelInstance, String gatewayActivityId) {
    List<FlowNode> result = new ArrayList<>();
    FlowNode flowNode = bpmnModelInstance.getModelElementById(gatewayActivityId);
    for (SequenceFlow sequence : flowNode.getOutgoing()) {
      result.add(sequence.getTarget());
    }
    return result;
  }

  private Optional<BpmnModelInstance> getBpmnModelInstance(String userId, String definitionKey, String definitionVersion) {
    return definitionService.getProcessDefinitionXml(userId, definitionKey, definitionVersion)
      .map(xml -> Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes())));
  }

  private Set<String> extractFlowNodesWithMultipleIncomingSequenceFlows(BpmnModelInstance bpmnModelInstance) {
    Collection<SequenceFlow> sequenceFlowCollection = bpmnModelInstance.getModelElementsByType(SequenceFlow.class);
    Set<String> activitiesWithOneIncomingSequenceFlow = new HashSet<>();
    Set<String> activityIdsWithMultipleIncomingSequenceFlows = new HashSet<>();
    for (SequenceFlow sequenceFlow : sequenceFlowCollection) {
      String targetActivityId = sequenceFlow.getTarget().getId();
      if(activitiesWithOneIncomingSequenceFlow.contains(targetActivityId) ){
        activityIdsWithMultipleIncomingSequenceFlows.add(targetActivityId);
      } else {
        activitiesWithOneIncomingSequenceFlow.add(targetActivityId);
      }
    }
    return activityIdsWithMultipleIncomingSequenceFlows;
  }
}
