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
  autorun,
  override,
  type IReactionDisposer,
} from 'mobx';
import {fetchBatchOperations} from 'modules/api/fetchBatchOperations';
import type {BatchOperationDto} from 'modules/api/sharedTypes';
import {
  applyBatchOperation,
  applyOperation,
  type BatchOperationQuery,
  type MigrationPlan,
  type Modifications,
} from 'modules/api/processInstances/operations';
import {sortOperations} from '../utils/sortOperations';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from '../networkReconnectionHandler';
import {deleteDecisionDefinition} from 'modules/api/decisions/operations';
import {deleteProcessDefinition} from 'modules/api/processes/operations';
import type {OperationEntity, OperationEntityType} from 'modules/types/operate';

type Query = BatchOperationQuery;
type OperationPayload = Parameters<typeof applyOperation>['1'];
type ErrorHandler = ({
  operationType,
  statusCode,
}: {
  operationType: OperationEntityType;
  statusCode?: number;
}) => void;

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
    },
  );

  fetchNextOperations = async () => {
    this.increasePage();
    this.fetchOperations(
      this.state.operations[this.state.operations.length - 1]!.sortValues,
    );
  };

  applyBatchOperation = async ({
    operationType,
    query,
    migrationPlan,
    modifications,
    onSuccess,
    onError,
  }: {
    operationType: OperationEntityType;
    query: Query;
    migrationPlan?: MigrationPlan;
    modifications?: Modifications;
    onSuccess: () => void;
    onError: ErrorHandler;
  }) => {
    try {
      const response = await applyBatchOperation({
        operationType,
        query,
        migrationPlan,
        modifications,
      });

      if (response.isSuccess) {
        this.prependOperations(response.data);
        onSuccess();
      } else {
        onError({operationType, statusCode: response.statusCode});
      }
    } catch {
      onError({operationType});
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
    onError?: ErrorHandler;
    onSuccess?: (operationType: OperationEntityType) => void;
  }) => {
    const response = await applyOperation(instanceId, payload);

    if (response.isSuccess) {
      this.prependOperations(response.data);
      onSuccess?.(payload.operationType);
    } else {
      onError?.({
        operationType: payload.operationType,
        statusCode: response.statusCode,
      });
    }
  };

  applyDeleteDecisionDefinitionOperation = async ({
    decisionDefinitionId,
    onError,
    onSuccess,
  }: {
    decisionDefinitionId: string;
    onError?: (statusCode: number) => void;
    onSuccess?: () => void;
  }) => {
    const response = await deleteDecisionDefinition(decisionDefinitionId);

    if (response.isSuccess) {
      this.prependOperations(response.data);
      onSuccess?.();
    } else {
      onError?.(response.statusCode);
    }
  };

  applyDeleteProcessDefinitionOperation = async ({
    processDefinitionId,
    onError,
    onSuccess,
  }: {
    processDefinitionId: string;
    onError?: (statusCode: number) => void;
    onSuccess?: () => void;
  }) => {
    const response = await deleteProcessDefinition(processDefinitionId);

    if (response.isSuccess) {
      this.prependOperations(response.data);
      onSuccess?.();
    } else {
      onError?.(response.statusCode);
    }
  };

  handlePolling = async () => {
    this.isPollRequestRunning = true;
    const response = await fetchBatchOperations(
      {
        pageSize: MAX_OPERATIONS_PER_REQUEST * this.state.page,
      },
      {isPolling: true},
    );

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

  setOperations(response: OperationEntity[]) {
    const operations = [...this.state.operations, ...response].reduce(
      (accumulator, operation) => {
        accumulator[operation.id] = operation;
        return accumulator;
      },
      {} as Record<string, OperationEntity>,
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
      (operation) => operation.endDate === null,
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
