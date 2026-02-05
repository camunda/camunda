/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable, when, reaction, type IReactionDisposer} from 'mobx';
import type {FlowNodeInstance} from 'modules/types/operate';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {modificationsStore} from './modifications';

type Selection = {
  flowNodeId?: string;
  flowNodeInstanceId?: FlowNodeInstance['id'];
  flowNodeType?: string;
  isMultiInstance?: boolean;
  isPlaceholder?: boolean;
  processInstanceId?: string;
  anchorFlowNodeId?: string;
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

  /**
   * @deprecated
   * will be migrated to useProcessInstanceElementSelection
   */
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

  /**
   * @deprecated
   * will be migrated to useProcessInstanceElementSelection
   */
  setSelection = (selection: Selection | null) => {
    this.state.selection = selection;
  };

  /**
   * @deprecated
   * will be migrated to useProcessInstanceElementSelection
   */
  clearSelection = () => {
    this.setSelection(this.rootNode);
  };

  /**
   * @deprecated
   * will be migrated to useProcessInstanceElementSelection
   */
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

  /**
   * @deprecated
   * will be migrated to useProcessInstanceElementSelection
   */
  get areMultipleInstancesSelected(): boolean {
    if (this.state.selection === null) {
      return false;
    }

    const {flowNodeInstanceId, flowNodeId} = this.state.selection;
    return flowNodeId !== undefined && flowNodeInstanceId === undefined;
  }

  /**
   * @deprecated
   * will be migrated to useProcessInstanceElementSelection
   */
  get rootNode() {
    return {
      flowNodeInstanceId: processInstanceDetailsStore.state.processInstance?.id,
      isMultiInstance: false,
    };
  }

  /**
   * @deprecated
   * will be migrated to useProcessInstanceElementSelection
   */
  get isRootNodeSelected() {
    return (
      this.state.selection?.flowNodeInstanceId ===
      processInstanceDetailsStore.state.processInstance?.id
    );
  }

  /**
   * @deprecated
   * will be migrated to useProcessInstanceElementSelection
   */
  get selectedFlowNodeId() {
    return this.state.selection?.flowNodeId;
  }

  /**
   * @deprecated
   * will be migrated to useProcessInstanceElementSelection
   */
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

  /**
   * @deprecated
   * will be migrated to useProcessInstanceElementSelection
   */
  reset = () => {
    this.state = {...DEFAULT_STATE};
    this.rootNodeSelectionDisposer?.();
    this.modificationModeChangeDisposer?.();
    this.lastModificationRemovedDisposer?.();
  };
}

/** @deprecated Do not use this store! Use useProcessInstanceElementSelection instead. */
export const flowNodeSelectionStore = new FlowNodeSelection();
export type {Selection};
