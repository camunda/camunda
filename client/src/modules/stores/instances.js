/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed, autorun} from 'mobx';
import {storeStateLocally, getStateLocally} from 'modules/utils/localStorage';
import {
  fetchWorkflowInstances,
  fetchWorkflowInstancesByIds,
} from 'modules/api/instances';
import {filters} from 'modules/stores/filters';
import {DEFAULT_MAX_RESULTS} from 'modules/constants';

const DEFAULT_STATE = {
  filteredInstancesCount: null,
  workflowInstances: [],
  isLoading: false,
  isInitialLoadComplete: false,
  instancesWithActiveOperations: [],
  instancesWithCompletedOperations: [],
};

class Instances {
  state = {...DEFAULT_STATE};
  intervalId = null;
  fetchInstancesDisposer = null;
  completedOperationActionsDisposer = null;
  instancesPollingDisposer = null;
  instancesLoadingDisposer = null;

  constructor() {
    this.state.filteredInstancesCount =
      getStateLocally().filteredInstancesCount || null;
  }

  init() {
    this.fetchInstancesDisposer = autorun(() => {
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

    this.completedOperationActionsDisposer = autorun(() => {
      if (this.state.instancesWithCompletedOperations.length > 0) {
        this.refreshInstances({
          ...filters.getFiltersPayload(),
          sorting: filters.state.sorting,
          firstResult: filters.firstElement,
          maxResults: DEFAULT_MAX_RESULTS,
        });

        this.resetInstancesWithCompletedOperations();
      }
    });

    this.instancesPollingDisposer = autorun(() => {
      if (this.state.instancesWithActiveOperations.length > 0) {
        if (this.intervalId === null) {
          this.startPollingInstancesById();
        }
      } else {
        this.stopPollingInstancesById();
      }
    });
  }

  resetInstancesWithCompletedOperations = () => {
    this.state.instancesWithCompletedOperations = [];
  };

  fetchInstances = async (payload) => {
    this.startLoading();

    const response = await fetchWorkflowInstances(payload);
    const {workflowInstances, totalCount} = response;

    this.setInstances({
      filteredInstancesCount: totalCount,
      workflowInstances,
    });

    this.stopLoading();
    this.setInstancesWithActiveOperations(this.state.workflowInstances);

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

  get visibleIdsInListPanel() {
    return this.state.workflowInstances.map(({id}) => id);
  }

  get areWorkflowInstancesEmpty() {
    return this.state.workflowInstances.length === 0;
  }

  addInstancesWithActiveOperations = ({
    ids,
    shouldPollAllVisibleIds = false,
  }) => {
    if (shouldPollAllVisibleIds) {
      this.state.instancesWithActiveOperations = this.visibleIdsInListPanel.filter(
        (id) => !ids.includes(id)
      );
    } else {
      this.state.instancesWithActiveOperations = this.state.instancesWithActiveOperations
        .concat(ids)
        .filter((id) => this.visibleIdsInListPanel.includes(id));
    }
  };

  setInstancesWithActiveOperations = (instances) => {
    this.state.instancesWithActiveOperations = instances
      .filter(({hasActiveOperation}) => hasActiveOperation)
      .map(({id}) => id);
  };

  setInstancesWithCompletedOperations = (instances) => {
    this.state.instancesWithCompletedOperations = instances
      .filter(({hasActiveOperation}) => !hasActiveOperation)
      .map(({id}) => id);
  };

  handlePollingInstancesByIds = async (instanceIds) => {
    const response = await fetchWorkflowInstancesByIds(instanceIds);
    if (this.intervalId !== null) {
      this.setInstancesWithActiveOperations(response.workflowInstances);
      this.setInstancesWithCompletedOperations(response.workflowInstances);
    }
  };

  startPollingInstancesById = async () => {
    this.intervalId = setInterval(() => {
      this.handlePollingInstancesByIds(
        this.state.instancesWithActiveOperations
      );
    }, 5000);
  };

  stopPollingInstancesById = () => {
    clearInterval(this.intervalId);
    this.intervalId = null;
  };

  reset = () => {
    this.state = {
      ...DEFAULT_STATE,
      filteredInstancesCount: this.state.filteredInstancesCount,
    };
    this.stopPollingInstancesById();

    this.fetchInstancesDisposer?.(); // eslint-disable-line no-unused-expressions
    this.completedOperationActionsDisposer?.(); // eslint-disable-line no-unused-expressions
    this.instancesPollingDisposer?.(); // eslint-disable-line no-unused-expressions
    this.instancesLoadingDisposer?.(); // eslint-disable-line no-unused-expressions
  };
}

decorate(Instances, {
  state: observable,
  reset: action,
  setInstances: action,
  startLoading: action,
  stopLoading: action,
  completeInitialLoad: action,
  addInstancesWithActiveOperations: action,
  resetInstancesWithCompletedOperations: action,
  setInstancesWithActiveOperations: action,
  setInstancesWithCompletedOperations: action,
  visibleIdsInListPanel: computed,
  areWorkflowInstancesEmpty: computed,
});

export const instances = new Instances();
