/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  observable,
  decorate,
  action,
  computed,
  when,
  autorun,
  IReactionDisposer,
} from 'mobx';
import {fetchVariables, applyOperation} from 'modules/api/instances';
import {differenceWith, differenceBy} from 'lodash';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {STATE} from 'modules/constants';
import {isInstanceRunning} from './utils/isInstanceRunning';

type Variable = {
  hasActiveOperation: boolean;
  id: string;
  name: string;
  scopeId: string;
  value: string;
  workflowInstanceId: string;
};
type State = {
  items: Variable[];
  isInitialLoadComplete: boolean;
  isLoading: boolean;
  isFailed: boolean;
};

const DEFAULT_STATE: State = {
  items: [],
  isInitialLoadComplete: false,
  isLoading: false,
  isFailed: false,
};

class Variables {
  state: State = {...DEFAULT_STATE};
  shouldCancelOngoingRequests: boolean = false;
  intervalId: null | number = null;
  disposer: null | IReactionDisposer = null;

  init = async (workflowInstanceId: any) => {
    when(
      () => currentInstanceStore.state.instance?.state === STATE.CANCELED,
      this.removeVariablesWithActiveOperations
    );

    this.disposer = autorun(() => {
      if (isInstanceRunning(currentInstanceStore.state.instance)) {
        if (this.intervalId === null) {
          this.startPolling(workflowInstanceId);
        }
      } else {
        this.stopPolling();
      }
    });
  };

  reset = () => {
    if (this.state.isLoading) {
      this.shouldCancelOngoingRequests = true;
    }
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
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

  setItems = (items: any) => {
    this.state.items = items;
  };

  get scopeId() {
    const {
      state: {selection},
    } = flowNodeInstanceStore;
    if (selection.treeRowIds.length > 0) {
      return selection.treeRowIds[0];
    }

    return undefined;
  }

  handlePolling = async (workflowInstanceId: string) => {
    const response = await fetchVariables({
      instanceId: workflowInstanceId,
      scopeId: this.scopeId !== undefined ? this.scopeId : workflowInstanceId,
    });

    if (this.intervalId !== null) {
      await this.handleResponse(response);
    }
  };

  handleResponse = async (response: any) => {
    if (this.shouldCancelOngoingRequests) {
      this.shouldCancelOngoingRequests = false;
      return;
    }

    if (!response.ok) {
      this.handleFailure();
      return;
    }

    const data = await response.json();
    if (this.state.items.length === 0) {
      this.setItems(data);
    } else {
      const {items} = this.state;
      const localVariables = differenceWith(
        items,
        data,
        (item: any, dataItem: any) =>
          item.name === dataItem.name && item.value === dataItem.value
      );
      const serverVariables = differenceBy(data, localVariables, 'name');

      this.setItems([...serverVariables, ...localVariables]);
    }

    this.handleSuccess();
  };

  fetchVariables = async (workflowInstanceId: any) => {
    this.startLoading();
    const response = await fetchVariables({
      instanceId: workflowInstanceId,
      scopeId: this.scopeId !== undefined ? this.scopeId : workflowInstanceId,
    });
    await this.handleResponse(response);
  };

  setSingleVariable = (variable: Variable) => {
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
    onError,
  }: {
    id: string;
    name: string;
    value: string;
    onError: () => void;
  }) => {
    this.setItems([
      ...this.state.items,
      {name, value, hasActiveOperation: true},
    ]);

    try {
      const response = await applyOperation(id, {
        operationType: 'UPDATE_VARIABLE',
        variableScopeId: this.scopeId,
        variableName: name,
        variableValue: value,
      });

      if (!response.ok) {
        this.setItems(this.state.items.filter((item) => item.name !== name));
        onError();
      }
    } catch {
      this.setItems(this.state.items.filter((item) => item.name !== name));
      onError();
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
      const response = await applyOperation(id, {
        operationType: 'UPDATE_VARIABLE',
        variableScopeId: this.scopeId,
        variableName: name,
        variableValue: value,
      });

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
    const {items} = this.state;
    return items.some(({hasActiveOperation}) => hasActiveOperation);
  }

  get hasNoVariables() {
    const {isLoading, isInitialLoadComplete, items} = this.state;
    return !isLoading && isInitialLoadComplete && items.length === 0;
  }

  removeVariablesWithActiveOperations = () => {
    this.state.items = this.state.items.filter(
      ({hasActiveOperation}) => !hasActiveOperation
    );
  };

  startPolling = async (instanceId: string) => {
    this.intervalId = setInterval(() => {
      this.handlePolling(instanceId);
    }, 5000);
  };

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
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
  setSingleVariable: action,
  hasNoVariables: computed,
  hasActiveOperation: computed,
  scopeId: computed,
});

export const variablesStore = new Variables();
