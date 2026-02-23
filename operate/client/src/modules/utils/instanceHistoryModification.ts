/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  BusinessObject,
  BusinessObjects,
} from 'bpmn-js/lib/NavigatedViewer';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import type {FlowNodeInstance} from 'modules/types/operate';
import {
  instanceHistoryModificationStore,
  type ModificationPlaceholder,
} from 'modules/stores/instanceHistoryModification';

import {
  modificationsStore,
  type FlowNodeModification,
} from 'modules/stores/modifications';

const getScopeIds = (modificationPayload: FlowNodeModification['payload']) => {
  const {operation} = modificationPayload;

  switch (operation) {
    case 'ADD_TOKEN':
      return [modificationPayload.scopeId];
    case 'MOVE_TOKEN':
      return modificationPayload.scopeIds;
    default:
      return [];
  }
};

const generateParentPlaceholders = (
  modificationPayload: FlowNodeModification['payload'],
  processDefinitionId?: string,
  processInstanceKey?: string,
  flowNode?: BusinessObject,
): ModificationPlaceholder[] => {
  if (
    flowNode === undefined ||
    modificationPayload.operation === 'CANCEL_TOKEN'
  ) {
    return [];
  }

  const scopeId = modificationPayload.parentScopeIds[flowNode.id];
  if (scopeId === undefined) {
    return [];
  }

  const parentFlowNode = flowNode?.$parent;

  return [
    ...generateParentPlaceholders(
      modificationPayload,
      processDefinitionId,
      processInstanceKey,
      parentFlowNode,
    ),
    {
      flowNodeInstance: {
        flowNodeId: flowNode.id,
        id: scopeId,
        type: 'SUB_PROCESS',
        startDate: '',
        endDate: null,
        sortValues: [],
        treePath: '',
        isPlaceholder: true,
      },
      operation: modificationPayload.operation,
      parentFlowNodeId: parentFlowNode?.id,
      parentInstanceId: getParentInstanceIdForPlaceholder(
        modificationPayload,
        processDefinitionId,
        processInstanceKey,
        parentFlowNode?.id,
      ),
    },
  ];
};

const getParentInstanceIdForPlaceholder = (
  modificationPayload: FlowNodeModification['payload'],
  processDefinitionId?: string,
  processInstanceKey?: string,
  parentFlowNodeId?: string,
) => {
  if (
    parentFlowNodeId === undefined ||
    modificationPayload.operation === 'CANCEL_TOKEN'
  ) {
    return undefined;
  }

  if (parentFlowNodeId === processDefinitionId) {
    return processInstanceKey;
  }

  if (
    'ancestorElement' in modificationPayload &&
    modificationPayload.ancestorElement !== undefined &&
    parentFlowNodeId === modificationPayload.ancestorElement.flowNodeId
  ) {
    return modificationPayload.ancestorElement.instanceKey;
  }

  return (
    modificationPayload.parentScopeIds[parentFlowNodeId] ??
    modificationsStore.getParentScopeId(parentFlowNodeId)
  );
};

const createModificationPlaceholders = ({
  modificationPayload,
  flowNode,
  processDefinitionId,
  processInstanceKey,
}: {
  modificationPayload: FlowNodeModification['payload'];
  flowNode: BusinessObject;
  processDefinitionId?: string;
  processInstanceKey?: string;
}): ModificationPlaceholder[] => {
  const parentFlowNodeId = flowNode.$parent?.id;

  if (
    parentFlowNodeId === undefined ||
    modificationPayload.operation === 'CANCEL_TOKEN'
  ) {
    return [];
  }

  return getScopeIds(modificationPayload).map((scopeId) => ({
    flowNodeInstance: {
      flowNodeId: flowNode.id,
      id: scopeId,
      type: isMultiInstance(flowNode) ? 'MULTI_INSTANCE_BODY' : flowNode.$type,
      startDate: '',
      endDate: null,
      sortValues: [],
      treePath: '',
      isPlaceholder: true,
    },
    operation: modificationPayload.operation,
    parentFlowNodeId,
    parentInstanceId: getParentInstanceIdForPlaceholder(
      modificationPayload,
      processDefinitionId,
      processInstanceKey,
      parentFlowNodeId,
    ),
  }));
};

const getModificationPlaceholders = (
  businessObjects: BusinessObjects,
  processDefinitionId?: string,
  processInstanceKey?: string,
) => {
  return modificationsStore.flowNodeModifications.reduce<
    ModificationPlaceholder[]
  >((modificationPlaceHolders, modificationPayload) => {
    const {operation} = modificationPayload;
    if (operation === 'CANCEL_TOKEN') {
      return modificationPlaceHolders;
    }

    const flowNodeId =
      operation === 'MOVE_TOKEN'
        ? modificationPayload.targetFlowNode.id
        : modificationPayload.flowNode.id;

    const flowNode = businessObjects[flowNodeId];

    if (flowNode === undefined) {
      return modificationPlaceHolders;
    }

    const newParentModificationPlaceholders = generateParentPlaceholders(
      modificationPayload,
      processDefinitionId,
      processInstanceKey,
      flowNode.$parent,
    );

    const newModificationPlaceholders = createModificationPlaceholders({
      modificationPayload,
      flowNode,
    });

    return [
      ...modificationPlaceHolders,
      ...newModificationPlaceholders,
      ...newParentModificationPlaceholders,
    ];
  }, []);
};

const getVisibleChildPlaceholders = (
  id: FlowNodeInstance['id'],
  flowNodeId: FlowNodeInstance['flowNodeId'],
  businessObjects: BusinessObjects,
  processDefinitionId?: string,
  processInstanceKey?: string,
  isPlaceholder?: boolean,
) => {
  if (
    isPlaceholder &&
    !instanceHistoryModificationStore.state.expandedFlowNodeInstanceIds.includes(
      id,
    )
  ) {
    return [];
  }

  const placeholders = getModificationPlaceholders(
    businessObjects,
    processDefinitionId,
    processInstanceKey,
  )
    .filter(({parentInstanceId}) => parentInstanceId === id)
    .map(({flowNodeInstance}) => flowNodeInstance);

  if (placeholders.length > 0) {
    return placeholders;
  }

  return getModificationPlaceholders(
    businessObjects,
    processDefinitionId,
    processInstanceKey,
  )
    .filter(
      ({parentInstanceId, parentFlowNodeId}) =>
        parentInstanceId === undefined && parentFlowNodeId === flowNodeId,
    )
    .map(({flowNodeInstance}) => flowNodeInstance);
};

const hasChildPlaceholders = (
  id: FlowNodeInstance['id'],
  businessObjects: BusinessObjects,
  processDefinitionId?: string,
  processInstanceKey?: string,
) => {
  return getModificationPlaceholders(
    businessObjects,
    processDefinitionId,
    processInstanceKey,
  ).some(({parentInstanceId}) => parentInstanceId === id);
};

export {hasChildPlaceholders, getVisibleChildPlaceholders};
