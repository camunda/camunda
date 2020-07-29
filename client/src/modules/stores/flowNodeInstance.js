/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed} from 'mobx';
import {constructFlowNodeIdToFlowNodeInstanceMap} from './mappers';
import {currentInstance} from 'modules/stores/currentInstance';
import {fetchActivityInstancesTree} from 'modules/api/activityInstances';

const DEFAULT_STATE = {
  selection: {
    treeRowIds: [],
    flowNodeId: null,
  },
  isInitialLoadComplete: false,
  isFailed: false,
  isLoading: false,
  response: null,
};

class FlowNodeInstance {
  state = {...DEFAULT_STATE};
  intervalId = null;

  setCurrentSelection = (selection) => {
    this.state.selection = selection;
  };

  fetchInstanceExecutionHistory = async (id) => {
    this.startLoading();

    const response = await fetchActivityInstancesTree(id);
    if (response.error) {
      this.handleFailure();
    } else {
      this.handleSuccess(response);
    }

    if (!this.state.isInitialLoadComplete) {
      this.completeInitialLoad();
    }
  };

  get isInstanceExecutionHistoryAvailable() {
    const {isInitialLoadComplete, isFailed} = this.state;

    return (
      isInitialLoadComplete &&
      !isFailed &&
      this.instanceExecutionHistory !== null &&
      Object.keys(this.instanceExecutionHistory).length > 0
    );
  }
  get instanceExecutionHistory() {
    const {instance} = currentInstance.state;
    const {response, isInitialLoadComplete} = this.state;

    if (instance === null || !isInitialLoadComplete) {
      return null;
    }
    return {
      ...response,
      id: instance.id,
      type: 'WORKFLOW',
      state: instance.state,
    };
  }

  get flowNodeIdToFlowNodeInstanceMap() {
    const {response, isFailed, isInitialLoadComplete} = this.state;

    if (isFailed || !isInitialLoadComplete) {
      return new Map();
    }
    return constructFlowNodeIdToFlowNodeInstanceMap(response);
  }

  startLoading = () => {
    this.state.isLoading = true;
  };

  handleFailure = () => {
    this.state.isLoading = false;
    this.state.isFailed = true;
  };

  handleSuccess = (response) => {
    this.state.isLoading = false;
    this.state.isFailed = false;
    this.state.response = response;
  };

  startPolling = async (workflowInstanceId) => {
    this.intervalId = setInterval(async () => {
      await this.fetchInstanceExecutionHistory(workflowInstanceId);
    }, 5000);
  };

  stopPolling = () => {
    clearInterval(this.intervalId);
  };

  completeInitialLoad = () => {
    this.state.isInitialLoadComplete = true;
  };

  reset = () => {
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.intervalId = null;
  };

  get areMultipleNodesSelected() {
    return this.state.selection.treeRowIds.length > 1;
  }
}

decorate(FlowNodeInstance, {
  state: observable,
  handleSuccess: action,
  handleFailure: action,
  startLoading: action,
  completeInitialLoad: action,
  reset: action,
  setCurrentSelection: action,
  areMultipleNodesSelected: computed,
  instanceExecutionHistory: computed,
  flowNodeIdToFlowNodeInstanceMap: computed,
  isInstanceExecutionHistoryAvailable: computed,
});

export const flowNodeInstance = new FlowNodeInstance();
