/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed, autorun} from 'mobx';
import {storeStateLocally, getStateLocally} from 'modules/utils/localStorage';
import {fetchWorkflowInstances} from 'modules/api/instances';
import {filters} from 'modules/stores/filters';
import {DEFAULT_MAX_RESULTS} from 'modules/constants';

const DEFAULT_STATE = {
  filteredInstancesCount: null,
  workflowInstances: [],
  isLoading: false,
  isInitialLoadComplete: false,
};

class Instances {
  state = {...DEFAULT_STATE};
  disposer = null;

  constructor() {
    this.state.filteredInstancesCount =
      getStateLocally().filteredInstancesCount || null;
  }

  init() {
    this.disposer = autorun(() => {
      // we should wait for initial load to complete so we are sure both groupedWorkflows and filters are initially loaded and this fetch won't run a couple of times on first page landing
      if (filters.state.isInitialLoadComplete) {
        this.fetchInstances({
          ...filters.getFiltersPayload(),
          sorting: filters.state.sorting,
          firstResult: filters.firstElement,
          maxResults: DEFAULT_MAX_RESULTS,
        });
      }
    });
  }

  fetchInstances = async (payload) => {
    this.startLoading();

    const response = await fetchWorkflowInstances(payload);
    const {workflowInstances, totalCount} = response;
    this.setInstances({
      filteredInstancesCount: totalCount,
      workflowInstances,
    });

    this.stopLoading();
    if (!this.state.isInitialLoadComplete) {
      this.completeInitialLoad();
    }
  };

  refreshInstances = async (payload) => {
    const response = await fetchWorkflowInstances(payload);
    const {workflowInstances, totalCount} = response;

    this.setInstances({
      filteredInstancesCount: totalCount,
      workflowInstances,
    });
  };

  startLoading = () => {
    this.state.isLoading = true;
  };

  stopLoading = () => {
    this.state.isLoading = false;
  };

  completeInitialLoad = () => {
    this.state.isInitialLoadComplete = true;
  };

  setInstances = ({filteredInstancesCount, workflowInstances}) => {
    this.state.workflowInstances = workflowInstances;
    this.state.filteredInstancesCount = filteredInstancesCount;
    storeStateLocally({filteredInstancesCount});
  };

  reset = () => {
    this.state = {
      ...DEFAULT_STATE,
      filteredInstancesCount: this.state.filteredInstancesCount,
    };

    if (this.disposer !== null) {
      this.disposer();
    }
  };

  get workflowInstanceIds() {
    return this.state.workflowInstances.map(({id}) => id);
  }
}

decorate(Instances, {
  state: observable,
  reset: action,
  setInstances: action,
  startLoading: action,
  stopLoading: action,
  completeInitialLoad: action,
  workflowInstanceIds: computed,
});

export const instances = new Instances();
