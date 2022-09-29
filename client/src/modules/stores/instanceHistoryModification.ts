/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {IReactionDisposer, makeAutoObservable} from 'mobx';
import {TYPE} from 'modules/constants';
import {FlowNodeInstance} from './flowNodeInstance';
import {modificationsStore, FlowNodeModification} from './modifications';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {isMultiInstance} from 'modules/bpmn-js/isMultiInstance';

type ModificationPlaceholder = {
  flowNodeInstance: FlowNodeInstance;
  parentFlowNodeId?: string;
  operation: FlowNodeModification['payload']['operation'];
  parentPlaceholerInstanceId: string | null;
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
  const parentScopeId =
    parentFlowNode !== undefined
      ? modificationPayload.parentScopeIds[parentFlowNode.id] ?? null
      : null;

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
      parentFlowNodeId: parentFlowNode?.id,
      operation: modificationPayload.operation,
      parentPlaceholerInstanceId: parentScopeId,
    },
  ];
};

const createModificationPlaceholders = ({
  modificationPayload,
  flowNode,
}: {
  modificationPayload: FlowNodeModification['payload'];
  // TODO: fix type in #3116
  flowNode: any;
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
      type: isMultiInstance(flowNode)
        ? TYPE.MULTI_INSTANCE_BODY
        : flowNode.$type,
      startDate: '',
      endDate: null,
      sortValues: [],
      treePath: '',
      isPlaceholder: true,
    },
    parentFlowNodeId,
    operation: modificationPayload.operation,
    parentPlaceholerInstanceId:
      modificationsStore.getParentScopeId(parentFlowNodeId),
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
        processInstanceDetailsDiagramStore.getFlowNode(flowNodeId);

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

  get flowNodeInstancesByParent() {
    return this.modificationPlaceholders.reduce<{
      [parentFlowNodeId: string]: FlowNodeInstance[];
    }>((flowNodeInstances, modification) => {
      const {parentFlowNodeId} = modification;

      if (parentFlowNodeId) {
        flowNodeInstances[parentFlowNodeId] = [
          ...(flowNodeInstances[parentFlowNodeId] ?? []),
          modification.flowNodeInstance,
        ];
      }
      return flowNodeInstances;
    }, {});
  }

  appendExpandedFlowNodeInstanceIds = (id: FlowNodeInstance['id']) => {
    if (
      !this.modificationPlaceholders.some(
        ({parentPlaceholerInstanceId}) => parentPlaceholerInstanceId === id
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

  getVisibleChildPlaceholders = (id: FlowNodeInstance['id']) => {
    if (!this.state.expandedFlowNodeInstanceIds.includes(id)) {
      return [];
    }

    return this.modificationPlaceholders
      .filter(
        ({parentPlaceholerInstanceId}) => parentPlaceholerInstanceId === id
      )
      .map(({flowNodeInstance}) => flowNodeInstance);
  };

  hasChildPlaceholders = (id: FlowNodeInstance['id']) => {
    return this.modificationPlaceholders.some(
      ({parentPlaceholerInstanceId}) => parentPlaceholerInstanceId === id
    );
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
  };
}

export const instanceHistoryModificationStore =
  new InstanceHistoryModification();
