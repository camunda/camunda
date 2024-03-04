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

import {
  makeObservable,
  when,
  IReactionDisposer,
  override,
  action,
  observable,
  computed,
} from 'mobx';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {
  fetchProcessInstanceDetailStatistics,
  ProcessInstanceDetailStatisticsDto,
} from 'modules/api/processInstances/fetchProcessInstanceDetailStatistics';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {modificationsStore} from './modifications';
import {isProcessEndEvent} from 'modules/bpmn-js/utils/isProcessEndEvent';
import isEqual from 'lodash/isEqual';

type Statistic = ProcessInstanceDetailStatisticsDto & {filteredActive: number};

type State = {
  statistics: ProcessInstanceDetailStatisticsDto[];
  status: 'initial' | 'fetched' | 'error';
};
const DEFAULT_STATE: State = {
  statistics: [],
  status: 'initial',
};

class ProcessInstanceDetailsStatistics extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  isPollRequestRunning: boolean = false;
  intervalId: null | ReturnType<typeof setInterval> = null;
  processInstanceDetailsStatisticsDisposer: null | IReactionDisposer = null;
  completedFlowNodesDisposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      handleFetchSuccess: action,
      handleFetchFailure: action,
      flowNodeStatistics: computed,
      statisticsByFlowNode: computed,
      willAllFlowNodesBeCanceled: computed,
      selectableFlowNodes: computed,
      reset: override,
    });
  }

  init = (processInstanceId: string) => {
    this.processInstanceDetailsStatisticsDisposer = when(
      () => processInstanceDetailsStore.state.processInstance !== null,
      () => {
        this.fetchFlowNodeStatistics(processInstanceId);
        this.startPolling(processInstanceId);
      },
    );
    this.completedFlowNodesDisposer = when(
      () =>
        processInstanceDetailsStore.state.processInstance !== null &&
        !processInstanceDetailsStore.isRunning,
      () => {
        this.stopPolling();
      },
    );
  };

  fetchFlowNodeStatistics = this.retryOnConnectionLost(
    async (processInstanceId: string) => {
      const response =
        await fetchProcessInstanceDetailStatistics(processInstanceId);

      if (response.isSuccess) {
        this.handleFetchSuccess(response.data);
      } else {
        this.handleFetchFailure();
      }
    },
  );

  handlePolling = async (processInstanceId: string) => {
    this.isPollRequestRunning = true;
    const response =
      await fetchProcessInstanceDetailStatistics(processInstanceId);

    if (this.intervalId !== null) {
      if (response.isSuccess) {
        this.handleFetchSuccess(response.data);
      } else {
        this.handleFetchFailure();
      }
    }

    this.isPollRequestRunning = false;
  };

  handleFetchSuccess = (statistics: ProcessInstanceDetailStatisticsDto[]) => {
    if (!isEqual(this.state.statistics, statistics)) {
      this.state.statistics = statistics;
    }
    this.state.status = 'fetched';
  };

  handleFetchFailure = () => {
    this.state.status = 'error';
  };

  startPolling = (
    processInstanceId: string,
    options: {runImmediately?: boolean} = {runImmediately: false},
  ) => {
    if (
      document.visibilityState === 'hidden' ||
      !processInstanceDetailsStore.isRunning
    ) {
      return;
    }

    if (options.runImmediately) {
      this.handlePolling(processInstanceId);
    }

    this.intervalId = setInterval(() => {
      if (!this.isPollRequestRunning) {
        this.handlePolling(processInstanceId);
      }
    }, 5000);
  };

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  get flowNodeStatistics() {
    return Object.keys(this.statisticsByFlowNode).flatMap((flowNodeId) => {
      const types = ['active', 'incidents', 'canceled', 'completed'] as const;

      const statistic = this.statisticsByFlowNode[flowNodeId]!;

      return types.reduce<
        {
          flowNodeId: string;
          count: number;
          flowNodeState: FlowNodeState;
        }[]
      >((states, flowNodeState) => {
        const count =
          flowNodeState === 'active'
            ? statistic['filteredActive']
            : statistic[flowNodeState];

        if (count === 0) {
          return states;
        }

        return [
          ...states,
          {
            flowNodeId,
            count,
            flowNodeState,
          },
        ];
      }, []);
    });
  }

  get statisticsByFlowNode() {
    return this.state.statistics.reduce<{
      [key: string]: Omit<Statistic, 'activityId'>;
    }>((statistics, {activityId, active, incidents, completed, canceled}) => {
      const businessObject =
        processInstanceDetailsDiagramStore.businessObjects[activityId];

      if (businessObject === undefined) {
        return statistics;
      }

      statistics[activityId] = {
        active,
        filteredActive:
          businessObject?.$type !== 'bpmn:SubProcess' ? active : 0,
        incidents,
        completed:
          modificationsStore.isModificationModeEnabled ||
          !isProcessEndEvent(businessObject)
            ? 0
            : completed,

        canceled: modificationsStore.isModificationModeEnabled ? 0 : canceled,
      };

      return statistics;
    }, {});
  }

  getTotalRunningInstancesForFlowNode = (flowNodeId: string) => {
    return (
      (this.statisticsByFlowNode[flowNodeId]?.active ?? 0) +
      (this.statisticsByFlowNode[flowNodeId]?.incidents ?? 0)
    );
  };

  getTotalRunningInstancesVisibleForFlowNode = (flowNodeId: string) => {
    return (
      (this.statisticsByFlowNode[flowNodeId]?.filteredActive ?? 0) +
      (this.statisticsByFlowNode[flowNodeId]?.incidents ?? 0)
    );
  };

  get selectableFlowNodes() {
    return this.state.statistics.map(({activityId}) => activityId);
  }

  get willAllFlowNodesBeCanceled() {
    if (
      modificationsStore.flowNodeModifications.filter(({operation}) =>
        ['ADD_TOKEN', 'MOVE_TOKEN'].includes(operation),
      ).length > 0
    ) {
      return false;
    }

    return this.state.statistics.every(
      ({activityId, active, incidents}) =>
        (active === 0 && incidents === 0) ||
        modificationsStore.modificationsByFlowNode[activityId]
          ?.areAllTokensCanceled,
    );
  }

  reset() {
    super.reset();
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.processInstanceDetailsStatisticsDisposer?.();
    this.completedFlowNodesDisposer?.();
  }
}

export const processInstanceDetailsStatisticsStore =
  new ProcessInstanceDetailsStatistics();
