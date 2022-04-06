/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, action, observable, override} from 'mobx';
import {fetchProcessInstancesStatistics} from 'modules/api/instances';
import {processInstancesStore} from 'modules/stores/processInstances';
import {getProcessInstancesRequestFilters} from 'modules/utils/filter';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type NodeStatistics = {
  active: number;
  activityId: string;
  canceled: number;
  completed: number;
  incidents: number;
};
type State = {
  statistics: NodeStatistics[];
  isLoading: boolean;
};

const DEFAULT_STATE: State = {
  statistics: [],
  isLoading: false,
};

class ProcessStatistics extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      startLoading: action,
      stopLoading: action,
      setProcessStatistics: action,
      resetState: action,
      reset: override,
    });
  }

  init = () => {
    processInstancesStore.addCompletedOperationsHandler(() => {
      const filters = getProcessInstancesRequestFilters();
      const processIds = filters?.processIds || [];

      if (processIds.length > 0) {
        this.fetchProcessStatistics(filters);
      }
    });
  };

  fetchProcessStatistics = this.retryOnConnectionLost(
    async (payload = getProcessInstancesRequestFilters()) => {
      this.setProcessStatistics([]);
      this.startLoading();
      try {
        const response = await fetchProcessInstancesStatistics(payload);
        this.setProcessStatistics(response.statistics);
      } catch (error) {
        logger.error('Failed to fetch process statistics');
        logger.error(error);
      }
      this.stopLoading();
    }
  );

  startLoading = () => {
    this.state.isLoading = true;
  };

  stopLoading = () => {
    this.state.isLoading = false;
  };

  setProcessStatistics = (statistics: NodeStatistics[]) => {
    this.state.statistics = statistics;
  };

  resetState = () => {
    this.state = {...DEFAULT_STATE};
  };

  reset() {
    super.reset();
    this.resetState();
  }
}

export const processStatisticsStore = new ProcessStatistics();
