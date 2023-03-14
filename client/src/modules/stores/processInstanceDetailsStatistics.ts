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
      const response = await fetchProcessInstanceDetailStatistics(
        processInstanceId
      );

      if (response.isSuccess) {
        this.handleFetchSuccess(response.data);
      } else {
        this.handleFetchFailure();
      }
    }
  );

  handlePolling = async (processInstanceId: string) => {
    this.isPollRequestRunning = true;
    const response = await fetchProcessInstanceDetailStatistics(
      processInstanceId
    );

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
        ['ADD_TOKEN', 'MOVE_TOKEN'].includes(operation)
      ).length > 0
    ) {
      return false;
    }

    return this.state.statistics.every(
      ({activityId, active, incidents}) =>
        (active === 0 && incidents === 0) ||
        modificationsStore.modificationsByFlowNode[activityId]
          ?.areAllTokensCanceled
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
