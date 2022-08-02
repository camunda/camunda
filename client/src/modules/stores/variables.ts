/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  makeObservable,
  observable,
  action,
  computed,
  when,
  autorun,
  IReactionDisposer,
  override,
} from 'mobx';
import {
  applyOperation,
  getOperation,
  fetchVariables,
  VariablePayload,
  fetchVariable,
} from 'modules/api/instances';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {
  MAX_VARIABLES_PER_REQUEST,
  MAX_VARIABLES_STORED,
} from 'modules/constants/variables';
import {isInstanceRunning} from './utils/isInstanceRunning';
import {logger} from 'modules/logger';
import {flowNodeMetaDataStore} from './flowNodeMetaData';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

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
};

const DEFAULT_STATE: State = {
  items: [],
  loadingItemId: null,
  pendingItem: null,
  status: 'initial',
  latestFetch: {fetchType: null, itemsCount: 0},
};

class Variables extends NetworkReconnectionHandler {
  state: State = {
    ...DEFAULT_STATE,
  };
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
      scopeId: computed,
      displayStatus: computed,
      setLatestFetchDetails: action,
    });

    this.pollingAbortController = new AbortController();
    this.fetchAbortController = new AbortController();
  }

  init = (instanceId: ProcessInstanceEntity['id']) => {
    this.instanceId = instanceId;

    this.variablesWithActiveOperationsDisposer = when(
      () =>
        processInstanceDetailsStore.state.processInstance?.state === 'CANCELED',
      this.removeVariablesWithActiveOperations
    );

    this.disposer = autorun(() => {
      if (
        isInstanceRunning(processInstanceDetailsStore.state.processInstance) &&
        this.scopeId !== null
      ) {
        if (this.intervalId === null) {
          this.startPolling(instanceId);
        }
      } else {
        this.stopPolling();
      }
    });

    this.fetchVariablesDisposer = autorun(() => {
      if (this.scopeId !== null) {
        this.clearItems();
        this.setPendingItem(null);

        this.fetchAbortController?.abort();

        this.fetchVariables({
          fetchType: 'initial',
          instanceId,
          payload: {
            pageSize: MAX_VARIABLES_PER_REQUEST,
            scopeId: this.scopeId ?? instanceId,
          },
        });
      }
    });
  };

  clearItems = () => {
    this.state.items = [];
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
        scopeId: this.scopeId ?? instanceId,
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
        scopeId: this.scopeId ?? instanceId,
        searchAfter: this.getSortValues('next'),
      },
    });
  };

  fetchVariable = async ({
    id,
    onError,
    enableLoading = true,
  }: {
    id: VariableEntity['id'];
    onError: () => void;
    enableLoading?: boolean;
  }): Promise<null | VariableEntity> => {
    if (enableLoading) {
      this.setLoadingItemId(id);
    }

    try {
      const response = await fetchVariable(id);
      if (response.ok) {
        const variable = await response.json();

        this.handleFetchVariableSuccess();
        return variable;
      } else {
        this.handleFetchVariableFailure();
        onError();
        return null;
      }
    } catch (error) {
      this.handleFetchVariableFailure(error);
      onError();
      return null;
    }
  };

  getVariables = (fetchType: FetchType, items: VariableEntity[]) => {
    switch (fetchType) {
      case 'next':
        const allVariables = [...this.state.items, ...items];

        return allVariables.slice(
          Math.max(allVariables.length - MAX_VARIABLES_STORED, 0)
        );
      case 'prev':
        return [...items, ...this.state.items].slice(0, MAX_VARIABLES_STORED);
      case 'initial':
      default:
        return items;
    }
  };

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';
    logger.error('Failed to fetch Variables');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  handleFetchSuccess = () => {
    this.state.status = 'fetched';
  };

  handleFetchVariableFailure = (error?: unknown) => {
    this.setLoadingItemId(null);
    logger.error('Failed to fetch Single Variable');
    if (error !== undefined) {
      logger.error(error);
    }
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

  get scopeId() {
    const {selection} = flowNodeSelectionStore.state;
    const {metaData} = flowNodeMetaDataStore.state;

    return (
      selection?.flowNodeInstanceId ?? metaData?.flowNodeInstanceId ?? null
    );
  }

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
    try {
      const {items} = this.state;

      if (this.pollingAbortController?.signal.aborted) {
        this.pollingAbortController = new AbortController();
      }

      this.isPollRequestRunning = true;
      const response = await fetchVariables({
        instanceId,
        payload: {
          scopeId: this.scopeId || instanceId,
          pageSize:
            items.length <= MAX_VARIABLES_PER_REQUEST
              ? MAX_VARIABLES_PER_REQUEST
              : items.length,
          searchAfterOrEqual: this.getSortValues('initial'),
        },
        signal: this.pollingAbortController?.signal,
      });

      if (this.intervalId !== null && response.ok) {
        const variables: VariableEntity[] = await response.json();

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

      if (!response.ok) {
        logger.error('Failed to poll Variables');
      }
    } catch (error) {
      logger.error('Failed to poll Variables');
      logger.error(error);
    } finally {
      this.isPollRequestRunning = false;
    }
  };

  handlePollingOperation = async (
    operationId: string,
    onSuccess: () => void,
    onError: () => void
  ) => {
    try {
      this.isPollOperationRequestRunning = true;
      const response = await getOperation(operationId);

      if (this.operationIntervalId !== null && response.ok) {
        const operationDetail = await response.json();
        if (operationDetail[0].state === 'COMPLETED') {
          this.setPendingItem(null);
          onSuccess();
          this.stopPollingOperation();
        } else if (operationDetail[0].state === 'FAILED') {
          this.setPendingItem(null);
          this.stopPollingOperation();
          onError();
        }
      }

      if (!response.ok) {
        logger.error('Failed to poll Variable Operation');
      }
    } catch (error) {
      logger.error('Failed to poll Variable Operation');
      logger.error(error);
    } finally {
      this.isPollOperationRequestRunning = false;
    }
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
      try {
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

        if (response.ok) {
          const variablesFromResponse = await response.json();
          this.setItems(this.getVariables(fetchType, variablesFromResponse));
          this.setLatestFetchDetails(fetchType, variablesFromResponse.length);

          this.handleFetchSuccess();
        } else {
          this.handleFetchFailure();
        }
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }

        this.handleFetchFailure(error);
      }
    }
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
    onError: () => void;
  }) => {
    this.setPendingItem({
      name,
      value,
      hasActiveOperation: true,
      isFirst: false,
      sortValues: null,
      isPreview: false,
    });

    try {
      this.stopPolling();
      const response = await applyOperation(id, {
        operationType: 'ADD_VARIABLE',
        variableScopeId: this.scopeId || undefined,
        variableName: name,
        variableValue: value,
      });

      if (response.ok) {
        const {id} = await response.json();
        this.startPollingOperation({operationId: id, onSuccess, onError});
        return 'SUCCESSFUL';
      } else {
        this.setPendingItem(null);
        if (response.status === 400) {
          return 'VALIDATION_ERROR';
        }

        onError();
        return 'FAILED';
      }
    } catch {
      this.setPendingItem(null);
      onError();
      return 'FAILED';
    } finally {
      this.startPolling(this.instanceId);
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
    onError: () => void;
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

    try {
      this.stopPolling();

      const response = await applyOperation(id, {
        operationType: 'UPDATE_VARIABLE',
        variableScopeId: this.scopeId || undefined,
        variableName: name,
        variableValue: value,
      });
      this.startPolling(this.instanceId);
      if (!response.ok) {
        this.setSingleVariable(originalVariable);
        onError();
      }
    } catch {
      this.setSingleVariable(originalVariable);
      onError();
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
      ({hasActiveOperation}) => !hasActiveOperation
    );
    this.state.pendingItem = null;
  };

  startPolling = async (instanceId: string | null) => {
    if (instanceId !== null && this.intervalId === null) {
      this.intervalId = setInterval(() => {
        if (!this.isPollRequestRunning) {
          this.handlePolling(instanceId);
        }
      }, 5000);
    }
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
    onError: () => void;
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

  get displayStatus() {
    const {status, items} = this.state;

    if (status === 'error') {
      return 'error';
    }
    if (['initial', 'first-fetch'].includes(status)) {
      return 'skeleton';
    }
    if (flowNodeMetaDataStore.hasMultipleInstances) {
      return 'multi-instances';
    }
    if (status === 'fetching' || this.scopeId === null) {
      return 'spinner';
    }
    if (this.hasNoVariables) {
      return 'no-variables';
    }
    if (
      ['fetched', 'fetching-next', 'fetching-prev'].includes(status) &&
      items.length > 0
    ) {
      return 'variables';
    }

    logger.error('Failed to show Variables');
    return 'error';
  }

  reset() {
    this.pollingAbortController?.abort();
    this.fetchAbortController?.abort();

    super.reset();

    this.stopPolling();
    this.stopPollingOperation();
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
    this.variablesWithActiveOperationsDisposer?.();
    this.fetchVariablesDisposer?.();
    this.instanceId = null;
    this.onPollingOperationSuccess = null;
  }
}

export const variablesStore = new Variables();
