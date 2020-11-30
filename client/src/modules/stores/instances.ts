/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  makeObservable,
  computed,
  observable,
  action,
  autorun,
  IReactionDisposer,
} from 'mobx';
import {storeStateLocally, getStateLocally} from 'modules/utils/localStorage';
import {
  fetchWorkflowInstances,
  fetchWorkflowInstancesByIds,
} from 'modules/api/instances';
import {filtersStore} from 'modules/stores/filters';
import {DEFAULT_MAX_RESULTS} from 'modules/constants';
import {logger} from 'modules/logger';

type Payload = Parameters<typeof fetchWorkflowInstances>['0'];

type State = {
  filteredInstancesCount: null | number;
  workflowInstances: WorkflowInstanceEntity[];
  instancesWithActiveOperations: WorkflowInstanceEntity['id'][];
  instancesWithCompletedOperations: WorkflowInstanceEntity['id'][];
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  filteredInstancesCount: null,
  workflowInstances: [],
  instancesWithActiveOperations: [],
  instancesWithCompletedOperations: [],
  status: 'initial',
};

class Instances {
  state: State = {
    ...DEFAULT_STATE,
  };
  intervalId: null | number = null;
  fetchInstancesDisposer: null | IReactionDisposer = null;
  completedOperationActionsDisposer: null | IReactionDisposer = null;
  instancesPollingDisposer: null | IReactionDisposer = null;

  constructor() {
    makeObservable(this, {
      state: observable,
      reset: action,
      startFetching: action,
      handleFetchSuccess: action,
      handleFetchError: action,
      setInstances: action,
      addInstancesWithActiveOperations: action,
      resetInstancesWithCompletedOperations: action,
      setInstancesWithActiveOperations: action,
      setInstancesWithCompletedOperations: action,
      removeInstanceFromInstancesWithActiveOperations: action,
      visibleIdsInListPanel: computed,
      areWorkflowInstancesEmpty: computed,
    });

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

  fetchInstances = async (payload: Payload) => {
    this.startFetching();

    try {
      const response = await fetchWorkflowInstances(payload);

      if (response.ok) {
        const {workflowInstances, totalCount} = await response.json();

        this.setInstances({
          filteredInstancesCount: totalCount,
          workflowInstances,
        });
        this.setInstancesWithActiveOperations(this.state.workflowInstances);
        this.handleFetchSuccess();
      } else {
        this.handleFetchError();
      }
    } catch (error) {
      this.handleFetchError(error);
    }
  };

  refreshInstances = async (payload: Payload) => {
    try {
      const response = await fetchWorkflowInstances(payload);

      if (response.ok) {
        const {workflowInstances, totalCount} = await response.json();
        this.setInstances({
          filteredInstancesCount: totalCount,
          workflowInstances,
        });
      } else {
        logger.error('Failed to refresh instances');
      }
    } catch (error) {
      logger.error('Failed to refresh instances');
      logger.error(error);
    }
  };

  startFetching = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  handleFetchSuccess = () => {
    this.state.status = 'fetched';
  };

  handleFetchError = (error?: Error) => {
    this.state.status = 'error';
    this.state.filteredInstancesCount = 0;
    this.state.workflowInstances = [];

    logger.error('Failed to fetch instances');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  setInstances = ({
    filteredInstancesCount,
    workflowInstances,
  }: {
    filteredInstancesCount: number;
    workflowInstances: WorkflowInstanceEntity[];
  }) => {
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
  }: {
    ids: WorkflowInstanceEntity['id'][];
    shouldPollAllVisibleIds?: boolean;
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

  setInstancesWithActiveOperations = (instances: WorkflowInstanceEntity[]) => {
    this.state.instancesWithActiveOperations = instances
      .filter(({hasActiveOperation}) => hasActiveOperation)
      .map(({id}) => id);
  };

  setInstancesWithCompletedOperations = (
    instances: WorkflowInstanceEntity[]
  ) => {
    this.state.instancesWithCompletedOperations = instances
      .filter(({hasActiveOperation}) => !hasActiveOperation)
      .map(({id}) => id);
  };

  handlePollingInstancesByIds = async (instanceIds: string[]) => {
    try {
      const response = await fetchWorkflowInstancesByIds(instanceIds);

      if (response.ok) {
        if (this.intervalId !== null) {
          const {workflowInstances} = await response.json();

          this.setInstancesWithActiveOperations(workflowInstances);
          this.setInstancesWithCompletedOperations(workflowInstances);
        }
      } else {
        logger.error('Failed to poll instances');
      }
    } catch (error) {
      logger.error('Failed to poll instances');
      logger.error(error);
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

export const instancesStore = new Instances();
