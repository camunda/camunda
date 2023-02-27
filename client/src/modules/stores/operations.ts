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
  autorun,
  IReactionDisposer,
  override,
} from 'mobx';
import {fetchBatchOperations} from 'modules/api/fetchBatchOperations';
import {BatchOperationDto} from 'modules/api/sharedTypes';
import {
  applyBatchOperation,
  applyOperation,
} from 'modules/api/processInstances/operations';
import {sortOperations} from './utils/sortOperations';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {deleteDecisionDefinition} from 'modules/api/decisions/operations';
import {deleteProcessDefinition} from 'modules/api/processes/operations';

type Query = Parameters<typeof applyBatchOperation>['1'];
type OperationPayload = Parameters<typeof applyOperation>['1'];

type State = {
  operations: OperationEntity[];
  hasMoreOperations: boolean;
  page: number;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  operations: [],
  hasMoreOperations: true,
  page: 1,
  status: 'initial',
};

const MAX_OPERATIONS_PER_REQUEST = 20;

class Operations extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  isPollRequestRunning: boolean = false;
  intervalId: null | ReturnType<typeof setInterval> = null;
  disposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      setOperations: action,
      increasePage: action,
      prependOperations: action,
      setHasMoreOperations: action,
      hasRunningOperations: computed,
      startFetching: action,
      handleFetchSuccess: action,
      handleFetchError: action,
    });
  }

  init() {
    this.fetchOperations();

    this.disposer = autorun(() => {
      if (this.hasRunningOperations) {
        if (this.intervalId === null) {
          this.startPolling();
        }
      } else {
        this.stopPolling();
      }
    });
  }

  fetchOperations = this.retryOnConnectionLost(
    async (searchAfter?: OperationEntity['sortValues']) => {
      this.startFetching();

      const response = await fetchBatchOperations({
        pageSize: MAX_OPERATIONS_PER_REQUEST,
        searchAfter,
      });

      if (response.isSuccess) {
        const operations = response.data;
        this.setOperations(operations);
        this.setHasMoreOperations(operations.length);
        this.handleFetchSuccess();
      } else {
        this.handleFetchError();
      }
    }
  );

  fetchNextOperations = async () => {
    this.increasePage();
    this.fetchOperations(
      this.state.operations[this.state.operations.length - 1]!.sortValues
    );
  };

  applyBatchOperation = async ({
    operationType,
    query,
    onSuccess,
    onError,
  }: {
    operationType: OperationEntityType;
    query: Query;
    onSuccess: () => void;
    onError: (operationType: OperationEntityType) => void;
  }) => {
    try {
      const response = await applyBatchOperation(operationType, query);

      if (response.isSuccess) {
        this.prependOperations(response.data);
        onSuccess();
      } else {
        onError(operationType);
      }
    } catch {
      onError(operationType);
    }
  };

  applyOperation = async ({
    instanceId,
    payload,
    onError,
    onSuccess,
  }: {
    instanceId: string;
    payload: OperationPayload;
    onError?: (operationType: OperationEntityType) => void;
    onSuccess?: (operationType: OperationEntityType) => void;
  }) => {
    const response = await applyOperation(instanceId, payload);

    if (response.isSuccess) {
      this.prependOperations(response.data);
      onSuccess?.(payload.operationType);
    } else {
      onError?.(payload.operationType);
    }
  };

  applyDeleteDecisionDefinitionOperation = async ({
    decisionDefinitionId,
    onError,
    onSuccess,
  }: {
    decisionDefinitionId: string;
    onError?: () => void;
    onSuccess?: () => void;
  }) => {
    const response = await deleteDecisionDefinition(decisionDefinitionId);

    if (response.isSuccess) {
      this.prependOperations(response.data);
      onSuccess?.();
    } else {
      onError?.();
    }
  };

  applyDeleteProcessDefinitionOperation = async ({
    processDefinitionId,
    onError,
    onSuccess,
  }: {
    processDefinitionId: string;
    onError?: () => void;
    onSuccess?: () => void;
  }) => {
    const response = await deleteProcessDefinition(processDefinitionId);

    if (response.isSuccess) {
      this.prependOperations(response.data);
      onSuccess?.();
    } else {
      onError?.();
    }
  };

  handlePolling = async () => {
    this.isPollRequestRunning = true;
    const response = await fetchBatchOperations({
      pageSize: MAX_OPERATIONS_PER_REQUEST * this.state.page,
    });

    if (this.intervalId !== null && response.isSuccess) {
      this.setOperations(response.data);
    }

    if (!response.isSuccess) {
      logger.error('Failed to poll operations');
    }

    this.isPollRequestRunning = false;
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

  handleFetchError = () => {
    this.state.status = 'error';
  };

  startPolling = async () => {
    this.intervalId = setInterval(() => {
      if (!this.isPollRequestRunning) {
        this.handlePolling();
      }
    }, 1000);
  };

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  prependOperations = (response: BatchOperationDto) => {
    this.state.operations.unshift({...response, sortValues: undefined});
  };

  setOperations(response: any) {
    const operations = [...this.state.operations, ...response].reduce(
      (accumulator, operation) => {
        accumulator[operation.id] = operation;
        return accumulator;
      },
      {}
    );

    this.state.operations = sortOperations(Object.values(operations));
  }

  setHasMoreOperations(operationCount: number) {
    this.state.hasMoreOperations =
      operationCount === MAX_OPERATIONS_PER_REQUEST;
  }

  increasePage() {
    this.state.page++;
  }

  get hasRunningOperations() {
    return this.state.operations.some(
      (operation) => operation.endDate === null
    );
  }

  reset() {
    super.reset();
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
  }
}

export const operationsStore = new Operations();
