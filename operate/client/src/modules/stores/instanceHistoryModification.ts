/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
  parentFlowNodeId?: string,
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
        parentFlowNode?.id,
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
      parentFlowNodeId,
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
  }

  appendExpandedFlowNodeInstanceIds = (id: FlowNodeInstance['id']) => {
    if (
      !this.modificationPlaceholders.some(
        ({parentInstanceId}) => parentInstanceId === id,
      )
    ) {
      return;
    }

    this.state.expandedFlowNodeInstanceIds.push(id);
  };

  removeFromExpandedFlowNodeInstanceIds = (id: FlowNodeInstance['id']) => {
    this.state.expandedFlowNodeInstanceIds =
      this.state.expandedFlowNodeInstanceIds.filter(
        (expandedFlowNodeInstanceId) => expandedFlowNodeInstanceId !== id,
      );
  };

  getVisibleChildPlaceholders = (
    id: FlowNodeInstance['id'],
    flowNodeId: FlowNodeInstance['flowNodeId'],
    isPlaceholder?: boolean,
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
          parentInstanceId === undefined && parentFlowNodeId === flowNodeId,
      )
      .map(({flowNodeInstance}) => flowNodeInstance);
  };

  hasChildPlaceholders = (id: FlowNodeInstance['id']) => {
    return this.modificationPlaceholders.some(
      ({parentInstanceId}) => parentInstanceId === id,
    );
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
  };
}

export const instanceHistoryModificationStore =
  new InstanceHistoryModification();
