/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IReactionDisposer, makeAutoObservable, when} from 'mobx';
import {BatchOperationQuery} from 'modules/api/processInstances/operations';
import {operationsStore} from './operations';
import {tracking} from 'modules/tracking';
import {notificationsStore} from './notifications';
import {panelStatesStore} from './panelStates';
import {processStatisticsStore} from './processStatistics/processStatistics.migration.source';

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
  flowNodeMapping: {[sourceId: string]: string};
  selectedSourceFlowNodeId?: string;
  selectedTargetFlowNodeId?: string;
  selectedInstancesCount: number;
  batchOperationQuery: BatchOperationQuery | null;
  sourceProcessDefinitionKey?: string | null;
  targetProcessDefinitionKey: string | null;
  hasPendingRequest: boolean;
};

const DEFAULT_STATE: State = {
  currentStep: null,
  flowNodeMapping: {},
  selectedSourceFlowNodeId: undefined,
  selectedTargetFlowNodeId: undefined,
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

  selectSourceFlowNode = (flowNodeId?: string) => {
    this.state.selectedTargetFlowNodeId = undefined;

    if (this.state.selectedSourceFlowNodeId === flowNodeId) {
      this.state.selectedSourceFlowNodeId = undefined;
    } else {
      this.state.selectedSourceFlowNodeId = flowNodeId;
    }
  };

  selectTargetFlowNode = (flowNodeId?: string) => {
    this.state.selectedSourceFlowNodeId = undefined;

    if (this.state.selectedTargetFlowNodeId === flowNodeId) {
      this.state.selectedTargetFlowNodeId = undefined;
    } else {
      this.state.selectedTargetFlowNodeId = flowNodeId;
    }
  };

  getAllSourceElements = (targetFlowNodeId: string) => {
    return Object.entries(this.state.flowNodeMapping)
      .filter(([_, t]) => t === targetFlowNodeId)
      .map(([s, _]) => {
        return s;
      });
  };

  get selectedSourceFlowNodeIds() {
    const {selectedSourceFlowNodeId, selectedTargetFlowNodeId} = this.state;

    if (selectedSourceFlowNodeId !== undefined) {
      const targetFlowNodeId =
        this.state.flowNodeMapping[selectedSourceFlowNodeId];

      if (targetFlowNodeId !== undefined) {
        return this.getAllSourceElements(targetFlowNodeId);
      }

      return [selectedSourceFlowNodeId];
    } else if (selectedTargetFlowNodeId !== undefined) {
      return this.getAllSourceElements(selectedTargetFlowNodeId);
    }
    return undefined;
  }

  get selectedTargetFlowNodeId() {
    const {selectedTargetFlowNodeId, selectedSourceFlowNodeId} = this.state;
    if (selectedTargetFlowNodeId !== undefined) {
      return selectedTargetFlowNodeId;
    } else if (selectedSourceFlowNodeId !== undefined) {
      return this.state.flowNodeMapping[selectedSourceFlowNodeId];
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

  get hasFlowNodeMapping() {
    return Object.keys(this.state.flowNodeMapping).length > 0;
  }

  setSelectedInstancesCount = (selectedInstancesCount: number) => {
    this.state.selectedInstancesCount = selectedInstancesCount;
  };

  setBatchOperationQuery = (query: BatchOperationQuery) => {
    this.state.batchOperationQuery = query;
  };

  updateFlowNodeMapping = ({
    sourceId,
    targetId,
  }: {
    sourceId: string;
    targetId?: string;
  }) => {
    if (targetId === undefined || targetId === '') {
      delete this.state.flowNodeMapping[sourceId];
    } else {
      this.state.flowNodeMapping[sourceId] = targetId;
    }
  };

  clearFlowNodeMapping = () => {
    this.state.flowNodeMapping = {};
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

  get flowNodeCountByTargetId() {
    return Object.entries(this.state.flowNodeMapping).reduce<{
      [targetElementId: string]: number;
    }>((mappingByTarget, [sourceElementId, targetElementId]) => {
      const previousCount = mappingByTarget[targetElementId];
      const newCount = processStatisticsStore.flowNodeStates
        .filter((state) => {
          return (
            state.flowNodeId === sourceElementId &&
            ['active', 'incidents'].includes(state.flowNodeState)
          );
        })
        .reduce((count, state) => {
          return count + state.count;
        }, 0);

      return {
        ...mappingByTarget,
        [targetElementId]:
          previousCount !== undefined ? previousCount + newCount : newCount,
      };
    }, {});
  }

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
          processInstanceMigrationStore.state.flowNodeMapping,
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

  resetFlowNodeMapping = () => {
    this.state.flowNodeMapping = {...DEFAULT_STATE.flowNodeMapping};
  };
}

export const processInstanceMigrationStore = new ProcessInstanceMigration();
