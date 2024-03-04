/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
    stepDescription: 'Mapping elements',
  },
  summary: {
    stepNumber: 2,
    stepDescription: 'Confirm',
  },
};

type State = {
  currentStep: 'elementMapping' | 'summary' | null;
  flowNodeMapping: {[sourceId: string]: string};
  selectedSourceFlowNodeId?: string;
  selectedTargetFlowNodeId?: string;
  selectedInstancesCount: number;
  batchOperationQuery: BatchOperationQuery | null;
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

  setTargetProcessDefinitionKey = (
    key: State['targetProcessDefinitionKey'],
  ) => {
    this.state.targetProcessDefinitionKey = key;
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
