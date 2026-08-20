/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import com.google.common.collect.Sets;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisOutcomeDto;
import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.util.LogUtil;
import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.slf4j.Logger;

public abstract class BranchAnalysisReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(BranchAnalysisReader.class);

  private final DefinitionService definitionService;

  protected BranchAnalysisReader(final DefinitionService definitionService) {
    this.definitionService = definitionService;
  }

  public BranchAnalysisResponseDto branchAnalysis(
      final BranchAnalysisRequestDto request, final ZoneId timezone) {
    final String logMsg =
        LogUtil.sanitizeLogMessage(
            String.format(
                "Performing branch analysis on process definition with key [%s] and versions [%s]",
                request.getProcessDefinitionKey(), request.getProcessDefinitionVersions()));
    LOG.debug(logMsg);

    final BranchAnalysisResponseDto result = new BranchAnalysisResponseDto();
    getBpmnModelInstance(
            request.getProcessDefinitionKey(),
            request.getProcessDefinitionVersions(),
            request.getTenantIds())
        .ifPresent(
            bpmnModelInstance -> {
              final List<FlowNode> gatewayOutcomes =
                  fetchGatewayOutcomes(bpmnModelInstance, request.getGateway());
              final Set<String> flowNodeIdsWithMultipleIncomingSequenceFlows =
                  extractFlowNodesWithMultipleIncomingSequenceFlows(bpmnModelInstance);
              final FlowNode gateway = bpmnModelInstance.getModelElementById(request.getGateway());
              final FlowNode end = bpmnModelInstance.getModelElementById(request.getEnd());
              final boolean canReachEndFromGateway =
                  isPathPossible(gateway, end, Sets.newHashSet());

              for (final FlowNode flowNode : gatewayOutcomes) {
                final Set<String> flowNodesToExcludeFromBranchAnalysis =
                    extractActivitiesToExclude(
                        gatewayOutcomes,
                        flowNodeIdsWithMultipleIncomingSequenceFlows,
                        flowNode.getId(),
                        request.getEnd());
                BranchAnalysisOutcomeDto branchAnalysis = new BranchAnalysisOutcomeDto();
                if (canReachEndFromGateway) {
                  branchAnalysis =
                      branchAnalysis(
                          flowNode, request, flowNodesToExcludeFromBranchAnalysis, timezone);
                } else {
                  branchAnalysis.setActivityId(flowNode.getId());
                  branchAnalysis.setActivitiesReached(
                      0L); // End event cannot be reached from gateway
                  branchAnalysis.setActivityCount(
                      calculateFlowNodeCount(
                          flowNode.getId(),
                          request,
                          flowNodesToExcludeFromBranchAnalysis,
                          timezone));
                }
                result.getFollowingNodes().put(branchAnalysis.getActivityId(), branchAnalysis);
              }

              result.setEndEvent(request.getEnd());
              result.setTotal(
                  calculateFlowNodeCount(
                      request.getEnd(), request, Collections.emptySet(), timezone));
            });

    return result;
  }

  protected abstract long calculateFlowNodeCount(
      final String flowNodeId,
      final BranchAnalysisRequestDto request,
      final Set<String> activitiesToExclude,
      final ZoneId timezone);

  protected abstract long calculateReachedEndEventFlowNodeCount(
      final String flowNodeId,
      final BranchAnalysisRequestDto request,
      final Set<String> activitiesToExclude,
      final ZoneId timezone);

  private Optional<BpmnModelInstance> getBpmnModelInstance(
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenantIds) {
    final Optional<String> processDefinitionXml =
        tenantIds.stream()
            .map(
                tenantId ->
                    getDefinitionXml(
                        definitionKey, definitionVersions, Collections.singletonList(tenantId)))
            .filter(Optional::isPresent)
            .findFirst()
            .orElseGet(
                () ->
                    getDefinitionXml(
                        definitionKey, definitionVersions, ReportConstants.DEFAULT_TENANT_IDS));

    return processDefinitionXml.map(
        xml -> Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes())));
  }

  private List<FlowNode> fetchGatewayOutcomes(
      final BpmnModelInstance bpmnModelInstance, final String gatewayFlowNodeId) {
    final List<FlowNode> result = new ArrayList<>();
    final FlowNode flowNode = bpmnModelInstance.getModelElementById(gatewayFlowNodeId);
    for (final SequenceFlow sequence : flowNode.getOutgoing()) {
      result.add(sequence.getTarget());
    }
    return result;
  }

  private Set<String> extractFlowNodesWithMultipleIncomingSequenceFlows(
      final BpmnModelInstance bpmnModelInstance) {
    final Collection<SequenceFlow> sequenceFlowCollection =
        bpmnModelInstance.getModelElementsByType(SequenceFlow.class);
    final Set<String> flowNodesWithOneIncomingSequenceFlow = new HashSet<>();
    final Set<String> flowNodeIdsWithMultipleIncomingSequenceFlows = new HashSet<>();
    for (final SequenceFlow sequenceFlow : sequenceFlowCollection) {
      final String targetFlowNodeId = sequenceFlow.getTarget().getId();
      if (flowNodesWithOneIncomingSequenceFlow.contains(targetFlowNodeId)) {
        flowNodeIdsWithMultipleIncomingSequenceFlows.add(targetFlowNodeId);
      } else {
        flowNodesWithOneIncomingSequenceFlow.add(targetFlowNodeId);
      }
    }
    return flowNodeIdsWithMultipleIncomingSequenceFlows;
  }

  private boolean isPathPossible(
      final FlowNode currentNode, final FlowNode targetNode, final Set<FlowNode> visitedNodes) {
    visitedNodes.add(currentNode);
    final List<FlowNode> succeedingNodes = currentNode.getSucceedingNodes().list();
    boolean pathFound = false;
    for (final FlowNode succeedingNode : succeedingNodes) {
      if (visitedNodes.contains(succeedingNode)) {
        continue;
      }
      pathFound =
          succeedingNode.equals(targetNode)
              || isPathPossible(succeedingNode, targetNode, visitedNodes);
      if (pathFound) {
        break;
      }
    }
    return pathFound;
  }

  private Set<String> extractActivitiesToExclude(
      final List<FlowNode> gatewayOutcomes,
      final Set<String> flowNodeIdsWithMultipleIncomingSequenceFlows,
      final String currentFlowNodeId,
      final String endEventFlowNodeId) {
    final Set<String> flowNodesToExcludeFromBranchAnalysis = new HashSet<>();
    for (final FlowNode gatewayOutgoingNode : gatewayOutcomes) {
      final String flowNodeId = gatewayOutgoingNode.getId();
      if (!flowNodeIdsWithMultipleIncomingSequenceFlows.contains(flowNodeId)) {
        flowNodesToExcludeFromBranchAnalysis.add(gatewayOutgoingNode.getId());
      }
    }
    flowNodesToExcludeFromBranchAnalysis.remove(currentFlowNodeId);
    flowNodesToExcludeFromBranchAnalysis.remove(endEventFlowNodeId);
    return flowNodesToExcludeFromBranchAnalysis;
  }

  private BranchAnalysisOutcomeDto branchAnalysis(
      final FlowNode flowNode,
      final BranchAnalysisRequestDto request,
      final Set<String> activitiesToExclude,
      final ZoneId timezone) {

    final BranchAnalysisOutcomeDto result = new BranchAnalysisOutcomeDto();
    result.setActivityId(flowNode.getId());
    result.setActivityCount(
        calculateFlowNodeCount(flowNode.getId(), request, activitiesToExclude, timezone));
    result.setActivitiesReached(
        calculateReachedEndEventFlowNodeCount(
            flowNode.getId(), request, activitiesToExclude, timezone));

    return result;
  }

  private Optional<String> getDefinitionXml(
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenants) {
    final Optional<ProcessDefinitionOptimizeDto> definitionWithXmlAsService =
        definitionService.getDefinitionWithXmlAsService(
            DefinitionType.PROCESS, definitionKey, definitionVersions, tenants);
    return definitionWithXmlAsService.map(ProcessDefinitionOptimizeDto::getBpmn20Xml);
  }
}
