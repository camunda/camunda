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
import {
  fetchProcessInstances,
  fetchProcessInstancesByIds,
} from 'modules/api/instances';
import {logger} from 'modules/logger';
import {
  getProcessInstancesRequestFilters,
  getSortParams,
} from 'modules/utils/filter';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {createOperation} from 'modules/utils/instance';
import {hasActiveOperations} from './utils/hasActiveOperations';
import {tracking} from 'modules/tracking';

type Payload = Parameters<typeof fetchProcessInstances>['0']['payload'];

type FetchType = 'initial' | 'prev' | 'next';
type State = {
  filteredInstancesCount: number;
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
  filteredInstancesCount: 0,
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
  refreshInstanceTimeout: number | undefined;
  completedOperationsHandlers: Array<() => void> = [];
  shouldRetryOnEmptyResponse: boolean = false;
  retryCount: number = 0;
  pollingAbortController: AbortController | undefined;

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

    this.pollingAbortController = new AbortController();
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

  getSorting = () => {
    return (
      getSortParams() || {
        sortBy: 'startDate',
        sortOrder: 'desc',
      }
    );
  };

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
        query: getProcessInstancesRequestFilters(),
        sorting: this.getSorting(),
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
        query: getProcessInstancesRequestFilters(),
        sorting: this.getSorting(),
        searchAfter:
          this.state.processInstances[this.state.processInstances.length - 1]
            ?.sortValues,
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
        query: getProcessInstancesRequestFilters(),
        sorting: this.getSorting(),
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
      const response = await fetchProcessInstances({payload});

      if (response.ok) {
        const {processInstances, totalCount} = await response.json();

        tracking.track({
          eventName: 'instances-loaded',
          filters: Object.keys(payload.query),
          ...payload.sorting,
        });

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

  refreshAllInstances = async () => {
    try {
      const response = await fetchProcessInstances({
        payload: {
          query: getProcessInstancesRequestFilters(),
          sorting: this.getSorting(),
          pageSize:
            this.state.processInstances.length > 0
              ? this.state.processInstances.length
              : MAX_INSTANCES_PER_REQUEST,
        },
      });

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
    this.state.filteredInstancesCount = 0;
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
            query: getProcessInstancesRequestFilters(),
            sorting: this.getSorting(),
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

  handleFetchError = (error?: unknown) => {
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
  };

  get visibleIdsInListPanel() {
    return this.state.processInstances.map(({id}) => id);
  }

  get areProcessInstancesEmpty() {
    return this.state.processInstances.length === 0;
  }

  markInstancesWithActiveOperations = ({
    ids,
    operationType,
    shouldPollAllVisibleIds = false,
  }: {
    ids: ProcessInstanceEntity['id'][];
    operationType: OperationEntityType;
    shouldPollAllVisibleIds?: boolean;
  }) => {
    if (shouldPollAllVisibleIds) {
      this.state.processInstances
        .filter((instance) => !ids.includes(instance.id))
        .forEach((instance) => {
          instance.hasActiveOperation = true;
          instance.operations.push(createOperation(operationType));
        });
    } else {
      this.state.processInstances
        .filter((instance) => ids.includes(instance.id))
        .forEach((instance) => {
          instance.hasActiveOperation = true;
          instance.operations.push(createOperation(operationType));
        });
    }
  };

  handlePollingActiveInstances = async () => {
    try {
      if (this.pollingAbortController?.signal.aborted) {
        this.pollingAbortController = new AbortController();
      }

      const response = await fetchProcessInstancesByIds({
        ids: this.instanceIdsWithActiveOperations,
        signal: this.pollingAbortController?.signal,
      });

      if (response.ok) {
        if (this.intervalId !== null) {
          const {
            processInstances,
          }: {
            processInstances: ProcessInstanceEntity[];
          } = await response.json();

          if (
            this.instanceIdsWithActiveOperations.length >
              processInstances.length ||
            processInstances.some(({hasActiveOperation}) => !hasActiveOperation)
          ) {
            this.completedOperationsHandlers.forEach((handler: () => void) => {
              handler();
            });

            this.refreshAllInstances();
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
    operationType,
    shouldPollAllVisibleIds,
  }: {
    instanceIds: string[];
    operationType: OperationEntityType;
    shouldPollAllVisibleIds?: boolean;
  }) => {
    if (shouldPollAllVisibleIds) {
      this.refreshAllInstances();
    } else {
      this.state.processInstances
        .filter((instance) => instanceIds.includes(instance.id))
        .forEach((instance) => {
          instance.operations = instance.operations.filter(
            (operation) =>
              !(operation.type === operationType && operation.id === undefined)
          );

          if (!hasActiveOperations(instance.operations)) {
            instance.hasActiveOperation = false;
          }
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

      this.refreshInstanceTimeout = window.setTimeout(
        this.refreshAllInstances,
        2000
      );
    }
  };

  reset() {
    this.pollingAbortController?.abort();

    super.reset();
    this.state = {
      ...DEFAULT_STATE,
    };
    this.stopPollingActiveInstances();
    this.fetchInstancesDisposer?.();
    this.completedOperationActionsDisposer?.();
    this.instancesPollingDisposer?.();
    this.completedOperationsHandlers = [];
    this.resetRetryInstancesFetch();
    window.clearTimeout(this.refreshInstanceTimeout);
  }
}

export const instancesStore = new Instances();
export {MAX_INSTANCES_STORED};
