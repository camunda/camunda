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
import {logger} from 'modules/logger';
import {getRequestFilters, IS_FILTERS_V2} from 'modules/utils/filter';

type Payload = Parameters<typeof fetchWorkflowInstances>['0'];

type FetchType = 'initial' | 'prev' | 'next';
type State = {
  filteredInstancesCount: null | number;
  workflowInstances: WorkflowInstanceEntity[];
  latestFetch: {
    fetchType: FetchType;
    workflowInstancesCount: number;
  } | null;
  status:
    | 'initial'
    | 'first-fetch'
    | 'fetching'
    | 'fetching-next'
    | 'fetching-prev'
    | 'fetched'
    | 'error';
};

const MAX_INSTANCES_STORED = 200;
const MAX_INSTANCES_PER_REQUEST = 50;

const DEFAULT_STATE: State = {
  filteredInstancesCount: null,
  workflowInstances: [],
  latestFetch: null,
  status: 'initial',
};

class Instances {
  state: State = {
    ...DEFAULT_STATE,
  };
  intervalId: null | ReturnType<typeof setInterval> = null;
  fetchInstancesDisposer: null | IReactionDisposer = null;
  completedOperationActionsDisposer: null | IReactionDisposer = null;
  instancesPollingDisposer: null | IReactionDisposer = null;
  completedOperationsHandlers: Array<() => void> = [];

  constructor() {
    makeObservable(this, {
      state: observable,
      reset: action,
      startFetching: action,
      startFetchingNext: action,
      startFetchingPrev: action,
      handleFetchSuccess: action,
      handleFetchError: action,
      setInstances: action,
      markInstancesWithActiveOperations: action,
      unmarkInstancesWithActiveOperations: action,
      visibleIdsInListPanel: computed,
      areWorkflowInstancesEmpty: computed,
      instanceIdsWithActiveOperations: computed,
      setLatestFetchDetails: action,
    });

    this.state.filteredInstancesCount =
      getStateLocally().filteredInstancesCount || null;
  }

  addCompletedOperationsHandler(handler: () => void) {
    this.completedOperationsHandlers.push(handler);
  }

  init() {
    if (!IS_FILTERS_V2) {
      this.fetchInstancesDisposer = autorun(() => {
        // we should wait for initial load to complete so we are sure both groupedWorkflows and filters are initially loaded and this fetch won't run a couple of times on first page landing
        if (filtersStore.state.isInitialLoadComplete) {
          this.fetchInitialInstances({
            query: filtersStore.getFiltersPayload(),
            sorting: filtersStore.state.sorting,
          });
        }
      });
    }

    this.instancesPollingDisposer = autorun(() => {
      if (this.instanceIdsWithActiveOperations.length > 0) {
        if (this.intervalId === null) {
          this.startPollingActiveInstances();
        }
      } else {
        this.stopPollingActiveInstances();
      }
    });
  }

  get instanceIdsWithActiveOperations() {
    return this.state.workflowInstances
      .filter((instance) => instance.hasActiveOperation)
      .map((instance) => instance.id);
  }

  setLatestFetchDetails = (
    fetchType: FetchType,
    workflowInstancesCount: number
  ) => {
    this.state.latestFetch = {
      fetchType,
      workflowInstancesCount,
    };
  };

  getWorkflowInstances = (
    fetchType: FetchType,
    workflowInstances: WorkflowInstanceEntity[]
  ) => {
    switch (fetchType) {
      case 'next':
        const allWorkflowInstances = [
          ...this.state.workflowInstances,
          ...workflowInstances,
        ];

        return allWorkflowInstances.slice(
          Math.max(allWorkflowInstances.length - MAX_INSTANCES_STORED, 0)
        );
      case 'prev':
        return [...workflowInstances, ...this.state.workflowInstances].slice(
          0,
          MAX_INSTANCES_STORED
        );
      case 'initial':
      default:
        return workflowInstances;
    }
  };

  shouldFetchPreviousInstances = () => {
    const {latestFetch, workflowInstances, status} = this.state;
    if (['fetching-prev', 'fetching-next', 'fetching'].includes(status)) {
      return false;
    }

    return (
      (latestFetch?.fetchType === 'next' &&
        workflowInstances.length === MAX_INSTANCES_STORED) ||
      (latestFetch?.fetchType === 'prev' &&
        latestFetch?.workflowInstancesCount === MAX_INSTANCES_PER_REQUEST)
    );
  };

  shouldFetchNextInstances = () => {
    const {latestFetch, workflowInstances, status} = this.state;
    if (['fetching-prev', 'fetching-next', 'fetching'].includes(status)) {
      return false;
    }

    return (
      (latestFetch?.fetchType === 'next' &&
        latestFetch?.workflowInstancesCount === MAX_INSTANCES_PER_REQUEST) ||
      (latestFetch?.fetchType === 'prev' &&
        workflowInstances.length === MAX_INSTANCES_STORED) ||
      latestFetch?.fetchType === 'initial'
    );
  };

  fetchPreviousInstances = async () => {
    this.startFetchingPrev();

    return this.fetchInstances({
      fetchType: 'prev',
      payload: {
        query: IS_FILTERS_V2
          ? getRequestFilters()
          : filtersStore.getFiltersPayload(),
        sorting: filtersStore.state.sorting,
        searchBefore: instancesStore.state.workflowInstances[0]?.sortValues,
        pageSize: MAX_INSTANCES_PER_REQUEST,
      },
    });
  };

  fetchNextInstances = async () => {
    this.startFetchingNext();

    return this.fetchInstances({
      fetchType: 'next',
      payload: {
        query: IS_FILTERS_V2
          ? getRequestFilters()
          : filtersStore.getFiltersPayload(),
        sorting: filtersStore.state.sorting,
        searchAfter: this.state.workflowInstances[
          this.state.workflowInstances.length - 1
        ]?.sortValues,
        pageSize: MAX_INSTANCES_PER_REQUEST,
      },
    });
  };

  fetchInitialInstances = async (payload: Payload) => {
    this.startFetching();
    this.fetchInstances({
      fetchType: 'initial',
      payload: {
        ...payload,
        pageSize: MAX_INSTANCES_PER_REQUEST,
        searchBefore: undefined,
        searchAfter: undefined,
      },
    });
  };

  fetchInstancesFromFilters = async () => {
    this.startFetching();
    this.fetchInstances({
      fetchType: 'initial',
      payload: {
        query: getRequestFilters(),
        sorting: filtersStore.state.sorting,
        pageSize: MAX_INSTANCES_PER_REQUEST,
        searchBefore: undefined,
        searchAfter: undefined,
      },
    });
  };

  fetchInstances = async ({
    fetchType,
    payload,
  }: {
    fetchType: FetchType;
    payload: Payload;
  }) => {
    try {
      const response = await fetchWorkflowInstances(payload);

      if (response.ok) {
        const {workflowInstances, totalCount} = await response.json();
        this.setInstances({
          filteredInstancesCount: totalCount,
          workflowInstances: this.getWorkflowInstances(
            fetchType,
            workflowInstances
          ),
        });

        this.setLatestFetchDetails(fetchType, workflowInstances.length);
        this.handleFetchSuccess();
      } else {
        this.handleFetchError();
      }
    } catch (error) {
      this.handleFetchError(error);
    }
  };

  refreshAllInstances = async (payload: Payload) => {
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

  startFetchingNext = () => {
    this.state.status = 'fetching-next';
  };

  startFetchingPrev = () => {
    this.state.status = 'fetching-prev';
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

  markInstancesWithActiveOperations = ({
    ids,
    shouldPollAllVisibleIds = false,
  }: {
    ids: WorkflowInstanceEntity['id'][];
    shouldPollAllVisibleIds?: boolean;
  }) => {
    if (shouldPollAllVisibleIds) {
      this.state.workflowInstances
        .filter((instance) => !ids.includes(instance.id))
        .forEach((instance) => {
          instance.hasActiveOperation = true;
        });
    } else {
      this.state.workflowInstances
        .filter((instance) => ids.includes(instance.id))
        .forEach((instance) => {
          instance.hasActiveOperation = true;
        });
    }
  };

  handlePollingActiveInstances = async () => {
    try {
      const response = await fetchWorkflowInstancesByIds(
        this.instanceIdsWithActiveOperations
      );

      if (response.ok) {
        if (this.intervalId !== null) {
          const {
            workflowInstances,
          }: {
            workflowInstances: WorkflowInstanceEntity[];
          } = await response.json();

          if (
            workflowInstances.some(
              ({hasActiveOperation}) => !hasActiveOperation
            )
          ) {
            this.completedOperationsHandlers.forEach((handler: () => void) => {
              handler();
            });

            this.refreshAllInstances({
              query: IS_FILTERS_V2
                ? getRequestFilters()
                : filtersStore.getFiltersPayload(),
              sorting: filtersStore.state.sorting,
              pageSize:
                this.state.workflowInstances.length > 0
                  ? this.state.workflowInstances.length
                  : MAX_INSTANCES_PER_REQUEST,
            });
          }
        }
      } else {
        logger.error('Failed to poll instances');
      }
    } catch (error) {
      logger.error('Failed to poll instances');
      logger.error(error);
    }
  };

  unmarkInstancesWithActiveOperations = ({
    instanceIds,
    shouldPollAllVisibleIds,
  }: {
    instanceIds: string[];
    shouldPollAllVisibleIds?: boolean;
  }) => {
    if (shouldPollAllVisibleIds) {
      this.refreshAllInstances({
        query: IS_FILTERS_V2
          ? getRequestFilters()
          : filtersStore.getFiltersPayload(),
        sorting: filtersStore.state.sorting,
        pageSize:
          this.state.workflowInstances.length > 0
            ? this.state.workflowInstances.length
            : MAX_INSTANCES_PER_REQUEST,
      });
    } else {
      this.state.workflowInstances
        .filter((instance) => instanceIds.includes(instance.id))
        .forEach((instance) => {
          instance.hasActiveOperation = false;
        });
    }
  };

  startPollingActiveInstances = async () => {
    this.intervalId = setInterval(() => {
      this.handlePollingActiveInstances();
    }, 5000);
  };

  stopPollingActiveInstances = () => {
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
    this.stopPollingActiveInstances();
    this.fetchInstancesDisposer?.();
    this.completedOperationActionsDisposer?.();
    this.instancesPollingDisposer?.();
    this.completedOperationsHandlers = [];
  };
}

export const instancesStore = new Instances();
export {MAX_INSTANCES_STORED};
