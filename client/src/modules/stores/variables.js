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

class Variables {
  state = {...DEFAULT_STATE};
  intervalId = null;

  reset = () => {
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.intervalId = null;
  };

  clearItems = () => {
    this.state.items = [];
  };

  handleFailure = () => {
    this.state.isFailed = true;
    this.state.isLoading = false;

    if (!this.state.isInitialLoadComplete) {
      this.state.isInitialLoadComplete = true;
    }
  };

  handleSuccess = () => {
    this.state.isFailed = false;
    this.state.isLoading = false;

    if (!this.state.isInitialLoadComplete) {
      this.state.isInitialLoadComplete = true;
    }
  };

  startLoading = () => {
    this.state.isLoading = true;
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

  handlePolling = async (workflowInstanceId) => {
    const response = await fetchVariables({
      instanceId: workflowInstanceId,
      scopeId: this.scopeId !== undefined ? this.scopeId : workflowInstanceId,
    });

    if (this.intervalId !== null) {
      this.handleResponse(response);
    }
  };

  handleResponse = (response) => {
    if (response.error) {
      this.handleFailure();
      return;
    }

    if (this.state.items.length === 0) {
      this.setItems(response);
    } else {
      const {items} = this.state;
      const localVariables = differenceWith(
        items,
        response,
        (item, responseItem) =>
          item.name === responseItem.name && item.value === responseItem.value
      );
      const serverVariables = differenceBy(response, localVariables, 'name');

      this.setItems([...serverVariables, ...localVariables]);
    }

    this.handleSuccess();
  };

  fetchVariables = async (workflowInstanceId) => {
    this.startLoading();

    this.handleResponse(
      await fetchVariables({
        instanceId: workflowInstanceId,
        scopeId: this.scopeId !== undefined ? this.scopeId : workflowInstanceId,
      })
    );
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
      items.some(({hasActiveOperation}) => hasActiveOperation) ||
      isVariableOperationInProgress
    );
  }

  get hasNoVariables() {
    const {isLoading, isInitialLoadComplete, items} = this.state;
    return !isLoading && isInitialLoadComplete && items.length === 0;
  }

  startPolling = async (workflowInstanceId) => {
    this.intervalId = setInterval(() => {
      this.handlePolling(workflowInstanceId);
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
  clearItems: action,
  updateVariable: action,
  addVariable: action,
  hasNoVariables: computed,
  hasActiveOperation: computed,
  scopeId: computed,
});

export const variables = new Variables();
