/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, autorun, computed} from 'mobx';
import * as operationsApi from 'modules/api/batchOperations';
import * as instancesApi from 'modules/api/instances';
import {sortOperations} from './utils/sortOperations';

const DEFAULT_STATE = {
  operations: [],
  page: 1,
  isInitialLoadComplete: false,
};

const PAGE_SIZE = 20;

class Operations {
  state = {...DEFAULT_STATE};
  intervalId = null;
  disposer = null;

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

  fetchOperations = async (searchAfter) => {
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

  applyBatchOperation = async (operationType, query) => {
    const response = await instancesApi.applyBatchOperation(
      operationType,
      query
    );
    this.prependOperations(response);
  };

  applyOperation = async (id, payload) => {
    const response = await instancesApi.applyOperation(id, payload);
    this.prependOperations(response);
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
    clearInterval(this.intervalId);
    this.intervalId = null;
  };

  prependOperations = (response) => {
    this.state.operations.unshift(response);
  };

  setOperations(response) {
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
    if (this.disposer !== null) {
      this.disposer();
    }
  };
}

decorate(Operations, {
  state: observable,
  reset: action,
  setOperations: action,
  increasePage: action,
  prependOperations: action,
  isLoading: action,
  hasRunningOperations: computed,
  completeInitialLoad: action,
});

export const operationsStore = new Operations();
