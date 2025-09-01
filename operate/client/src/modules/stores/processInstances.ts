/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  makeObservable,
  computed,
  observable,
  action,
  autorun,
  override,
  type IReactionDisposer,
} from 'mobx';
import {
  fetchProcessInstances,
  fetchProcessInstancesByIds,
  type ProcessInstancesDto,
} from 'modules/api/processInstances/fetchProcessInstances';
import {searchProcessInstances} from 'modules/api/v2/processInstances/searchProcessInstances';
import type {QueryProcessInstancesRequestBody} from '@vzeta/camunda-api-zod-schemas/8.8';
import {logger} from 'modules/logger';
import {
  getProcessInstancesRequestFilters,
  getSortParams,
} from 'modules/utils/filter';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {createOperation} from 'modules/utils/instance';
import {hasActiveOperations} from './utils/hasActiveOperations';
import {tracking} from 'modules/tracking';
import type {
  ProcessInstanceEntity,
  OperationEntityType,
} from 'modules/types/operate';

type Payload = Parameters<typeof fetchProcessInstances>['0']['payload'];

type FetchType = 'initial' | 'prev' | 'next';
type State = {
  filteredProcessInstancesCount: number;
  runningInstancesCount: number;
  processInstances: ProcessInstancesDto['processInstances'];
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
    | 'error';
};

const MAX_PROCESS_INSTANCES_STORED = 200;
const MAX_INSTANCES_PER_REQUEST = 50;

const DEFAULT_STATE: State = {
  filteredProcessInstancesCount: 0,
  runningInstancesCount: -1,
  processInstances: [],
  latestFetch: null,
  status: 'initial',
};

class ProcessInstances extends NetworkReconnectionHandler {
  state: State = {
    ...DEFAULT_STATE,
  };
  isPollRequestRunning: boolean = false;
  intervalId: null | ReturnType<typeof setInterval> = null;
  fetchInstancesDisposer: null | IReactionDisposer = null;
  completedOperationActionsDisposer: null | IReactionDisposer = null;
  instancesPollingDisposer: null | IReactionDisposer = null;
  refreshInstanceTimeout: number | undefined;
  completedOperationsHandlers: Array<() => void> = [];
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
      handleFetchError: action,
      setProcessInstances: action,
      markProcessInstancesWithActiveOperations: action,
      unmarkProcessInstancesWithActiveOperations: action,
      setRunningInstancesCount: action,
      visibleIdsInListPanel: computed,
      areProcessInstancesEmpty: computed,
      processInstanceIdsWithActiveOperations: computed,
      setLatestFetchDetails: action,
    });

    this.pollingAbortController = new AbortController();
  }

  addCompletedOperationsHandler(handler: () => void) {
    this.completedOperationsHandlers.push(handler);
  }

  init() {
    this.instancesPollingDisposer = autorun(() => {
      if (this.processInstanceIdsWithActiveOperations.length > 0) {
        if (this.intervalId === null) {
          this.startPollingActiveInstances();
        }
      } else {
        this.stopPollingActiveInstances();
      }
    });
  }

  get processInstanceIdsWithActiveOperations() {
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
    processInstancesCount: number,
  ) => {
    this.state.latestFetch = {
      fetchType,
      processInstancesCount,
    };
  };

getProcessInstances = (
    fetchType: FetchType,
    processInstances: ProcessInstanceEntity[],
  ) => {
    switch (fetchType) {
      case 'next': {
        const allProcessInstances: ProcessInstanceEntity[] = [
          ...this.state.processInstances,
          ...processInstances,
        ];

        return allProcessInstances.slice(
          Math.max(
            allProcessInstances.length - MAX_PROCESS_INSTANCES_STORED,
            0,
          ),
        );
      }
      case 'prev':
        return [...processInstances, ...this.state.processInstances].slice(
          0,
          MAX_PROCESS_INSTANCES_STORED,
        );
      case 'initial':
      default:
        return processInstances;
    }
  };

  getProcessInstanceOperationError = (
    selectedId: ProcessInstanceEntity['id'],
  ) => {
    const {batchOperationId} = getProcessInstancesRequestFilters();

    const instance = this.state.processInstances.find(
      (processInstance) => processInstance.id === selectedId,
    );

    return (
      instance?.operations.find(
        (operation) => operation.batchOperationId === batchOperationId,
      )?.errorMessage || null
    );
  };

  shouldFetchPreviousInstances = () => {
    const {latestFetch, processInstances, status} = this.state;
    if (['fetching-prev', 'fetching-next', 'fetching'].includes(status)) {
      return false;
    }

    return (
      (latestFetch?.fetchType === 'next' &&
        processInstances.length === MAX_PROCESS_INSTANCES_STORED) ||
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
        processInstances.length === MAX_PROCESS_INSTANCES_STORED) ||
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
        searchBefore:
          processInstancesStore.state.processInstances[0]?.sortValues,
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

  fetchProcessInstancesFromFilters = this.retryOnConnectionLost(async () => {
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

  // New v2 API method for fetching process instances
  fetchProcessInstancesFromFiltersV2 = this.retryOnConnectionLost(async () => {
    this.startFetching();
    
    // Simple v2 API payload with basic structure
    const v2ApiPayload: QueryProcessInstancesRequestBody = {
      filter: getProcessInstancesRequestFilters(),
      page: { limit: MAX_INSTANCES_PER_REQUEST },
      sort: [{ field: 'startDate', order: 'desc' }],
    };
    
    this.fetchInstancesV2({
      fetchType: 'initial',
      payload: v2ApiPayload,
    });
  });

  // New v2 API fetch method
  fetchInstancesV2 = async ({
    fetchType,
    payload,
  }: {
    fetchType: FetchType;
    payload: QueryProcessInstancesRequestBody;
  }) => {
    console.log('[ProcessInstances] Calling v2 API with payload:', payload);
    
    const {response, error} = await searchProcessInstances(payload);

    if (response) {
      console.log('[ProcessInstances] v2 API response:', response);
      
      // Map v2 response to ProcessInstanceEntity format
      const processInstances: ProcessInstanceEntity[] = response.items.map((item: any) => ({
        id: item.processInstanceKey?.toString() || '',
        processId: item.processDefinitionId || '',
        processName: item.processDefinitionName || '',
        processVersion: item.processDefinitionVersion || 1,
        startDate: item.startDate || '',
        endDate: item.endDate || null,
        state: item.state || 'ACTIVE',
        bpmnProcessId: item.processDefinitionId || '',
        hasActiveOperation: false, // Default to false, can be updated based on operations
        operations: [], // Default empty operations array
        sortValues: [item.startDate || ''], // For pagination
        parentInstanceId: item.parentProcessInstanceKey || null,
        rootInstanceId: item.rootProcessInstanceKey || item.processInstanceKey || null,
        callHierarchy: [], // Not provided in v2 API, set to empty array
        tenantId: item.tenantId || '',
        permissions: null, // Not provided in v2 API
      }));

      // Create ProcessInstancesDto format
      const processInstancesDto: ProcessInstancesDto = {
        processInstances,
        totalCount: response.items.length, // Use items length as we don't have totalCount in v2 response
      };

      tracking.track({
        eventName: 'instances-loaded',
        filters: Object.keys(payload.filter || {}),
        sortBy: payload.sort?.[0]?.field,
        sortOrder: payload.sort?.[0]?.order,
      });

      this.setProcessInstances({
        filteredProcessInstancesCount: processInstancesDto.totalCount,
        processInstances: this.getProcessInstances(fetchType, processInstancesDto.processInstances),
      });

      this.setLatestFetchDetails(fetchType, processInstancesDto.processInstances.length);
      this.handleFetchSuccess();
    } else {
      console.error('[ProcessInstances] v2 API error:', error);
      this.handleFetchError();
    }
  };

  fetchInstances = async ({
    fetchType,
    payload,
  }: {
    fetchType: FetchType;
    payload: Payload;
  }) => {
    const response = await fetchProcessInstances({payload});

    if (response.isSuccess) {
      const {processInstances, totalCount} = response.data;

      tracking.track({
        eventName: 'instances-loaded',
        filters: Object.keys(payload.query),
        ...payload.sorting,
      });

      this.setProcessInstances({
        filteredProcessInstancesCount: totalCount,
        processInstances: this.getProcessInstances(fetchType, processInstances),
      });

      this.setLatestFetchDetails(fetchType, processInstances.length);

      this.handleFetchSuccess();
    } else {
      this.handleFetchError();
    }
  };

  fetchRunningInstancesCount = async () => {
    const {processIds} = getProcessInstancesRequestFilters();

    const response = await fetchProcessInstances({
      payload: {
        query: {active: true, running: true, incidents: true, processIds},
        pageSize: 0,
      },
      options: {isPolling: true},
    });

    this.setRunningInstancesCount(
      response.isSuccess ? response.data.totalCount : 0,
    );
  };

  refreshAllInstances = async () => {
    const [processInstancesResponse] = await Promise.all([
      fetchProcessInstances({
        payload: {
          query: getProcessInstancesRequestFilters(),
          sorting: this.getSorting(),
          pageSize:
            this.state.processInstances.length > 0
              ? this.state.processInstances.length
              : MAX_INSTANCES_PER_REQUEST,
        },
        options: {isPolling: true},
      }),
      this.fetchRunningInstancesCount(),
    ]);

    if (processInstancesResponse.isSuccess) {
      const {processInstances, totalCount} = processInstancesResponse.data;
      this.setProcessInstances({
        filteredProcessInstancesCount: totalCount,
        processInstances,
      });
    } else {
      logger.error('Failed to refresh instances');
    }
  };

  startFetching = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
    this.state.filteredProcessInstancesCount = 0;
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

  handleFetchError = () => {
    this.state.status = 'error';
    this.state.filteredProcessInstancesCount = 0;
    this.state.processInstances = [];
  };

  setProcessInstances = ({
    filteredProcessInstancesCount,
    processInstances,
  }: {
    filteredProcessInstancesCount: number;
    processInstances: ProcessInstanceEntity[];
  }) => {
    this.state.processInstances = processInstances;
    this.state.filteredProcessInstancesCount = filteredProcessInstancesCount;
  };

  setRunningInstancesCount = (runningInstancesCount: number) => {
    this.state.runningInstancesCount = runningInstancesCount;
  };

  get visibleIdsInListPanel() {
    return this.state.processInstances.map(({id}) => id);
  }

  get areProcessInstancesEmpty() {
    return this.state.processInstances.length === 0;
  }

  markProcessInstancesWithActiveOperations = ({
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
    if (this.pollingAbortController?.signal.aborted) {
      this.pollingAbortController = new AbortController();
    }

    this.isPollRequestRunning = true;
    const response = await fetchProcessInstancesByIds(
      {
        ids: this.processInstanceIdsWithActiveOperations,
        signal: this.pollingAbortController?.signal,
      },
      {isPolling: true},
    );

    if (response.isSuccess) {
      if (this.intervalId !== null) {
        const {
          processInstances,
        }: {
          processInstances: ProcessInstanceEntity[];
        } = response.data;

        if (
          this.processInstanceIdsWithActiveOperations.length >
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

    this.isPollRequestRunning = false;
  };

  unmarkProcessInstancesWithActiveOperations = ({
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
              !(operation.type === operationType && operation.id === undefined),
          );

          if (!hasActiveOperations(instance.operations)) {
            instance.hasActiveOperation = false;
          }
        });
    }
  };

  startPollingActiveInstances = async () => {
    this.intervalId = setInterval(() => {
      if (!this.isPollRequestRunning) {
        this.handlePollingActiveInstances();
      }
    }, 5000);
  };

  stopPollingActiveInstances = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;

      this.refreshInstanceTimeout = window.setTimeout(
        this.refreshAllInstances,
        2000,
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
    window.clearTimeout(this.refreshInstanceTimeout);
  }
}

export const processInstancesStore = new ProcessInstances();
export {MAX_PROCESS_INSTANCES_STORED};
