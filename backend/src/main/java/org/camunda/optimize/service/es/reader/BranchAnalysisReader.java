/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.google.common.collect.Sets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisOutcomeDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.util.DefinitionQueryUtil.createDefinitionQuery;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;


@RequiredArgsConstructor
@Component
@Slf4j
public class BranchAnalysisReader {

  private final OptimizeElasticsearchClient esClient;
  private final DefinitionService definitionService;
  private final ProcessQueryFilterEnhancer queryFilterEnhancer;
  private final ProcessDefinitionReader processDefinitionReader;

  public BranchAnalysisResponseDto branchAnalysis(final BranchAnalysisRequestDto request, final ZoneId timezone) {
    log.debug(
      "Performing branch analysis on process definition with key [{}] and versions [{}]",
      request.getProcessDefinitionKey(),
      request.getProcessDefinitionVersions()
    );

    final BranchAnalysisResponseDto result = new BranchAnalysisResponseDto();
    getBpmnModelInstance(
      request.getProcessDefinitionKey(),
      request.getProcessDefinitionVersions(),
      request.getTenantIds()
    ).ifPresent(bpmnModelInstance -> {
      final List<FlowNode> gatewayOutcomes = fetchGatewayOutcomes(bpmnModelInstance, request.getGateway());
      final Set<String> activityIdsWithMultipleIncomingSequenceFlows =
        extractFlowNodesWithMultipleIncomingSequenceFlows(bpmnModelInstance);
      final FlowNode gateway = bpmnModelInstance.getModelElementById(request.getGateway());
      final FlowNode end = bpmnModelInstance.getModelElementById(request.getEnd());
      final boolean canReachEndFromGateway = isPathPossible(gateway, end, Sets.newHashSet());

      for (FlowNode activity : gatewayOutcomes) {
        final Set<String> activitiesToExcludeFromBranchAnalysis = extractActivitiesToExclude(
          gatewayOutcomes, activityIdsWithMultipleIncomingSequenceFlows, activity.getId(), request.getEnd()
        );
        BranchAnalysisOutcomeDto branchAnalysis = new BranchAnalysisOutcomeDto();
        if (canReachEndFromGateway) {
          branchAnalysis = branchAnalysis(
            activity, request, activitiesToExcludeFromBranchAnalysis, timezone
          );
        } else {
          branchAnalysis.setActivityId(activity.getId());
          branchAnalysis.setActivitiesReached(0L); // End event cannot be reached from gateway
          branchAnalysis.setActivityCount((calculateActivityCount(
            activity.getId(),
            request,
            activitiesToExcludeFromBranchAnalysis,
            timezone
          )));
        }
        result.getFollowingNodes().put(branchAnalysis.getActivityId(), branchAnalysis);
      }

      result.setEndEvent(request.getEnd());
      result.setTotal(calculateActivityCount(request.getEnd(), request, Collections.emptySet(), timezone));
    });

    return result;
  }

  private boolean isPathPossible(final FlowNode currentNode, final FlowNode targetNode,
                                 final Set<FlowNode> visitedNodes) {
    visitedNodes.add(currentNode);
    final List<FlowNode> succeedingNodes = currentNode.getSucceedingNodes().list();
    boolean pathFound = false;
    for (FlowNode succeedingNode : succeedingNodes) {
      if (visitedNodes.contains(succeedingNode)) {
        continue;
      }
      pathFound = succeedingNode.equals(targetNode) || isPathPossible(succeedingNode, targetNode, visitedNodes);
      if (pathFound) {
        break;
      }
    }
    return pathFound;
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
                                                  final BranchAnalysisRequestDto request,
                                                  final Set<String> activitiesToExclude,
                                                  final ZoneId timezone) {

    BranchAnalysisOutcomeDto result = new BranchAnalysisOutcomeDto();
    result.setActivityId(flowNode.getId());
    result.setActivityCount(calculateActivityCount(flowNode.getId(), request, activitiesToExclude, timezone));
    result.setActivitiesReached(calculateReachedEndEventActivityCount(
      flowNode.getId(),
      request,
      activitiesToExclude,
      timezone
    ));

    return result;
  }

  private long calculateReachedEndEventActivityCount(final String activityId,
                                                     final BranchAnalysisRequestDto request,
                                                     final Set<String> activitiesToExclude,
                                                     final ZoneId timezone) {
    final BoolQueryBuilder query = buildBaseQuery(request, activitiesToExclude)
      .must(createMustMatchActivityIdQuery(request.getGateway()))
      .must(createMustMatchActivityIdQuery(activityId))
      .must(createMustMatchActivityIdQuery(request.getEnd()));
    return executeQuery(request, query, timezone);
  }

  private long calculateActivityCount(final String activityId,
                                      final BranchAnalysisRequestDto request,
                                      final Set<String> activitiesToExclude,
                                      final ZoneId timezone) {
    final BoolQueryBuilder query = buildBaseQuery(request, activitiesToExclude)
      .must(createMustMatchActivityIdQuery(request.getGateway()))
      .must(createMustMatchActivityIdQuery(activityId));
    return executeQuery(request, query, timezone);
  }

  private BoolQueryBuilder buildBaseQuery(final BranchAnalysisRequestDto request,
                                          final Set<String> activitiesToExclude) {
    final BoolQueryBuilder query = createDefinitionQuery(
      request.getProcessDefinitionKey(),
      request.getProcessDefinitionVersions(),
      request.getTenantIds(),
      new ProcessInstanceIndex(request.getProcessDefinitionKey()),
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

  private long executeQuery(final BranchAnalysisRequestDto request,
                            final BoolQueryBuilder query,
                            final ZoneId timezone) {
    queryFilterEnhancer.addFilterToQuery(query, request.getFilter(), timezone);
    final CountRequest searchRequest =
      new CountRequest(getProcessInstanceIndexAliasName(request.getProcessDefinitionKey())).query(query);
    try {
      final CountResponse countResponse = esClient.count(searchRequest, RequestOptions.DEFAULT);
      return countResponse.getCount();
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to perform branch analysis on process definition with key [%s] and versions [%s}]",
        request.getProcessDefinitionKey(),
        request.getProcessDefinitionVersions()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
          "Was not able to perform branch analysis because the required instance index {} does not " +
            "exist. Returning 0 instead.",
          getProcessInstanceIndexAliasName(request.getProcessDefinitionKey())
        );
        return 0L;
      }
      throw e;
    }
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

  private Optional<BpmnModelInstance> getBpmnModelInstance(final String definitionKey,
                                                           final List<String> definitionVersions,
                                                           final List<String> tenantIds) {
    final Optional<String> processDefinitionXml = tenantIds.stream()
      .map(tenantId -> getDefinitionXml(definitionKey, definitionVersions, Collections.singletonList(tenantId)))
      .filter(Optional::isPresent)
      .findFirst()
      .orElseGet(() -> getDefinitionXml(definitionKey, definitionVersions, ReportConstants.DEFAULT_TENANT_IDS));

    return processDefinitionXml
      .map(xml -> Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes())));
  }

  private Optional<String> getDefinitionXml(final String definitionKey,
                                            final List<String> definitionVersions,
                                            final List<String> tenants) {
    final Optional<ProcessDefinitionOptimizeDto> definitionWithXmlAsService =
      definitionService.getDefinitionWithXmlAsService(
        PROCESS,
        definitionKey,
        definitionVersions,
        tenants
      );
    return definitionWithXmlAsService
      .map(ProcessDefinitionOptimizeDto::getBpmn20Xml);
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
