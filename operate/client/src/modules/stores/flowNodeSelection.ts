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

import {IReactionDisposer, makeAutoObservable, when, reaction} from 'mobx';
import {FlowNodeInstance} from './flowNodeInstance';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {modificationsStore} from './modifications';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {processInstanceDetailsStatisticsStore} from './processInstanceDetailsStatistics';
import {flowNodeMetaDataStore} from './flowNodeMetaData';

type Selection = {
  flowNodeId?: string;
  flowNodeInstanceId?: FlowNodeInstance['id'];
  flowNodeType?: string;
  isMultiInstance?: boolean;
  isPlaceholder?: boolean;
};

type State = {
  selection: Selection | null;
};

const DEFAULT_STATE: State = {
  selection: null,
};

class FlowNodeSelection {
  state: State = {...DEFAULT_STATE};
  rootNodeSelectionDisposer: null | IReactionDisposer = null;
  modificationModeChangeDisposer: null | IReactionDisposer = null;
  lastModificationRemovedDisposer: null | IReactionDisposer = null;

  constructor() {
    makeAutoObservable(this, {init: false, selectFlowNode: false});
  }

  init = () => {
    this.rootNodeSelectionDisposer = when(
      () => processInstanceDetailsStore.state.processInstance?.id !== undefined,
      () => this.clearSelection(),
    );

    this.modificationModeChangeDisposer = reaction(
      () => modificationsStore.isModificationModeEnabled,
      this.clearSelection,
    );
    this.lastModificationRemovedDisposer = reaction(
      () => modificationsStore.flowNodeModifications,
      (modificationsNext, modificationsPrev) => {
        if (
          this.state.selection === null ||
          this.isRootNodeSelected ||
          modificationsNext.length >= modificationsPrev.length
        ) {
          return;
        }

        const {flowNodeInstanceId} = this.state.selection;

        if (flowNodeInstanceId === undefined) {
          return;
        }

        const newScopeIds = modificationsStore.flowNodeModifications.reduce<
          string[]
        >((scopeIds, modification) => {
          if (modification.operation === 'ADD_TOKEN') {
            return [
              ...scopeIds,
              ...Object.values(modification.parentScopeIds),
              ...[modification.scopeId],
            ];
          }

          if (modification.operation === 'MOVE_TOKEN') {
            return [
              ...scopeIds,
              ...Object.values(modification.parentScopeIds),
              ...modification.scopeIds,
            ];
          }

          return scopeIds;
        }, []);

        if (!newScopeIds.includes(flowNodeInstanceId)) {
          this.clearSelection();
        }
      },
    );
  };

  setSelection = (selection: Selection | null) => {
    this.state.selection = selection;
  };

  clearSelection = () => {
    this.setSelection(this.rootNode);
  };

  selectFlowNode = (selection: Selection) => {
    if (
      selection.flowNodeId === undefined ||
      (!this.areMultipleInstancesSelected && this.isSelected(selection))
    ) {
      this.clearSelection();
    } else {
      this.setSelection(selection);
    }
  };

  get areMultipleInstancesSelected(): boolean {
    if (this.state.selection === null) {
      return false;
    }

    const {flowNodeInstanceId, flowNodeId} = this.state.selection;
    return flowNodeId !== undefined && flowNodeInstanceId === undefined;
  }

  get rootNode() {
    return {
      flowNodeInstanceId: processInstanceDetailsStore.state.processInstance?.id,
      isMultiInstance: false,
    };
  }

  get isRootNodeSelected() {
    return (
      this.state.selection?.flowNodeInstanceId ===
      processInstanceDetailsStore.state.processInstance?.id
    );
  }

  get isPlaceholderSelected() {
    return (
      this.state.selection?.isPlaceholder ||
      (!this.hasRunningOrFinishedTokens &&
        this.newTokenCountForSelectedNode === 1)
    );
  }

  get selectedRunningInstanceCount() {
    const currentSelection = this.state.selection;
    if (currentSelection === null) {
      return 0;
    }

    if (
      currentSelection.isPlaceholder ||
      this.isRootNodeSelected ||
      currentSelection.flowNodeId === undefined
    ) {
      return 0;
    }

    if (currentSelection.flowNodeInstanceId !== undefined) {
      return flowNodeMetaDataStore.isSelectedInstanceRunning ? 1 : 0;
    }

    return processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode(
      currentSelection.flowNodeId,
    );
  }

  get selectedFlowNodeName() {
    if (
      processInstanceDetailsStore.state.processInstance === null ||
      this.state.selection === null
    ) {
      return '';
    }

    if (this.isRootNodeSelected) {
      return processInstanceDetailsStore.state.processInstance.processName;
    }

    if (this.state.selection.flowNodeId === undefined) {
      return '';
    }

    return processInstanceDetailsDiagramStore.getFlowNodeName(
      this.state.selection.flowNodeId,
    );
  }

  get hasRunningOrFinishedTokens() {
    const currentFlowNodeSelection = this.state.selection;

    return (
      currentFlowNodeSelection?.flowNodeId !== undefined &&
      processInstanceDetailsStatisticsStore.state.statistics.some(
        ({activityId}) => activityId === currentFlowNodeSelection.flowNodeId,
      )
    );
  }

  get newTokenCountForSelectedNode() {
    const currentFlowNodeSelection = this.state.selection;

    const flowNodeId = currentFlowNodeSelection?.flowNodeId;
    if (flowNodeId === undefined) {
      return 0;
    }

    return (
      (modificationsStore.modificationsByFlowNode[flowNodeId]?.newTokens ?? 0) +
      modificationsStore.flowNodeModifications.filter(
        (modification) =>
          modification.operation !== 'CANCEL_TOKEN' &&
          Object.keys(modification.parentScopeIds).includes(flowNodeId),
      ).length
    );
  }

  get hasPendingCancelOrMoveModification() {
    const currentSelection = this.state.selection;

    if (currentSelection === null) {
      return false;
    }

    const {flowNodeId, flowNodeInstanceId} = currentSelection;

    if (this.isRootNodeSelected || flowNodeId === undefined) {
      return processInstanceDetailsStatisticsStore.willAllFlowNodesBeCanceled;
    }

    if (
      modificationsStore.modificationsByFlowNode[flowNodeId]
        ?.areAllTokensCanceled
    ) {
      return true;
    }

    return (
      flowNodeInstanceId !== undefined &&
      modificationsStore.hasPendingCancelOrMoveModification(
        flowNodeId,
        flowNodeInstanceId,
      )
    );
  }

  isSelected = ({
    flowNodeId,
    flowNodeInstanceId,
    isMultiInstance,
  }: {
    flowNodeId?: string;
    flowNodeInstanceId?: string;
    isMultiInstance?: boolean;
  }) => {
    const {selection} = this.state;

    if (selection === null) {
      return false;
    }

    if (selection.isMultiInstance !== isMultiInstance) {
      return false;
    }

    if (selection.flowNodeInstanceId === undefined) {
      return selection.flowNodeId === flowNodeId;
    }

    return selection.flowNodeInstanceId === flowNodeInstanceId;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
    this.rootNodeSelectionDisposer?.();
    this.modificationModeChangeDisposer?.();
    this.lastModificationRemovedDisposer?.();
  };
}

export const flowNodeSelectionStore = new FlowNodeSelection();
export type {Selection};
