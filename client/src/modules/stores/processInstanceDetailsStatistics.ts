/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {fetchProcessInstanceDetailStatistics} from 'modules/api/instances';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {modificationsStore} from './modifications';

type Statistic = {
  activityId: string;
  active: number;
  canceled: number;
  incidents: number;
  completed: number;
};

type State = {
  statistics: Statistic[];
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
      reset: override,
    });
  }

  init = (processInstanceId: string) => {
    this.processInstanceDetailsStatisticsDisposer = when(
      () => processInstanceDetailsStore.state.processInstance !== null,
      () => {
        this.fetchFlowNodeStatistics(processInstanceId);
        this.startPolling(processInstanceId);
      }
    );
    this.completedFlowNodesDisposer = when(
      () =>
        processInstanceDetailsStore.state.processInstance !== null &&
        !processInstanceDetailsStore.isRunning,
      () => {
        this.stopPolling();
      }
    );
  };

  fetchFlowNodeStatistics = this.retryOnConnectionLost(
    async (processInstanceId: string) => {
      try {
        const response = await fetchProcessInstanceDetailStatistics(
          processInstanceId
        );
        if (response.ok) {
          this.handleFetchSuccess(await response.json());
        } else {
          this.handleFetchFailure();
        }
      } catch (error) {
        this.handleFetchFailure(error);
      }
    }
  );

  handlePolling = async (processInstanceId: string) => {
    try {
      this.isPollRequestRunning = true;
      const response = await fetchProcessInstanceDetailStatistics(
        processInstanceId
      );

      if (this.intervalId !== null) {
        if (response.ok) {
          this.handleFetchSuccess(await response.json());
        } else {
          this.handleFetchFailure();
        }
      }
    } catch (error) {
      if (this.intervalId !== null) {
        this.handleFetchFailure(error);
      }
    } finally {
      this.isPollRequestRunning = false;
    }
  };

  handleFetchSuccess = (statistics: State['statistics']) => {
    this.state.statistics = statistics;
    this.state.status = 'fetched';
  };

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';

    logger.error('Failed to fetch process instance detail statistics');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  startPolling = (processInstanceId: string) => {
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
        const count = statistic[flowNodeState];

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
      const metaData =
        processInstanceDetailsDiagramStore.getMetaData(activityId);

      statistics[activityId] = {
        active:
          metaData?.type.elementType === undefined ||
          !['TASK_SUBPROCESS', 'EVENT_SUBPROCESS'].includes(
            metaData.type.elementType
          )
            ? active
            : 0,
        incidents,
        completed:
          modificationsStore.isModificationModeEnabled ||
          !metaData?.type.isProcessEndEvent
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
