/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable, when, type IReactionDisposer} from 'mobx';
import type {BatchOperationQuery} from 'modules/api/processInstances/operations';
import {operationsStore} from './operations';
import {tracking} from 'modules/tracking';
import {notificationsStore} from './notifications';
import {panelStatesStore} from './panelStates';

const STEPS = {
  elementMapping: {
    stepNumber: 1,
    stepDescription: 'mapping elements',
  },
  summary: {
    stepNumber: 2,
    stepDescription: 'confirm',
  },
};

type State = {
  currentStep: 'elementMapping' | 'summary' | null;
  itemMapping: {[sourceId: string]: string};
  selectedSourceItemId?: string;
  selectedTargetItemId?: string;
  selectedInstancesCount: number;
  batchOperationQuery: BatchOperationQuery | null;
  sourceProcessDefinitionKey?: string | null;
  targetProcessDefinitionKey: string | null;
  hasPendingRequest: boolean;
};

const DEFAULT_STATE: State = {
  currentStep: null,
  itemMapping: {},
  selectedSourceItemId: undefined,
  selectedTargetItemId: undefined,
  selectedInstancesCount: 0,
  batchOperationQuery: null,
  targetProcessDefinitionKey: null,
  sourceProcessDefinitionKey: null,
  hasPendingRequest: false,
};

class ProcessInstanceMigration {
  state: State = {...DEFAULT_STATE};
  disposer: IReactionDisposer | null = null;

  constructor() {
    makeAutoObservable(this);
  }

  setCurrentStep = (step: State['currentStep']) => {
    this.state.currentStep = step;
  };

  get currentStep() {
    if (this.state.currentStep === null) {
      return null;
    }

    return STEPS[this.state.currentStep];
  }

  selectSourceFlowNode = (itemId?: string) => {
    this.state.selectedTargetItemId = undefined;

    if (this.state.selectedSourceItemId === itemId) {
      this.state.selectedSourceItemId = undefined;
    } else {
      this.state.selectedSourceItemId = itemId;
    }
  };

  selectTargetItem = (flowNodeId?: string) => {
    this.state.selectedSourceItemId = undefined;

    if (this.state.selectedTargetItemId === flowNodeId) {
      this.state.selectedTargetItemId = undefined;
    } else {
      this.state.selectedTargetItemId = flowNodeId;
    }
  };

  getAllSourceElements = (targetFlowNodeId: string) => {
    return Object.entries(this.state.itemMapping)
      .filter(([_, t]) => t === targetFlowNodeId)
      .map(([s, _]) => {
        return s;
      });
  };

  get selectedSourceItemIds() {
    const {selectedSourceItemId, selectedTargetItemId} = this.state;

    if (selectedSourceItemId !== undefined) {
      const targetFlowNodeId = this.state.itemMapping[selectedSourceItemId];

      if (targetFlowNodeId !== undefined) {
        return this.getAllSourceElements(targetFlowNodeId);
      }

      return [selectedSourceItemId];
    } else if (selectedTargetItemId !== undefined) {
      return this.getAllSourceElements(selectedTargetItemId);
    }
    return undefined;
  }

  get selectedTargetItemId() {
    const {selectedTargetItemId, selectedSourceItemId} = this.state;
    if (selectedTargetItemId !== undefined) {
      return selectedTargetItemId;
    } else if (selectedSourceItemId !== undefined) {
      return this.state.itemMapping[selectedSourceItemId];
    }
    return undefined;
  }

  enable = () => {
    this.state.currentStep = 'elementMapping';
  };

  disable = () => {
    this.state.currentStep = null;
  };

  reset = () => {
    this.disposer?.();
    this.state = {...DEFAULT_STATE};
  };

  get isSummaryStep() {
    return this.state.currentStep === 'summary';
  }

  get isEnabled() {
    return this.state.currentStep !== null;
  }

  get hasItemMapping() {
    return Object.keys(this.state.itemMapping).length > 0;
  }

  setSelectedInstancesCount = (selectedInstancesCount: number) => {
    this.state.selectedInstancesCount = selectedInstancesCount;
  };

  setBatchOperationQuery = (query: BatchOperationQuery) => {
    this.state.batchOperationQuery = query;
  };

  updateItemMapping = ({
    sourceId,
    targetId,
  }: {
    sourceId: string;
    targetId?: string;
  }) => {
    if (targetId === undefined || targetId === '') {
      delete this.state.itemMapping[sourceId];
    } else {
      this.state.itemMapping[sourceId] = targetId;
    }
  };

  clearItemMapping = () => {
    this.state.itemMapping = {};
  };

  setTargetProcessDefinitionKey = (
    key: State['targetProcessDefinitionKey'],
  ) => {
    this.state.targetProcessDefinitionKey = key;
  };

  setSourceProcessDefinitionKey = (
    key: State['sourceProcessDefinitionKey'],
  ) => {
    this.state.sourceProcessDefinitionKey = key;
  };

  setHasPendingRequest = () => {
    if (!this.state.hasPendingRequest) {
      this.state.hasPendingRequest = true;

      this.disposer = when(
        () => operationsStore.state.status === 'fetched',
        () => {
          panelStatesStore.expandOperationsPanel();
          this.requestBatchProcess();
          this.state.hasPendingRequest = false;
        },
      );
    }
  };

  requestBatchProcess = () => {
    const {batchOperationQuery} = processInstanceMigrationStore.state;
    if (batchOperationQuery === null) {
      return;
    }

    const {targetProcessDefinitionKey} = this.state;
    if (targetProcessDefinitionKey === null) {
      return;
    }

    operationsStore.applyBatchOperation({
      operationType: 'MIGRATE_PROCESS_INSTANCE',
      query: batchOperationQuery,
      migrationPlan: {
        targetProcessDefinitionKey,
        mappingInstructions: Object.entries(
          processInstanceMigrationStore.state.itemMapping,
        ).map(([sourceElementId, targetElementId]) => ({
          sourceElementId,
          targetElementId,
        })),
      },
      onSuccess: () => {
        tracking.track({
          eventName: 'batch-operation',
          operationType: 'MIGRATE_PROCESS_INSTANCE',
        });
      },
      onError: ({statusCode}) =>
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Operation could not be created',
          subtitle:
            statusCode === 403 ? 'You do not have permission' : undefined,
          isDismissable: true,
        }),
    });
  };

  resetItemMapping = () => {
    this.state.itemMapping = {...DEFAULT_STATE.itemMapping};
  };
}

export const processInstanceMigrationStore = new ProcessInstanceMigration();
