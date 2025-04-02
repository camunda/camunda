/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import isNil from 'lodash/isNil';
import {makeAutoObservable} from 'mobx';
import {flowNodeMetaDataStore} from './flowNodeMetaData';
import {flowNodeSelectionStore} from './flowNodeSelection';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';

type ModificationOption =
  | 'add'
  | 'cancel-all'
  | 'cancel-instance'
  | 'move-all'
  | 'move-instance';

class ModificationRules {
  constructor() {
    makeAutoObservable(this);
  }

  get selectedFlowNodeId() {
    return flowNodeSelectionStore.state.selection?.flowNodeId;
  }

  get selectedFlowNodeInstanceId() {
    return (
      flowNodeSelectionStore.state.selection?.flowNodeInstanceId ??
      flowNodeMetaDataStore.state.metaData?.flowNodeInstanceId
    );
  }

  get canBeModified() {
    if (this.selectedFlowNodeId === undefined) {
      return false;
    }

    return !processInstanceDetailsDiagramStore.nonModifiableFlowNodes.includes(
      this.selectedFlowNodeId,
    );
  }

  get canBeCanceled() {
    if (this.selectedFlowNodeId === undefined || !this.canBeModified) {
      return false;
    }

    const {hasPendingCancelOrMoveModification, selectedRunningInstanceCount} =
      flowNodeSelectionStore;

    return (
      processInstanceDetailsDiagramStore.cancellableFlowNodes.includes(
        this.selectedFlowNodeId,
      ) &&
      !hasPendingCancelOrMoveModification &&
      selectedRunningInstanceCount > 0
    );
  }

  get availableModifications() {
    const options: ModificationOption[] = [];
    const {
      selectedRunningInstanceCount,
      state: {selection},
    } = flowNodeSelectionStore;

    if (this.selectedFlowNodeId === undefined || !this.canBeModified) {
      return options;
    }

    if (
      processInstanceDetailsDiagramStore.appendableFlowNodes.includes(
        this.selectedFlowNodeId,
      ) &&
      !(
        processInstanceDetailsDiagramStore.isMultiInstance(
          this.selectedFlowNodeId,
        ) && !selection?.isMultiInstance
      ) &&
      selection?.flowNodeInstanceId === undefined
    ) {
      options.push('add');
    }

    if (!this.canBeCanceled) {
      return options;
    }

    const isSingleOperationAllowed =
      !isNil(this.selectedFlowNodeInstanceId) &&
      selectedRunningInstanceCount === 1 &&
      !processInstanceDetailsDiagramStore.isSubProcess(this.selectedFlowNodeId);

    if (isSingleOperationAllowed) {
      options.push('cancel-instance');
    } else {
      options.push('cancel-all');
    }

    if (
      processInstanceDetailsDiagramStore.isSubProcess(this.selectedFlowNodeId)
    ) {
      return options;
    }

    if (isSingleOperationAllowed) {
      options.push('move-instance');
    } else {
      options.push('move-all');
    }

    return options;
  }
}

export const modificationRulesStore = new ModificationRules();
export type {ModificationOption};
