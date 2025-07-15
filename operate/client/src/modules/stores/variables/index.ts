/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  makeObservable,
  observable,
  action,
  computed,
  when,
  autorun,
  type IReactionDisposer,
  override,
  reaction,
} from 'mobx';
import {getOperation} from 'modules/api/getOperation';
import {fetchVariable} from 'modules/api/fetchVariable';
import {
  fetchVariables,
  type VariablePayload,
} from 'modules/api/processInstances/fetchVariables';
import {applyOperation} from 'modules/api/processInstances/operations';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {
  MAX_VARIABLES_PER_REQUEST,
  MAX_VARIABLES_STORED,
} from 'modules/constants/variables';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from '../networkReconnectionHandler';
import {modificationsStore} from '../modifications';
import {getScopeId} from 'modules/utils/variables';
import type {
  ProcessInstanceEntity,
  VariableEntity,
} from 'modules/types/operate';

type FetchType = 'initial' | 'prev' | 'next';
type State = {
  items: VariableEntity[];
  loadingItemId: VariableEntity['id'] | null;
  pendingItem: VariableEntity | null;
  status:
    | 'initial'
    | 'first-fetch'
    | 'fetching'
    | 'fetching-next'
    | 'fetching-prev'
    | 'fetched'
    | 'error';
  latestFetch: {
    fetchType: FetchType | null;
    itemsCount: number;
  };
  fullVariableValues: {[key: string]: string};
};

const DEFAULT_STATE: State = {
  items: [],
  loadingItemId: null,
  pendingItem: null,
  status: 'initial',
  latestFetch: {fetchType: null, itemsCount: 0},
  fullVariableValues: {},
};

class Variables extends NetworkReconnectionHandler {
  state: State = {
    ...DEFAULT_STATE,
  };
  areVariablesLoadedOnce: boolean = false;
  isPollRequestRunning: boolean = false;
  intervalId: null | ReturnType<typeof setInterval> = null;
  isPollOperationRequestRunning: boolean = false;
  operationIntervalId: null | ReturnType<typeof setInterval> = null;
  disposer: null | IReactionDisposer = null;
  variablesWithActiveOperationsDisposer: null | IReactionDisposer = null;
  fetchVariablesDisposer: null | IReactionDisposer = null;
  instanceId: ProcessInstanceEntity['id'] | null = null;
  pollingAbortController: AbortController | undefined;
  fetchAbortController: AbortController | undefined;
  onPollingOperationSuccess: (() => void) | null = null;
  deleteFullVariablesDisposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      setItems: action,
      setPendingItem: action,
      handleFetchSuccess: action,
      startFetching: action,
      startFetchingNext: action,
      startFetchingPrev: action,
      setLoadingItemId: action,
      handleFetchFailure: action,
      clearItems: action,
      updateVariable: action,
      addVariable: action,
      setSingleVariable: action,
      hasNoVariables: computed,
      hasActiveOperation: computed,
      setLatestFetchDetails: action,
      setAreVariablesLoadedOnce: action,
      areVariablesLoadedOnce: observable,
      setFullVariableValue: action,
      deleteFullVariableValue: action,
      clearFullVariableValues: action,
    });

    this.pollingAbortController = new AbortController();
    this.fetchAbortController = new AbortController();
  }

  init = (instanceId: ProcessInstanceEntity['id']) => {
    this.instanceId = instanceId;

    this.variablesWithActiveOperationsDisposer = when(
      () =>
        processInstanceDetailsStore.state.processInstance?.state === 'CANCELED',
      this.removeVariablesWithActiveOperations,
    );

    this.disposer = autorun(() => {
      if (processInstanceDetailsStore.isRunning && getScopeId() !== null) {
        if (
          this.intervalId === null &&
          !modificationsStore.isModificationModeEnabled
        ) {
          this.startPolling(instanceId);
        }
      } else {
        this.stopPolling();
      }
    });

    this.fetchVariablesDisposer = reaction(
      () => getScopeId(),
      (scopeId) => {
        this.clearItems();

        if (scopeId !== null) {
          this.setPendingItem(null);
          this.fetchAbortController?.abort();

          this.fetchVariables({
            fetchType: 'initial',
            instanceId,
            payload: {
              pageSize: MAX_VARIABLES_PER_REQUEST,
              scopeId: scopeId ?? instanceId,
            },
          });
        }
      },
      {fireImmediately: true},
    );

    this.deleteFullVariablesDisposer = reaction(
      () => modificationsStore.isModificationModeEnabled,
      (isModification, prevIsModification) => {
        if (!isModification && prevIsModification) {
          this.clearFullVariableValues();
        }
      },
    );
  };

  clearItems = () => {
    this.state.items = [];
  };

  setAreVariablesLoadedOnce = (areVariablesLoadedOnce: boolean) => {
    this.areVariablesLoadedOnce = areVariablesLoadedOnce;
  };

  setLatestFetchDetails = (fetchType: FetchType, itemsCount: number) => {
    this.state.latestFetch.fetchType = fetchType;
    this.state.latestFetch.itemsCount = itemsCount;
  };

  shouldFetchPreviousVariables = () => {
    const {items, status} = this.state;
    if (
      ['fetching-prev', 'fetching-next', 'fetching'].includes(status) ||
      items.length < MAX_VARIABLES_PER_REQUEST
    ) {
      return false;
    }

    return !items[0]?.isFirst;
  };

  shouldFetchNextVariables = () => {
    const {latestFetch, items, status} = this.state;
    if (
      ['fetching-prev', 'fetching-next', 'fetching'].includes(status) ||
      items.length < MAX_VARIABLES_PER_REQUEST
    ) {
      return false;
    }

    return (
      (latestFetch.fetchType === 'next' &&
        latestFetch.itemsCount === MAX_VARIABLES_PER_REQUEST) ||
      (latestFetch.fetchType === 'prev' &&
        items.length === MAX_VARIABLES_STORED) ||
      latestFetch.fetchType === 'initial'
    );
  };

  fetchPreviousVariables = async (instanceId: ProcessInstanceEntity['id']) => {
    this.startFetchingPrev();

    return this.fetchVariables({
      fetchType: 'prev',
      instanceId,
      payload: {
        pageSize: MAX_VARIABLES_PER_REQUEST,
        scopeId: getScopeId() ?? instanceId,
        searchBefore: this.getSortValues('prev'),
      },
    });
  };

  fetchNextVariables = async (instanceId: ProcessInstanceEntity['id']) => {
    this.startFetchingNext();

    return this.fetchVariables({
      fetchType: 'next',
      instanceId,
      payload: {
        pageSize: MAX_VARIABLES_PER_REQUEST,
        scopeId: getScopeId() ?? instanceId,
        searchAfter: this.getSortValues('next'),
      },
    });
  };

  refreshVariables = async (instanceId: ProcessInstanceEntity['id']) => {
    this.startFetching();

    this.fetchVariables({
      fetchType: 'initial',
      instanceId,
      payload: {
        pageSize: MAX_VARIABLES_PER_REQUEST,
        scopeId: getScopeId() ?? instanceId,
      },
    });
  };

  fetchVariable = async ({
    processInstanceId,
    variableId,
    onSuccess,
    onError,
    enableLoading = true,
  }: {
    processInstanceId: ProcessInstanceEntity['id'];
    variableId: VariableEntity['id'];
    onSuccess?: (variable: VariableEntity) => void;
    onError: () => void;
    enableLoading?: boolean;
  }): Promise<null | VariableEntity> => {
    if (enableLoading) {
      this.setLoadingItemId(variableId);
    }

    const response = await fetchVariable(processInstanceId, variableId);
    if (response.isSuccess) {
      const variable = await response.data;

      this.handleFetchVariableSuccess();
      onSuccess?.(variable);
      return variable;
    } else {
      this.handleFetchVariableFailure();
      onError();
      return null;
    }
  };

  getVariables = (fetchType: FetchType, items: VariableEntity[]) => {
    switch (fetchType) {
      case 'next': {
        const allVariables: VariableEntity[] = [...this.state.items, ...items];

        return allVariables.slice(
          Math.max(allVariables.length - MAX_VARIABLES_STORED, 0),
        );
      }
      case 'prev':
        return [...items, ...this.state.items].slice(0, MAX_VARIABLES_STORED);
      case 'initial':
      default:
        return items;
    }
  };

  handleFetchFailure = () => {
    this.state.status = 'error';
  };

  handleFetchSuccess = () => {
    this.state.status = 'fetched';
  };

  handleFetchVariableFailure = () => {
    this.setLoadingItemId(null);
  };

  handleFetchVariableSuccess = () => {
    this.setLoadingItemId(null);
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

  setItems = (items: VariableEntity[]) => {
    this.state.items = items;
  };

  setLoadingItemId = (id: VariableEntity['id'] | null) => {
    this.state.loadingItemId = id;
  };

  setPendingItem = (item: VariableEntity | null) => {
    this.state.pendingItem = item;
  };

  getSortValues = (fetchType: FetchType) => {
    const {items} = this.state;

    if (fetchType === 'initial') {
      if (items.length > 0 && !items[0]?.isFirst) {
        return items[0]?.sortValues ?? undefined;
      }

      return undefined;
    }
    if (fetchType === 'next') {
      return items[items.length - 1]?.sortValues ?? undefined;
    }
    if (fetchType === 'prev') {
      return items[0]?.sortValues ?? undefined;
    }
  };

  handlePolling = async (instanceId: ProcessInstanceEntity['id']) => {
    const {items} = this.state;

    if (this.pollingAbortController?.signal.aborted) {
      this.pollingAbortController = new AbortController();
    }

    this.isPollRequestRunning = true;

    const response = await fetchVariables(
      {
        instanceId,
        payload: {
          scopeId: getScopeId() || instanceId,
          pageSize:
            items.length <= MAX_VARIABLES_PER_REQUEST
              ? MAX_VARIABLES_PER_REQUEST
              : items.length,
          searchAfterOrEqual: this.getSortValues('initial'),
        },
        signal: this.pollingAbortController?.signal,
      },
      {isPolling: true},
    );

    if (this.intervalId !== null && response.isSuccess) {
      const variables = response.data;

      const {pendingItem} = this.state;

      if (
        pendingItem !== null &&
        variables.some(({name}) => name === pendingItem.name)
      ) {
        this.setPendingItem(null);
        this.stopPollingOperation();
        this.onPollingOperationSuccess?.();
      }

      this.setItems(variables);
    }

    if (!response.isSuccess) {
      logger.error('Failed to poll Variables');
    }

    this.isPollRequestRunning = false;
  };

  handlePollingOperation = async (
    operationId: string,
    onSuccess: () => void,
    onError: (statusCode: number) => void,
  ) => {
    this.isPollOperationRequestRunning = true;
    const response = await getOperation(operationId);

    if (this.operationIntervalId !== null && response.isSuccess) {
      const operationDetail = response.data[0];
      if (operationDetail !== undefined) {
        if (operationDetail.state === 'COMPLETED') {
          this.setPendingItem(null);
          onSuccess();
          this.stopPollingOperation();
        } else if (operationDetail.state === 'FAILED') {
          this.setPendingItem(null);
          this.stopPollingOperation();
          onError(response.statusCode);
        }
      }
    }

    if (!response.isSuccess) {
      logger.error('Failed to poll Variable Operation');
    }

    this.isPollOperationRequestRunning = false;
  };

  fetchVariables = this.retryOnConnectionLost(
    async ({
      fetchType,
      instanceId,
      payload,
    }: {
      fetchType: FetchType;
      instanceId: ProcessInstanceEntity['id'];
      payload: VariablePayload;
    }) => {
      if (flowNodeSelectionStore.state.selection?.isPlaceholder) {
        return null;
      }

      if (fetchType === 'initial') {
        this.startFetching();
      }

      if (this.fetchAbortController?.signal.aborted) {
        this.fetchAbortController = new AbortController();
      }

      const response = await fetchVariables({
        instanceId,
        payload,
        signal: this.fetchAbortController?.signal,
      });

      if (response.isSuccess) {
        const variablesFromResponse = response.data;
        this.setItems(this.getVariables(fetchType, variablesFromResponse));
        this.setLatestFetchDetails(fetchType, variablesFromResponse.length);

        this.handleFetchSuccess();
      } else {
        if (!response.isAborted) {
          this.handleFetchFailure();
        }
      }
    },
  );

  setSingleVariable = (variable: VariableEntity) => {
    const {items} = this.state;
    this.state.items = items.map((item) => {
      if (item.name === variable.name) {
        return variable;
      }

      return item;
    });
  };

  addVariable = async ({
    id,
    name,
    value,
    onSuccess,
    onError,
  }: {
    id: string;
    name: string;
    value: string;
    onSuccess: () => void;
    onError: (statusCode: number) => void;
  }) => {
    this.setPendingItem({
      name,
      value,
      hasActiveOperation: true,
      isFirst: false,
      sortValues: null,
      isPreview: false,
    });

    this.stopPolling();
    const response = await applyOperation(id, {
      operationType: 'ADD_VARIABLE',
      variableScopeId: getScopeId() || undefined,
      variableName: name,
      variableValue: value,
    });

    this.startPolling(this.instanceId);

    if (response.isSuccess) {
      const {id} = response.data;
      this.startPollingOperation({operationId: id, onSuccess, onError});
      return 'SUCCESSFUL';
    } else {
      this.setPendingItem(null);
      if (response.statusCode === 400) {
        return 'VALIDATION_ERROR';
      }

      onError(response.statusCode);
      return 'FAILED';
    }
  };

  updateVariable = async ({
    id,
    name,
    value,
    onError,
  }: {
    id: string;
    name: string;
    value: string;
    onError: (statusCode: number) => void;
  }) => {
    const {items} = this.state;

    const originalVariable = items.find((item) => item.name === name);
    if (originalVariable === undefined) {
      return;
    }

    this.setSingleVariable({
      ...originalVariable,
      value,
      hasActiveOperation: true,
    });

    this.stopPolling();

    const response = await applyOperation(id, {
      operationType: 'UPDATE_VARIABLE',
      variableScopeId: getScopeId() || undefined,
      variableName: name,
      variableValue: value,
    });

    this.startPolling(this.instanceId);

    if (!response.isSuccess) {
      this.setSingleVariable(originalVariable);
      onError(response.statusCode);
    }
  };

  get hasActiveOperation() {
    const {items, pendingItem} = this.state;
    return (
      items.some(({hasActiveOperation}) => hasActiveOperation) ||
      pendingItem !== null
    );
  }

  get hasNoVariables() {
    const {status, items} = this.state;
    return status === 'fetched' && items.length === 0;
  }

  removeVariablesWithActiveOperations = () => {
    this.state.items = this.state.items.filter(
      ({hasActiveOperation}) => !hasActiveOperation,
    );
    this.state.pendingItem = null;
  };

  startPolling = async (
    instanceId: string | null,
    options: {runImmediately?: boolean} = {runImmediately: false},
  ) => {
    if (
      document.visibilityState === 'hidden' ||
      instanceId === null ||
      !processInstanceDetailsStore.isRunning
    ) {
      return;
    }

    if (options.runImmediately) {
      this.handlePolling(instanceId);
    }

    this.intervalId = setInterval(() => {
      if (!this.isPollRequestRunning) {
        this.handlePolling(instanceId);
      }
    }, 5000);
  };

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  startPollingOperation = async ({
    operationId,
    onSuccess,
    onError,
  }: {
    operationId: string;
    onSuccess: () => void;
    onError: (statusCode: number) => void;
  }) => {
    this.onPollingOperationSuccess = onSuccess;
    this.operationIntervalId = setInterval(() => {
      if (!this.isPollOperationRequestRunning) {
        this.handlePollingOperation(operationId, onSuccess, onError);
      }
    }, 5000);
  };

  stopPollingOperation = () => {
    const {operationIntervalId} = this;

    if (operationIntervalId !== null) {
      clearInterval(operationIntervalId);
      this.operationIntervalId = null;
    }
  };

  setFullVariableValue = (
    id: VariableEntity['id'],
    value: VariableEntity['value'],
  ) => {
    if (id === undefined) {
      return undefined;
    }

    this.state.fullVariableValues[id] = value;
  };

  deleteFullVariableValue = (id: VariableEntity['id']) => {
    if (id === undefined) {
      return undefined;
    }

    delete this.state.fullVariableValues[id];
  };

  getFullVariableValue = (id: VariableEntity['id']) => {
    if (id === undefined) {
      return undefined;
    }

    return this.state.fullVariableValues[id];
  };

  clearFullVariableValues = () => {
    this.state.fullVariableValues = {};
  };

  reset() {
    this.pollingAbortController?.abort();
    this.fetchAbortController?.abort();

    super.reset();

    this.stopPolling();
    this.stopPollingOperation();
    this.state = {
      ...DEFAULT_STATE,
    };
    this.disposer?.();
    this.variablesWithActiveOperationsDisposer?.();
    this.fetchVariablesDisposer?.();
    this.deleteFullVariablesDisposer?.();
    this.instanceId = null;
    this.onPollingOperationSuccess = null;
  }
}

export const variablesStore = new Variables();
