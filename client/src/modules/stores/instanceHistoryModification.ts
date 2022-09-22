/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {IReactionDisposer, makeAutoObservable} from 'mobx';
import {FlowNodeInstance} from './flowNodeInstance';
import {modificationsStore, FlowNodeModification} from './modifications';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';

type ModificationPlaceholder = {
  flowNodeInstance: FlowNodeInstance;
  parentFlowNodeId: string;
  operation: FlowNodeModification['payload']['operation'];
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

const createModificationPlaceholders = ({
  modificationPayload,
  flowNode,
}: {
  modificationPayload: FlowNodeModification['payload'];
  // TODO: fix type in #3116
  flowNode: any;
}): ModificationPlaceholder[] => {
  const parentFlowNodeId = flowNode.$parent?.id;

  if (parentFlowNodeId === undefined) {
    return [];
  }

  return getScopeIds(modificationPayload).map((scopeId) => ({
    flowNodeInstance: {
      flowNodeId: flowNode.id,
      id: scopeId,
      type: flowNode.$type,
      startDate: '',
      endDate: null,
      sortValues: [],
      treePath: null,
      isPlaceholder: true,
    },
    parentFlowNodeId,
    operation: modificationPayload.operation,
  }));
};

type State = {
  expandedFlowNodeIds: string[];
};
const DEFAULT_STATE: State = {
  expandedFlowNodeIds: [],
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
      const flowNodeId =
        modificationPayload.operation === 'MOVE_TOKEN'
          ? modificationPayload.targetFlowNode.id
          : modificationPayload.flowNode.id;

      const newModificationPlaceholders = createModificationPlaceholders({
        modificationPayload,
        flowNode: processInstanceDetailsDiagramStore.getFlowNode(flowNodeId),
      });

      return [...modificationPlaceHolders, ...newModificationPlaceholders];
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

  appendPlaceholders = (flowNodeId: string) => {
    if (
      !this.state.expandedFlowNodeIds.some((id: string) => id === flowNodeId)
    ) {
      this.state.expandedFlowNodeIds.push(flowNodeId);
    }
  };

  removePlaceholders = (flowNodeId: string) => {
    const index = this.state.expandedFlowNodeIds.indexOf(flowNodeId);

    if (index >= 0) {
      this.state.expandedFlowNodeIds.splice(index, 1);
    }
  };

  getVisibleFlowNodeInstancesByParent(flowNodeId: string) {
    return this.state.expandedFlowNodeIds.includes(flowNodeId)
      ? this.flowNodeInstancesByParent[flowNodeId] ?? []
      : [];
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
  };
}

export const instanceHistoryModificationStore =
  new InstanceHistoryModification();
