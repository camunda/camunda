/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {IReactionDisposer, makeAutoObservable} from 'mobx';
import {FlowNodeInstance} from './flowNodeInstance';
import {modificationsStore, FlowNodeModification} from './modifications';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {processInstanceDetailsStore} from './processInstanceDetails';

type ModificationPlaceholder = {
  flowNodeInstance: FlowNodeInstance;
  parentFlowNodeId?: string;
  operation: FlowNodeModification['payload']['operation'];
  parentInstanceId?: string;
};

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

const getParentInstanceIdForPlaceholder = (
  modificationPayload: FlowNodeModification['payload'],
  parentFlowNodeId?: string
) => {
  if (
    parentFlowNodeId === undefined ||
    modificationPayload.operation === 'CANCEL_TOKEN'
  ) {
    return undefined;
  }

  if (
    parentFlowNodeId ===
    processInstanceDetailsStore.state.processInstance?.bpmnProcessId
  ) {
    return processInstanceDetailsStore.state.processInstance?.id;
  }

  if (
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

const generateParentPlaceholders = (
  modificationPayload: FlowNodeModification['payload'],
  flowNode?: BusinessObject
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
    ...generateParentPlaceholders(modificationPayload, parentFlowNode),
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
        parentFlowNode?.id
      ),
    },
  ];
};

const createModificationPlaceholders = ({
  modificationPayload,
  flowNode,
}: {
  modificationPayload: FlowNodeModification['payload'];
  flowNode: BusinessObject;
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
      parentFlowNodeId
    ),
  }));
};

type State = {
  expandedFlowNodeInstanceIds: string[];
};

const DEFAULT_STATE: State = {
  expandedFlowNodeInstanceIds: [],
};

class InstanceHistoryModification {
  state: State = {...DEFAULT_STATE};
  disposer: null | IReactionDisposer = null;

  constructor() {
    makeAutoObservable(this);
  }

  get modificationPlaceholders() {
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

      const flowNode =
        processInstanceDetailsDiagramStore.businessObjects[flowNodeId];

      if (flowNode === undefined) {
        return modificationPlaceHolders;
      }

      const newParentModificationPlaceholders = generateParentPlaceholders(
        modificationPayload,
        flowNode.$parent
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
  }

  appendExpandedFlowNodeInstanceIds = (id: FlowNodeInstance['id']) => {
    if (
      !this.modificationPlaceholders.some(
        ({parentInstanceId}) => parentInstanceId === id
      )
    ) {
      return;
    }

    this.state.expandedFlowNodeInstanceIds.push(id);
  };

  removeFromExpandedFlowNodeInstanceIds = (id: FlowNodeInstance['id']) => {
    this.state.expandedFlowNodeInstanceIds =
      this.state.expandedFlowNodeInstanceIds.filter(
        (expandedFlowNodeInstanceId) => expandedFlowNodeInstanceId !== id
      );
  };

  getVisibleChildPlaceholders = (
    id: FlowNodeInstance['id'],
    flowNodeId: FlowNodeInstance['flowNodeId'],
    isPlaceholder?: boolean
  ) => {
    if (isPlaceholder && !this.state.expandedFlowNodeInstanceIds.includes(id)) {
      return [];
    }

    const placeholders = this.modificationPlaceholders
      .filter(({parentInstanceId}) => parentInstanceId === id)
      .map(({flowNodeInstance}) => flowNodeInstance);

    if (placeholders.length > 0) {
      return placeholders;
    }

    return instanceHistoryModificationStore.modificationPlaceholders
      .filter(
        ({parentInstanceId, parentFlowNodeId}) =>
          parentInstanceId === undefined && parentFlowNodeId === flowNodeId
      )
      .map(({flowNodeInstance}) => flowNodeInstance);
  };

  hasChildPlaceholders = (id: FlowNodeInstance['id']) => {
    return this.modificationPlaceholders.some(
      ({parentInstanceId}) => parentInstanceId === id
    );
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
  };
}

export const instanceHistoryModificationStore =
  new InstanceHistoryModification();
