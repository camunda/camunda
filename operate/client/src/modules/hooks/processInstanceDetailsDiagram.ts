/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isMoveModificationTarget} from 'modules/bpmn-js/utils/isMoveModificationTarget';
import {getFirstMultiInstanceParent} from 'modules/bpmn-js/utils/isWithinMultiInstance';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {useTotalRunningInstancesByFlowNode} from 'modules/queries/flownodeInstancesStatistics/useTotalRunningInstancesForFlowNode';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {modificationsStore} from 'modules/stores/modifications';
import {
  hasMultipleScopes,
  hasSingleScope,
} from 'modules/utils/processInstanceDetailsDiagram';

const useFlowNodes = () => {
  const {data: statistics} = useFlownodeInstancesStatistics();
  const {data: totalRunningInstancesByFlowNode} =
    useTotalRunningInstancesByFlowNode();
  const {data: businessObjects} = useBusinessObjects();

  return Object.values(businessObjects ?? {}).map((flowNode) => {
    const firstMultiInstanceParent = getFirstMultiInstanceParent(flowNode);
    const flowNodeState = statistics?.items.find(
      ({elementId}) => elementId === flowNode.id,
    );

    return {
      id: flowNode.id,
      isCancellable:
        flowNodeState !== undefined &&
        (flowNodeState.active > 0 || flowNodeState.incidents > 0),
      isMoveModificationTarget: isMoveModificationTarget(flowNode),
      firstMultiInstanceParent,
      hasMultipleScopes: hasMultipleScopes(
        flowNode.$parent,
        totalRunningInstancesByFlowNode,
      ),
      hasSingleScope:
        !firstMultiInstanceParent ||
        hasSingleScope(flowNode.$parent, totalRunningInstancesByFlowNode),
    };
  });
};

const useAppendableFlowNodes = () => {
  const flowNodes = useFlowNodes();
  const {
    state: {status, sourceFlowNodeIdForMoveOperation},
    isMoveAllOperation,
  } = modificationsStore;

  const sourceMultiInstanceParent = flowNodes.find(
    ({id}) => id === sourceFlowNodeIdForMoveOperation,
  )?.firstMultiInstanceParent;

  return flowNodes
    .filter((flowNode) => {
      if (!flowNode.isMoveModificationTarget) {
        return false;
      }

      // Add token
      if (status !== 'moving-token') {
        return flowNode.hasSingleScope && !flowNode.hasMultipleScopes;
      }

      // Moving token is allowed for 1 scope
      if (flowNode.hasSingleScope) {
        return true;
      }

      // Moving multiple tokens to another element inside the multi-instance is not allowed
      if (isMoveAllOperation) {
        return false;
      }

      // Moving to/from different multi-instance parents is not allowed
      if (sourceMultiInstanceParent !== flowNode.firstMultiInstanceParent) {
        return false;
      }

      return true;
    })
    .map(({id}) => id);
};

const useCancellableFlowNodes = () => {
  return useFlowNodes()
    .filter((flowNode) => flowNode.isCancellable)
    .map(({id}) => id);
};

const useModifiableFlowNodes = () => {
  const appendableFlowNodes = useAppendableFlowNodes();
  const cancellableFlowNodes = useCancellableFlowNodes();

  if (modificationsStore.state.status === 'moving-token') {
    return appendableFlowNodes.filter(
      (flowNodeId) =>
        flowNodeId !==
        modificationsStore.state.sourceFlowNodeIdForMoveOperation,
    );
  } else {
    return Array.from(
      new Set([...appendableFlowNodes, ...cancellableFlowNodes]),
    );
  }
};

const useNonModifiableFlowNodes = () => {
  const flowNodes = useFlowNodes();
  const modifiableFlowNodes = useModifiableFlowNodes();

  return flowNodes
    .filter((flowNode) => !modifiableFlowNodes.includes(flowNode.id))
    .map(({id}) => id);
};

export {
  useAppendableFlowNodes,
  useCancellableFlowNodes,
  useModifiableFlowNodes,
  useNonModifiableFlowNodes,
};
