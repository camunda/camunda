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
  override,
} from 'mobx';
import {storeStateLocally, getStateLocally} from 'modules/utils/localStorage';
import {
  fetchProcessInstances,
  fetchProcessInstancesByIds,
} from 'modules/api/instances';
import {logger} from 'modules/logger';
import {getRequestFilters, getSorting} from 'modules/utils/filter';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type Payload = Parameters<typeof fetchProcessInstances>['0'];

type FetchType = 'initial' | 'prev' | 'next';
type State = {
  filteredInstancesCount: null | number;
  processInstances: ProcessInstanceEntity[];
  latestFetch: {
    fetchType: FetchType;
    processInstancesCount: number;
  } | null;
  status:
    | 'initial'
    | 'first-fetch'
    | 'fetching'
    | 'fetching-next'
    | 'fetching-prev'
    | 'fetched'
    | 'refetching'
    | 'error';
};

const MAX_INSTANCES_STORED = 200;
const MAX_INSTANCES_PER_REQUEST = 50;

const DEFAULT_STATE: State = {
  filteredInstancesCount: null,
  processInstances: [],
  latestFetch: null,
  status: 'initial',
};

class Instances extends NetworkReconnectionHandler {
  state: State = {
    ...DEFAULT_STATE,
  };
  intervalId: null | ReturnType<typeof setInterval> = null;
  fetchInstancesDisposer: null | IReactionDisposer = null;
  completedOperationActionsDisposer: null | IReactionDisposer = null;
  instancesPollingDisposer: null | IReactionDisposer = null;
  retryInstanceFetchTimeout: NodeJS.Timeout | null = null;
  completedOperationsHandlers: Array<() => void> = [];
  shouldRetryOnEmptyResponse: boolean = false;
  retryCount: number = 0;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      startFetching: action,
      startFetchingNext: action,
      startFetchingPrev: action,
      handleFetchSuccess: action,
      handleRefetch: action,
      handleFetchError: action,
      setInstances: action,
      markInstancesWithActiveOperations: action,
      unmarkInstancesWithActiveOperations: action,
      visibleIdsInListPanel: computed,
      areProcessInstancesEmpty: computed,
      instanceIdsWithActiveOperations: computed,
      setLatestFetchDetails: action,
    });

    this.state.filteredInstancesCount =
      getStateLocally().filteredInstancesCount || null;
  }

  addCompletedOperationsHandler(handler: () => void) {
    this.completedOperationsHandlers.push(handler);
  }

  init(shouldRetryOnEmptyResponse: boolean = false) {
    this.shouldRetryOnEmptyResponse = shouldRetryOnEmptyResponse;

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
    return this.state.processInstances
      .filter((instance) => instance.hasActiveOperation)
      .map((instance) => instance.id);
  }

  setLatestFetchDetails = (
    fetchType: FetchType,
    processInstancesCount: number
  ) => {
    this.state.latestFetch = {
      fetchType,
      processInstancesCount,
    };
  };

  getProcessInstances = (
    fetchType: FetchType,
    processInstances: ProcessInstanceEntity[]
  ) => {
    switch (fetchType) {
      case 'next':
        const allProcessInstances = [
          ...this.state.processInstances,
          ...processInstances,
        ];

        return allProcessInstances.slice(
          Math.max(allProcessInstances.length - MAX_INSTANCES_STORED, 0)
        );
      case 'prev':
        return [...processInstances, ...this.state.processInstances].slice(
          0,
          MAX_INSTANCES_STORED
        );
      case 'initial':
      default:
        return processInstances;
    }
  };

  shouldFetchPreviousInstances = () => {
    const {latestFetch, processInstances, status} = this.state;
    if (['fetching-prev', 'fetching-next', 'fetching'].includes(status)) {
      return false;
    }

    return (
      (latestFetch?.fetchType === 'next' &&
        processInstances.length === MAX_INSTANCES_STORED) ||
      (latestFetch?.fetchType === 'prev' &&
        latestFetch?.processInstancesCount === MAX_INSTANCES_PER_REQUEST)
    );
  };

  shouldFetchNextInstances = () => {
    const {latestFetch, processInstances, status} = this.state;
    if (['fetching-prev', 'fetching-next', 'fetching'].includes(status)) {
      return false;
    }

    return (
      (latestFetch?.fetchType === 'next' &&
        latestFetch?.processInstancesCount === MAX_INSTANCES_PER_REQUEST) ||
      (latestFetch?.fetchType === 'prev' &&
        processInstances.length === MAX_INSTANCES_STORED) ||
      latestFetch?.fetchType === 'initial'
    );
  };

  fetchPreviousInstances = async () => {
    this.startFetchingPrev();

    return this.fetchInstances({
      fetchType: 'prev',
      payload: {
        query: getRequestFilters(),
        sorting: getSorting(),
        searchBefore: instancesStore.state.processInstances[0]?.sortValues,
        pageSize: MAX_INSTANCES_PER_REQUEST,
      },
    });
  };

  fetchNextInstances = async () => {
    this.startFetchingNext();

    return this.fetchInstances({
      fetchType: 'next',
      payload: {
        query: getRequestFilters(),
        sorting: getSorting(),
        searchAfter: this.state.processInstances[
          this.state.processInstances.length - 1
        ]?.sortValues,
        pageSize: MAX_INSTANCES_PER_REQUEST,
      },
    });
  };

  fetchInstancesFromFilters = this.retryOnConnectionLost(async () => {
    this.resetRetryInstancesFetch();
    this.startFetching();
    this.fetchInstances({
      fetchType: 'initial',
      payload: {
        query: getRequestFilters(),
        sorting: getSorting(),
        pageSize: MAX_INSTANCES_PER_REQUEST,
        searchBefore: undefined,
        searchAfter: undefined,
      },
    });
  });

  fetchInstances = async ({
    fetchType,
    payload,
  }: {
    fetchType: FetchType;
    payload: Payload;
  }) => {
    try {
      const response = await fetchProcessInstances(payload);

      if (response.ok) {
        const {processInstances, totalCount} = await response.json();

        this.setInstances({
          filteredInstancesCount: totalCount,
          processInstances: this.getProcessInstances(
            fetchType,
            processInstances
          ),
        });

        this.setLatestFetchDetails(fetchType, processInstances.length);

        if (this.shouldRefetchInstances) {
          this.handleRefetch();
        } else {
          this.handleFetchSuccess();
        }
      } else {
        this.handleFetchError();
      }
    } catch (error) {
      this.handleFetchError(error);
    }
  };

  get shouldRefetchInstances() {
    return (
      this.shouldRetryOnEmptyResponse &&
      this.state.processInstances.length === 0
    );
  }

  refreshAllInstances = async (payload: Payload) => {
    try {
      const response = await fetchProcessInstances(payload);

      if (response.ok) {
        const {processInstances, totalCount} = await response.json();
        this.setInstances({
          filteredInstancesCount: totalCount,
          processInstances,
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

  resetRetryInstancesFetch = () => {
    if (this.retryInstanceFetchTimeout !== null) {
      clearTimeout(this.retryInstanceFetchTimeout);
    }

    this.retryCount = 0;
  };

  handleFetchSuccess = () => {
    this.state.status = 'fetched';
  };

  handleRefetch = () => {
    if (this.retryCount < 3) {
      this.retryCount += 1;

      this.retryInstanceFetchTimeout = setTimeout(() => {
        this.fetchInstances({
          fetchType: 'initial',
          payload: {
            query: getRequestFilters(),
            sorting: getSorting(),
            pageSize: MAX_INSTANCES_PER_REQUEST,
            searchBefore: undefined,
            searchAfter: undefined,
          },
        });
      }, 5000);

      if (this.state.status === 'first-fetch') {
        return;
      }

      this.state.status = 'refetching';
    } else {
      this.resetRetryInstancesFetch();
      this.handleFetchSuccess();
    }
  };

  handleFetchError = (error?: Error) => {
    this.state.status = 'error';
    this.state.filteredInstancesCount = 0;
    this.state.processInstances = [];

    logger.error('Failed to fetch instances');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  setInstances = ({
    filteredInstancesCount,
    processInstances,
  }: {
    filteredInstancesCount: number;
    processInstances: ProcessInstanceEntity[];
  }) => {
    this.state.processInstances = processInstances;
    this.state.filteredInstancesCount = filteredInstancesCount;

    storeStateLocally({filteredInstancesCount});
  };

  get visibleIdsInListPanel() {
    return this.state.processInstances.map(({id}) => id);
  }

  get areProcessInstancesEmpty() {
    return this.state.processInstances.length === 0;
  }

  markInstancesWithActiveOperations = ({
    ids,
    shouldPollAllVisibleIds = false,
  }: {
    ids: ProcessInstanceEntity['id'][];
    shouldPollAllVisibleIds?: boolean;
  }) => {
    if (shouldPollAllVisibleIds) {
      this.state.processInstances
        .filter((instance) => !ids.includes(instance.id))
        .forEach((instance) => {
          instance.hasActiveOperation = true;
        });
    } else {
      this.state.processInstances
        .filter((instance) => ids.includes(instance.id))
        .forEach((instance) => {
          instance.hasActiveOperation = true;
        });
    }
  };

  handlePollingActiveInstances = async () => {
    try {
      const response = await fetchProcessInstancesByIds(
        this.instanceIdsWithActiveOperations
      );

      if (response.ok) {
        if (this.intervalId !== null) {
          const {
            processInstances,
          }: {
            processInstances: ProcessInstanceEntity[];
          } = await response.json();

          if (
            processInstances.some(({hasActiveOperation}) => !hasActiveOperation)
          ) {
            this.completedOperationsHandlers.forEach((handler: () => void) => {
              handler();
            });

            this.refreshAllInstances({
              query: getRequestFilters(),
              sorting: getSorting(),
              pageSize:
                this.state.processInstances.length > 0
                  ? this.state.processInstances.length
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
        query: getRequestFilters(),
        sorting: getSorting(),
        pageSize:
          this.state.processInstances.length > 0
            ? this.state.processInstances.length
            : MAX_INSTANCES_PER_REQUEST,
      });
    } else {
      this.state.processInstances
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

  reset() {
    super.reset();
    this.state = {
      ...DEFAULT_STATE,
      filteredInstancesCount: this.state.filteredInstancesCount,
    };
    this.stopPollingActiveInstances();
    this.fetchInstancesDisposer?.();
    this.completedOperationActionsDisposer?.();
    this.instancesPollingDisposer?.();
    this.completedOperationsHandlers = [];
    this.resetRetryInstancesFetch();
  }
}

export const instancesStore = new Instances();
export {MAX_INSTANCES_STORED};
