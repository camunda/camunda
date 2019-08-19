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
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.TenantAuthorizationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.service.util.DefinitionQueryUtil.createDefinitionQuery;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;


@AllArgsConstructor
@Component
@Slf4j
public class BranchAnalysisReader {

  private OptimizeElasticsearchClient esClient;
  private ProcessDefinitionService definitionService;
  private TenantAuthorizationService tenantAuthorizationService;
  private ProcessQueryFilterEnhancer queryFilterEnhancer;
  private ProcessDefinitionReader processDefinitionReader;

  public BranchAnalysisDto branchAnalysis(final String userId, final BranchAnalysisQueryDto request) {
    ValidationHelper.validate(request);
    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, request.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }

    log.debug(
      "Performing branch analysis on process definition with key [{}] and versions [{}]",
      request.getProcessDefinitionKey(),
      request.getProcessDefinitionVersions()
    );

    final BranchAnalysisDto result = new BranchAnalysisDto();
    getBpmnModelInstance(
      userId, request.getProcessDefinitionKey(), request.getProcessDefinitionVersions(), request.getTenantIds()
    ).ifPresent(bpmnModelInstance -> {
      final List<FlowNode> gatewayOutcomes = fetchGatewayOutcomes(bpmnModelInstance, request.getGateway());
      final Set<String> activityIdsWithMultipleIncomingSequenceFlows =
        extractFlowNodesWithMultipleIncomingSequenceFlows(bpmnModelInstance);

      for (FlowNode activity : gatewayOutcomes) {
        final Set<String> activitiesToExcludeFromBranchAnalysis = extractActivitiesToExclude(
          gatewayOutcomes, activityIdsWithMultipleIncomingSequenceFlows, activity.getId(), request.getEnd()
        );
        final BranchAnalysisOutcomeDto branchAnalysis = branchAnalysis(
          activity, request, activitiesToExcludeFromBranchAnalysis
        );
        result.getFollowingNodes().put(branchAnalysis.getActivityId(), branchAnalysis);
      }

      result.setEndEvent(request.getEnd());
      result.setTotal(calculateActivityCount(request.getEnd(), request, Collections.emptySet()));
    });

    return result;
  }

  private Set<String> extractActivitiesToExclude(final List<FlowNode> gatewayOutcomes,
                                                 final Set<String> activityIdsWithMultipleIncomingSequenceFlows,
                                                 final String currentActivityId,
                                                 final String endEventActivityId) {
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

  private BranchAnalysisOutcomeDto branchAnalysis(final FlowNode flowNode,
                                                  final BranchAnalysisQueryDto request,
                                                  final Set<String> activitiesToExclude) {

    BranchAnalysisOutcomeDto result = new BranchAnalysisOutcomeDto();
    result.setActivityId(flowNode.getId());
    result.setActivityCount(calculateActivityCount(flowNode.getId(), request, activitiesToExclude));
    result.setActivitiesReached(calculateReachedEndEventActivityCount(flowNode.getId(), request, activitiesToExclude));

    return result;
  }

  private long calculateReachedEndEventActivityCount(final String activityId,
                                                     final BranchAnalysisQueryDto request,
                                                     final Set<String> activitiesToExclude) {
    final BoolQueryBuilder query = buildBaseQuery(request, activitiesToExclude)
      .must(createMustMatchActivityIdQuery(request.getGateway()))
      .must(createMustMatchActivityIdQuery(activityId))
      .must(createMustMatchActivityIdQuery(request.getEnd()));
    return executeQuery(request, query);
  }

  private long calculateActivityCount(final String activityId,
                                      final BranchAnalysisQueryDto request,
                                      final Set<String> activitiesToExclude) {
    final BoolQueryBuilder query = buildBaseQuery(request, activitiesToExclude)
      .must(createMustMatchActivityIdQuery(request.getGateway()))
      .must(createMustMatchActivityIdQuery(activityId));
    return executeQuery(request, query);
  }

  private BoolQueryBuilder buildBaseQuery(final BranchAnalysisQueryDto request, final Set<String> activitiesToExclude) {
    final BoolQueryBuilder query = createDefinitionQuery(
      request.getProcessDefinitionKey(),
      request.getProcessDefinitionVersions(),
      request.getTenantIds(),
      new ProcessInstanceIndex(),
      processDefinitionReader::getLatestVersionToKey
    );
    excludeActivities(activitiesToExclude, query);
    return query;
  }

  private void excludeActivities(final Set<String> activitiesToExclude,
                                 final BoolQueryBuilder query) {
    for (String excludeActivityId : activitiesToExclude) {
      query.mustNot(createMustMatchActivityIdQuery(excludeActivityId));
    }
  }

  private NestedQueryBuilder createMustMatchActivityIdQuery(final String activityId) {
    return nestedQuery(
      ProcessInstanceIndex.EVENTS,
      termQuery("events.activityId", activityId),
      ScoreMode.None
    );
  }

  private long executeQuery(final BranchAnalysisQueryDto request, final BoolQueryBuilder query) {
    queryFilterEnhancer.addFilterToQuery(query, request.getFilter());

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(0)
      .fetchSource(false);
    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
      .types(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to perform branch analysis on process definition with key [%s] and versions [%s}]",
        request.getProcessDefinitionKey(),
        request.getProcessDefinitionVersions()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return searchResponse.getHits().getTotalHits();
  }

  private List<FlowNode> fetchGatewayOutcomes(final BpmnModelInstance bpmnModelInstance,
                                              final String gatewayActivityId) {
    List<FlowNode> result = new ArrayList<>();
    FlowNode flowNode = bpmnModelInstance.getModelElementById(gatewayActivityId);
    for (SequenceFlow sequence : flowNode.getOutgoing()) {
      result.add(sequence.getTarget());
    }
    return result;
  }

  private Optional<BpmnModelInstance> getBpmnModelInstance(final String userId,
                                                           final String definitionKey,
                                                           final List<String> definitionVersions,
                                                           final List<String> tenantIds) {
    final Optional<String> processDefinitionXml = tenantIds.stream()
      .map(tenantId -> definitionService.getProcessDefinitionXml(userId, definitionKey, definitionVersions, tenantId))
      .filter(Optional::isPresent)
      .findFirst()
      .orElse(definitionService.getProcessDefinitionXml(userId, definitionKey, definitionVersions));

    return processDefinitionXml
      .map(xml -> Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes())));
  }

  private Set<String> extractFlowNodesWithMultipleIncomingSequenceFlows(final BpmnModelInstance bpmnModelInstance) {
    Collection<SequenceFlow> sequenceFlowCollection = bpmnModelInstance.getModelElementsByType(SequenceFlow.class);
    Set<String> activitiesWithOneIncomingSequenceFlow = new HashSet<>();
    Set<String> activityIdsWithMultipleIncomingSequenceFlows = new HashSet<>();
    for (SequenceFlow sequenceFlow : sequenceFlowCollection) {
      String targetActivityId = sequenceFlow.getTarget().getId();
      if (activitiesWithOneIncomingSequenceFlow.contains(targetActivityId)) {
        activityIdsWithMultipleIncomingSequenceFlows.add(targetActivityId);
      } else {
        activitiesWithOneIncomingSequenceFlow.add(targetActivityId);
      }
    }
    return activityIdsWithMultipleIncomingSequenceFlows;
  }
}
