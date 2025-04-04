/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * DEPRECATED: The `flowNodeSelectionStore` is being deprecated and replaced with
 * utility functions and hooks in `flowNodeSelection.ts` as part of the Operate v2 migration.
 *
 * Please avoid adding new functionality to this store and migrate existing logic
 * to the new utils and hooks where possible.
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
