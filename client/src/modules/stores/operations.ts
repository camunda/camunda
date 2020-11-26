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
import {
  applyBatchOperation,
  applyOperation,
  BatchOperationQuery,
} from 'modules/api/instances';
import {OperationType} from 'modules/types';
import {sortOperations} from './utils/sortOperations';

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
  isInitialLoadComplete: boolean;
};

type Payload = {
  operationType: OperationType;
  incidentId?: string;
};

type ApplyOperationProps = {
  instanceId: string;
  payload: Payload;
  onError: () => void;
};

const DEFAULT_STATE: State = {
  operations: [],
  page: 1,
  isInitialLoadComplete: false,
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

    const response = await operationsApi.fetchOperations({
      pageSize: PAGE_SIZE * this.state.page,
      searchAfter,
    });

    if (!this.state.isInitialLoadComplete) {
      this.completeInitialLoad();
    }

    this.setOperations(response);
  };

  applyBatchOperation = async ({
    operationType,
    query,
    onSuccess,
    onError,
  }: {
    operationType: OperationType;
    query: BatchOperationQuery;
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
  }: ApplyOperationProps) => {
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
    const response = await operationsApi.fetchOperations({
      pageSize: PAGE_SIZE * this.state.page,
    });

    if (this.intervalId !== null) {
      this.setOperations(response);
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

  completeInitialLoad() {
    this.state.isInitialLoadComplete = true;
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
  completeInitialLoad: action,
});

export const operationsStore = new Operations();
