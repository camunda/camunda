/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  observable,
  decorate,
  action,
  computed,
  autorun,
  IReactionDisposer,
} from 'mobx';
import {storeStateLocally, getStateLocally} from 'modules/utils/localStorage';
import {
  fetchWorkflowInstances,
  fetchWorkflowInstancesByIds,
  WorkflowInstancesQuery,
} from 'modules/api/instances';
import {filtersStore} from 'modules/stores/filters';
import {DEFAULT_MAX_RESULTS} from 'modules/constants';

type State = {
  filteredInstancesCount: null | number;
  workflowInstances: unknown[];
  isLoading: boolean;
  isInitialLoadComplete: boolean;
  instancesWithActiveOperations: unknown[];
  instancesWithCompletedOperations: unknown[];
};

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
  intervalId: null | number = null;
  fetchInstancesDisposer: null | IReactionDisposer = null;
  completedOperationActionsDisposer: null | IReactionDisposer = null;
  instancesPollingDisposer: null | IReactionDisposer = null;

  constructor() {
    this.state.filteredInstancesCount =
      getStateLocally().filteredInstancesCount || null;
  }

  init() {
    this.fetchInstancesDisposer = autorun(() => {
      // we should wait for initial load to complete so we are sure both groupedWorkflows and filters are initially loaded and this fetch won't run a couple of times on first page landing
      if (filtersStore.state.isInitialLoadComplete) {
        this.fetchInstances({
          ...filtersStore.getFiltersPayload(),
          sorting: filtersStore.state.sorting,
          firstResult: filtersStore.firstElement,
          maxResults: DEFAULT_MAX_RESULTS,
        });
      }
    });

    this.completedOperationActionsDisposer = autorun(() => {
      if (this.state.instancesWithCompletedOperations.length > 0) {
        this.refreshInstances({
          ...filtersStore.getFiltersPayload(),
          sorting: filtersStore.state.sorting,
          firstResult: filtersStore.firstElement,
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

  fetchInstances = async (
    payload: WorkflowInstancesQuery = {
      firstResult: 0,
      maxResults: DEFAULT_MAX_RESULTS,
    }
  ) => {
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

  refreshInstances = async (payload: any) => {
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

  setInstances = ({filteredInstancesCount, workflowInstances}: any) => {
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
  }: any) => {
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

  setInstancesWithActiveOperations = (instances: any) => {
    this.state.instancesWithActiveOperations = instances
      .filter(({hasActiveOperation}: any) => hasActiveOperation)
      .map(({id}: any) => id);
  };

  setInstancesWithCompletedOperations = (instances: any) => {
    this.state.instancesWithCompletedOperations = instances
      .filter(({hasActiveOperation}: any) => !hasActiveOperation)
      .map(({id}: any) => id);
  };

  handlePollingInstancesByIds = async (instanceIds: string[]) => {
    const response = await fetchWorkflowInstancesByIds(instanceIds);
    if (this.intervalId !== null) {
      this.setInstancesWithActiveOperations(response.workflowInstances);
      this.setInstancesWithCompletedOperations(response.workflowInstances);
    }
  };

  removeInstanceFromInstancesWithActiveOperations = ({
    ids,
    shouldPollAllVisibleIds,
  }: {
    ids: string[];
    shouldPollAllVisibleIds?: boolean;
  }) => {
    if (shouldPollAllVisibleIds) {
      this.fetchInstances({
        ...filtersStore.getFiltersPayload(),
        sorting: filtersStore.state.sorting,
        firstResult: filtersStore.firstElement,
        maxResults: DEFAULT_MAX_RESULTS,
      });
    } else {
      this.state.instancesWithActiveOperations = this.state.instancesWithActiveOperations.filter(
        (id) => !ids.includes(id)
      );
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
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  reset = () => {
    this.state = {
      ...DEFAULT_STATE,
      filteredInstancesCount: this.state.filteredInstancesCount,
    };
    this.stopPollingInstancesById();
    this.fetchInstancesDisposer?.();
    this.completedOperationActionsDisposer?.();
    this.instancesPollingDisposer?.();
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
  removeInstanceFromInstancesWithActiveOperations: action,
  visibleIdsInListPanel: computed,
  areWorkflowInstancesEmpty: computed,
});

export const instancesStore = new Instances();
