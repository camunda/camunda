/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {isMoveModificationTarget} from 'modules/bpmn-js/utils/isMoveModificationTarget';
import {IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED} from 'modules/feature-flags';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {useTotalRunningInstancesByFlowNode} from 'modules/queries/flownodeInstancesStatistics/useTotalRunningInstancesForFlowNode';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {modificationsStore} from 'modules/stores/modifications';
import {hasMultipleScopes} from 'modules/utils/processInstanceDetailsDiagram';

const useFlowNodes = () => {
  const {data: statistics} = useFlownodeInstancesStatistics();
  const {data: totalRunningInstancesByFlowNode} =
    useTotalRunningInstancesByFlowNode();
  const processDefinitionKey = useProcessDefinitionKeyContext();
  const businessObjects =
    useProcessInstanceXml({
      processDefinitionKey: processDefinitionKey,
    }).data?.businessObjects ?? {};

  return Object.values(businessObjects).map((flowNode) => {
    const flowNodeState = statistics?.items.find(
      ({flowNodeId}) => flowNodeId === flowNode.id,
    );

    return {
      id: flowNode.id,
      isCancellable:
        flowNodeState !== undefined &&
        (flowNodeState.active > 0 || flowNodeState.incidents > 0),
      isMoveModificationTarget: isMoveModificationTarget(flowNode),
      hasMultipleScopes: hasMultipleScopes(
        flowNode.$parent,
        totalRunningInstancesByFlowNode,
      ),
    };
  });
};

const useAppendableFlowNodes = () => {
  return useFlowNodes()
    .filter(
      (flowNode) =>
        flowNode.isMoveModificationTarget &&
        ((IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED &&
          modificationsStore.state.status !== 'moving-token') ||
          !flowNode.hasMultipleScopes),
    )
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

export {
  useFlowNodes,
  useAppendableFlowNodes,
  useCancellableFlowNodes,
  useModifiableFlowNodes,
};
