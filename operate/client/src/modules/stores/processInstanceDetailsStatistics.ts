/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IReactionDisposer} from 'mobx';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {
  fetchProcessInstanceDetailStatistics,
  ProcessInstanceDetailStatisticsDto,
} from 'modules/api/processInstances/fetchProcessInstanceDetailStatistics';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {modificationsStore} from './modifications';
import {isProcessEndEvent} from 'modules/bpmn-js/utils/isProcessEndEvent';
import isEqual from 'lodash/isEqual';

type Statistic = ProcessInstanceDetailStatisticsDto & {
  filteredActive: number;
  completedEndEvents: number;
};

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

  handleFetchSuccess = (statistics: ProcessInstanceDetailStatisticsDto[]) => {
    if (!isEqual(this.state.statistics, statistics)) {
      this.state.statistics = statistics;
    }
    this.state.status = 'fetched';
  };

  handleFetchFailure = () => {
    this.state.status = 'error';
  };

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
        completed: !isProcessEndEvent(businessObject) ? completed : 0,
        completedEndEvents: isProcessEndEvent(businessObject) ? completed : 0,
        canceled,
        ...(modificationsStore.isModificationModeEnabled
          ? {
              completed: 0,
              completedEndEvents: 0,
              canceled: 0,
            }
          : {}),
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

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
    this.processInstanceDetailsStatisticsDisposer?.();
    this.completedFlowNodesDisposer?.();
  }
}

export const processInstanceDetailsStatisticsStore =
  new ProcessInstanceDetailsStatistics();
