/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  observable,
  decorate,
  action,
  autorun,
  computed,
  IReactionDisposer,
} from 'mobx';
import * as operationsApi from 'modules/api/batchOperations';
import {applyBatchOperation, applyOperation} from 'modules/api/instances';
import {sortOperations} from './utils/sortOperations';
import {logger} from 'modules/logger';

type Query = Parameters<typeof applyBatchOperation>['1'];
type OperationPayload = Parameters<typeof applyOperation>['1'];

type Operation = {
  endDate: null | string;
  id: string;
  instancesCount: number;
  name: null | string;
  operationsFinishedCount: number;
  operationsTotalCount: number;
  sortValues: unknown;
  startDate: string;
  type: string;
};
type State = {
  operations: Operation[];
  page: number;
  status: 'initial' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  operations: [],
  page: 1,
  status: 'initial',
};

const PAGE_SIZE = 20;

class Operations {
  state: State = {...DEFAULT_STATE};
  intervalId: null | number = null;
  disposer: null | IReactionDisposer = null;

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

  fetchOperations = async (searchAfter?: string) => {
    if (searchAfter !== undefined) {
      this.increasePage();
    }

    this.startFetching();

    try {
      const response = await operationsApi.fetchOperations({
        pageSize: PAGE_SIZE * this.state.page,
        searchAfter,
      });

      if (response.ok) {
        this.setOperations(await response.json());
        this.handleFetchSuccess();
      } else {
        this.handleFetchError();
      }
    } catch (error) {
      this.handleFetchError(error);
    }
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
    onError: () => void;
  }) => {
    try {
      const response = await applyBatchOperation(operationType, query);

      if (response.ok) {
        this.prependOperations(await response.json());
        onSuccess();
      } else {
        onError();
      }
    } catch {
      onError();
    }
  };

  applyOperation = async ({
    instanceId,
    payload,
    onError,
  }: {
    instanceId: string;
    payload: OperationPayload;
    onError: () => void;
  }) => {
    try {
      const response = await applyOperation(instanceId, payload);

      if (response.ok) {
        this.prependOperations(await response.json());
      } else {
        onError();
      }
    } catch {
      return onError();
    }
  };

  handlePolling = async () => {
    try {
      const response = await operationsApi.fetchOperations({
        pageSize: PAGE_SIZE * this.state.page,
      });

      if (this.intervalId !== null && response.ok) {
        this.setOperations(await response.json());
      }

      if (!response.ok) {
        logger.error('Failed to poll operations');
      }
    } catch (error) {
      logger.error('Failed to poll operations');
      logger.error(error);
    }
  };

  startFetching = () => {
    this.state.status = 'fetching';
  };

  handleFetchSuccess = () => {
    this.state.status = 'fetched';
  };

  handleFetchError = (error?: Error) => {
    this.state.status = 'error';

    logger.error('Failed to fetch operations');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  startPolling = async () => {
    this.intervalId = setInterval(() => {
      this.handlePolling();
    }, 5000);
  };

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  prependOperations = (response: Operation) => {
    this.state.operations.unshift(response);
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

  increasePage() {
    this.state.page++;
  }

  get hasRunningOperations() {
    return this.state.operations.some(
      (operation) => operation.endDate === null
    );
  }

  reset = () => {
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
  };
}

decorate(Operations, {
  state: observable,
  reset: action,
  setOperations: action,
  increasePage: action,
  prependOperations: action,
  hasRunningOperations: computed,
  startFetching: action,
  handleFetchSuccess: action,
  handleFetchError: action,
});

export const operationsStore = new Operations();
