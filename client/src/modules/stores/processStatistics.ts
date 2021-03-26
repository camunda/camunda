/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeAutoObservable, IReactionDisposer, Lambda} from 'mobx';
import {fetchProcessInstancesStatistics} from 'modules/api/instances';
import {instancesStore} from 'modules/stores/instances';
import {getRequestFilters} from 'modules/utils/filter';

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

class ProcessStatistics {
  state: State = {...DEFAULT_STATE};
  diagramReactionDisposer: null | IReactionDisposer = null;
  filterObserveDisposer: null | Lambda = null;

  constructor() {
    makeAutoObservable(this);
  }

  init = () => {
    instancesStore.addCompletedOperationsHandler(() => {
      const filters = getRequestFilters();
      const processIds = filters?.processIds || [];

      if (processIds.length > 0) {
        this.fetchProcessStatistics(filters);
      }
    });
  };

  fetchProcessStatistics = async (payload = getRequestFilters()) => {
    this.setProcessStatistics([]);
    this.startLoading();
    const response = await fetchProcessInstancesStatistics(payload);
    this.setProcessStatistics(response.statistics);
    this.stopLoading();
  };

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

  reset = () => {
    this.resetState();

    this.diagramReactionDisposer?.();
    this.filterObserveDisposer?.();
  };
}

export const processStatisticsStore = new ProcessStatistics();
