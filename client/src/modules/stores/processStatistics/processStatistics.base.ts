/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, action, observable, override, computed} from 'mobx';
import {
  fetchProcessInstancesStatistics,
  ProcessInstancesStatisticsDto,
} from 'modules/api/processInstances/fetchProcessInstancesStatistics';
import {getProcessInstancesRequestFilters} from 'modules/utils/filter';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from '../networkReconnectionHandler';
import {
  ACTIVE_BADGE,
  CANCELED_BADGE,
  COMPLETED_BADGE,
  INCIDENTS_BADGE,
} from 'modules/bpmn-js/badgePositions';

type State = {
  statistics: ProcessInstancesStatisticsDto[];
};

const DEFAULT_STATE: State = {
  statistics: [],
};

const overlayPositions = {
  active: ACTIVE_BADGE,
  incidents: INCIDENTS_BADGE,
  canceled: CANCELED_BADGE,
  completed: COMPLETED_BADGE,
} as const;

class ProcessStatistics extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      startFetching: action,
      handleFetchStatisticsSuccess: action,
      flowNodeStates: computed,
      resetState: action,
      overlaysData: computed,
    });
  }

  fetchProcessStatistics = this.retryOnConnectionLost(async () => {
    this.startFetching();
    const response = await fetchProcessInstancesStatistics(
      getProcessInstancesRequestFilters(),
    );

    if (response.isSuccess) {
      this.handleFetchStatisticsSuccess(response.data);
    } else {
      this.handleFetchError();
    }
  });

  startFetching = () => {
    this.state.statistics = [];
  };

  handleFetchStatisticsSuccess = (
    statistics: ProcessInstancesStatisticsDto[],
  ) => {
    this.state.statistics = statistics;
  };

  handleFetchError = (error?: unknown) => {
    logger.error('Failed to fetch diagram statistics');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  get overlaysData() {
    return this.flowNodeStates.map(({flowNodeState, count, flowNodeId}) => ({
      payload: {flowNodeState, count},
      type: `statistics-${flowNodeState}`,
      flowNodeId,
      position: overlayPositions[flowNodeState],
    }));
  }

  get flowNodeStates() {
    return this.state.statistics.flatMap((statistics) => {
      const types = ['active', 'incidents', 'canceled', 'completed'] as const;
      return types.reduce<
        {
          flowNodeId: string;
          count: number;
          flowNodeState: FlowNodeState;
        }[]
      >((states, flowNodeState) => {
        const count = statistics[flowNodeState];

        if (count > 0) {
          return [
            ...states,
            {
              flowNodeId: statistics.activityId,
              count,
              flowNodeState,
            },
          ];
        } else {
          return states;
        }
      }, []);
    });
  }

  resetState = () => {
    this.state = {...DEFAULT_STATE};
  };

  reset() {
    super.reset();
    this.resetState();
  }
}

export {ProcessStatistics};
