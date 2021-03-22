/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeAutoObservable, IReactionDisposer, Lambda} from 'mobx';
import {fetchWorkflowInstancesStatistics} from 'modules/api/instances';
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

class WorkflowStatistics {
  state: State = {...DEFAULT_STATE};
  diagramReactionDisposer: null | IReactionDisposer = null;
  filterObserveDisposer: null | Lambda = null;

  constructor() {
    makeAutoObservable(this);
  }

  init = () => {
    instancesStore.addCompletedOperationsHandler(() => {
      const filters = getRequestFilters();
      const workflowIds = filters?.workflowIds || [];

      if (workflowIds.length > 0) {
        this.fetchWorkflowStatistics(filters);
      }
    });
  };

  fetchWorkflowStatistics = async (payload = getRequestFilters()) => {
    this.setWorkflowStatistics([]);
    this.startLoading();
    const response = await fetchWorkflowInstancesStatistics(payload);
    this.setWorkflowStatistics(response.statistics);
    this.stopLoading();
  };

  startLoading = () => {
    this.state.isLoading = true;
  };

  stopLoading = () => {
    this.state.isLoading = false;
  };

  setWorkflowStatistics = (statistics: NodeStatistics[]) => {
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

export const workflowStatisticsStore = new WorkflowStatistics();
