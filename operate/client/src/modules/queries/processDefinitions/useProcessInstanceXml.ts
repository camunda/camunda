/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isCompensationAssociation} from 'modules/bpmn-js/utils/isCompensationAssociation';
import {
  ParsedXmlData,
  useProcessDefinitionXml,
} from './useProcessDefinitionXml';
import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {modificationsStore} from 'modules/stores/modifications';
import {IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED} from 'modules/feature-flags';
import {isFlowNode} from 'modules/utils/flowNodes';
import {isMoveModificationTarget} from 'modules/bpmn-js/utils/isMoveModificationTarget';
import {ProcessInstanceDetailStatisticsDto} from 'modules/api/processInstances/fetchProcessInstanceDetailStatistics';

type ExtendedParsedXmlData = ParsedXmlData & {
  compensationAssociations: Array<BusinessObject>;
  modifiableFlowNodes: string[];
};

type ProcessInstanceXmlParserParams = ParsedXmlData & {
  processInstanceStatistics: ProcessInstanceDetailStatisticsDto[];
  getTotalRunningInstancesForFlowNode: (flowNodeId: string) => number;
};

function processInstanceXmlParser({
  xml,
  diagramModel,
  selectableFlowNodes,
  processInstanceStatistics,
  getTotalRunningInstancesForFlowNode,
}: ProcessInstanceXmlParserParams) {
  const businessObjects: {[flowNodeId: string]: BusinessObject} =
    Object.entries(diagramModel.elementsById).reduce(
      (flowNodes, [flowNodeId, businessObject]) => {
        if (isFlowNode(businessObject)) {
          return {...flowNodes, [flowNodeId]: businessObject};
        } else {
          return flowNodes;
        }
      },
      {},
    );

  const hasMultipleScopes = (parentFlowNode?: BusinessObject): boolean => {
    if (parentFlowNode === undefined) {
      return false;
    }

    const scopeCount = getTotalRunningInstancesForFlowNode(parentFlowNode.id);

    if (scopeCount > 1) {
      return true;
    }

    if (parentFlowNode.$parent?.$type !== 'bpmn:SubProcess') {
      return false;
    }

    return hasMultipleScopes(parentFlowNode.$parent);
  };

  const flowNodes = Object.values(businessObjects).map((flowNode) => {
    const flowNodeState = processInstanceStatistics.find(
      ({activityId}) => activityId === flowNode.id,
    );

    return {
      id: flowNode.id,
      isCancellable:
        flowNodeState !== undefined &&
        (flowNodeState.active > 0 || flowNodeState.incidents > 0),
      isMoveModificationTarget: isMoveModificationTarget(flowNode),
      hasMultipleScopes: hasMultipleScopes(flowNode.$parent),
    };
  });

  const cancellableFlowNodes = flowNodes
    .filter((flowNode) => flowNode.isCancellable)
    .map(({id}) => id);

  const appendableFlowNodes = flowNodes
    .filter(
      (flowNode) =>
        flowNode.isMoveModificationTarget &&
        ((IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED &&
          modificationsStore.state.status !== 'moving-token') ||
          !flowNode.hasMultipleScopes),
    )
    .map(({id}) => id);

  const modifiableFlowNodes =
    modificationsStore.state.status === 'moving-token'
      ? appendableFlowNodes.filter(
          (flowNodeId) =>
            flowNodeId !==
            modificationsStore.state.sourceFlowNodeIdForMoveOperation,
        )
      : Array.from(new Set([...appendableFlowNodes, ...cancellableFlowNodes]));

  return {
    xml,
    diagramModel,
    selectableFlowNodes,
    compensationAssociations: Object.values(diagramModel.elementsById).filter(
      isCompensationAssociation,
    ),
    modifiableFlowNodes,
  };
}

function useProcessInstanceXml({
  processDefinitionKey,
  processInstanceStatistics,
  getTotalRunningInstancesForFlowNode,
}: {
  processDefinitionKey?: string;
  processInstanceStatistics: ProcessInstanceDetailStatisticsDto[];
  getTotalRunningInstancesForFlowNode: (flowNodeId: string) => number;
}) {
  return useProcessDefinitionXml<ExtendedParsedXmlData>({
    processDefinitionKey,
    select: (params) =>
      processInstanceXmlParser({
        ...params,
        processInstanceStatistics,
        getTotalRunningInstancesForFlowNode,
      }),
    enabled: !!processDefinitionKey,
  });
}

export {useProcessInstanceXml};
