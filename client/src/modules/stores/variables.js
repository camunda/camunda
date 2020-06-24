/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed} from 'mobx';
import {fetchVariables, applyOperation} from 'modules/api/instances';
import {differenceWith, differenceBy} from 'lodash';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';

const DEFAULT_STATE = {
  items: [],
  isInitialLoadComplete: false,
  isLoading: false,
  isFailed: false,
  isVariableOperationInProgress: false,
};

let isStoreInitialized = false;

class Variables {
  state = {...DEFAULT_STATE};
  timeoutId = null;

  reset = () => {
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    isStoreInitialized = false;
  };

  clearItems = () => {
    this.state.items = [];
  };

  handleFailure = () => {
    this.state.isFailed = true;
    this.state.isLoading = false;
  };

  handleSuccess = () => {
    this.state.isFailed = false;
    this.state.isLoading = false;
  };

  startLoading = () => {
    this.state.isLoading = true;
  };

  setIsInitialLoadComplete = () => {
    this.state.isInitialLoadComplete = true;
  };

  setItems = (items) => {
    this.state.items = items;
  };

  get scopeId() {
    const {
      state: {selection},
    } = flowNodeInstance;
    if (selection.treeRowIds.length > 0) {
      return selection.treeRowIds[0];
    }

    return undefined;
  }

  fetchVariables = async (workflowInstanceId, isPolling = false) => {
    if (!isPolling) {
      this.startLoading();
    }
    const response = await fetchVariables({
      instanceId: workflowInstanceId,
      scopeId: this.scopeId !== undefined ? this.scopeId : workflowInstanceId,
    });
    if (!isStoreInitialized) {
      return;
    }
    if (response.error) {
      this.handleFailure();
    } else {
      if (this.state.items.length === 0) {
        this.setItems(response);
      } else {
        const {items} = this.state;

        const localVariables = differenceWith(
          items,
          response,
          (item, res) => item.name === res.name && item.value === res.value
        );

        const serverVariables = differenceBy(response, localVariables, 'name');
        this.setItems([...serverVariables, ...localVariables]);
      }

      this.handleSuccess();

      if (!this.state.isInitialLoadComplete) {
        this.setIsInitialLoadComplete();
      }
    }
  };

  addVariable = async (id, name, value) => {
    this.state.isVariableOperationInProgress = true;

    this.setItems([
      ...this.state.items,
      {name, value, hasActiveOperation: true},
    ]);

    await applyOperation(id, {
      operationType: 'UPDATE_VARIABLE',
      variableScopeId: this.scopeId,
      variableName: name,
      variableValue: value,
    });
    this.state.isVariableOperationInProgress = false;
  };

  updateVariable = async (id, name, value) => {
    this.state.isVariableOperationInProgress = true;
    const {items} = this.state;

    this.setItems(
      items.map((item) => {
        if (item.name === name) {
          return {
            name,
            value,
            hasActiveOperation: true,
          };
        }

        return item;
      })
    );

    await applyOperation(id, {
      operationType: 'UPDATE_VARIABLE',
      variableScopeId: this.scopeId,
      variableName: name,
      variableValue: value,
    });
    this.state.isVariableOperationInProgress = false;
  };

  get hasActiveOperation() {
    const {items, isVariableOperationInProgress} = this.state;

    return (
      items.filter((item) => item.hasActiveOperation).length > 0 ||
      isVariableOperationInProgress
    );
  }

  get hasNoVariables() {
    const {isLoading, isInitialLoadComplete, items} = this.state;
    return !isLoading && isInitialLoadComplete && items.length === 0;
  }

  init = async (workflowInstanceId) => {
    isStoreInitialized = true;
    await this.startPolling(workflowInstanceId);
  };

  startPolling = async (workflowInstanceId) => {
    this.intervalId = setInterval(async () => {
      await this.fetchVariables(workflowInstanceId, true);
    }, 5000);
  };

  stopPolling = () => {
    clearInterval(this.intervalId);
  };
}

decorate(Variables, {
  state: observable,
  reset: action,
  setItems: action,
  handleSuccess: action,
  startLoading: action,
  handleFailure: action,
  setIsInitialLoadComplete: action,
  clearItems: action,
  updateVariable: action,
  addVariable: action,
  hasNoVariables: computed,
  hasActiveOperation: computed,
  scopeId: computed,
});

export const variables = new Variables();
