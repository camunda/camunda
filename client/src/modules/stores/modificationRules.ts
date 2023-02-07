/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {isNil} from 'lodash';
import {makeAutoObservable} from 'mobx';
import {flowNodeMetaDataStore} from './flowNodeMetaData';
import {flowNodeSelectionStore} from './flowNodeSelection';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';

type ModificationOption = 'add' | 'cancel-all' | 'cancel-instance' | 'move-all';

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
      this.selectedFlowNodeId
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
        this.selectedFlowNodeId
      ) &&
      !hasPendingCancelOrMoveModification &&
      selectedRunningInstanceCount > 0
    );
  }

  get availableModifications() {
    const options: ModificationOption[] = [];
    const {selectedRunningInstanceCount} = flowNodeSelectionStore;

    if (this.selectedFlowNodeId === undefined || !this.canBeModified) {
      return options;
    }

    if (
      processInstanceDetailsDiagramStore.appendableFlowNodes.includes(
        this.selectedFlowNodeId
      ) &&
      !(
        processInstanceDetailsDiagramStore.isMultiInstance(
          this.selectedFlowNodeId
        ) && !flowNodeSelectionStore.state.selection?.isMultiInstance
      )
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
      !processInstanceDetailsDiagramStore.isSubProcess(this.selectedFlowNodeId)
    ) {
      options.push('move-all');
    }

    return options;
  }
}

export const modificationRulesStore = new ModificationRules();
