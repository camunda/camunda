/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed, when, autorun} from 'mobx';
import {constructFlowNodeIdToFlowNodeInstanceMap} from './mappers';
import {isInstanceRunning} from './utils/isInstanceRunning';
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
  disposer = null;

  init() {
    when(
      () => currentInstance.state.instance?.id !== undefined,
      () => {
        this.setCurrentSelection({
          flowNodeId: null,
          treeRowIds: [currentInstance.state.instance.id],
        });

        this.fetchInstanceExecutionHistory(currentInstance.state.instance.id);
      }
    );

    this.disposer = autorun(() => {
      const {instance} = currentInstance.state;

      if (isInstanceRunning(instance)) {
        if (this.intervalId === null) {
          this.startPolling(instance.id);
        }
      } else {
        this.stopPolling();
      }
    });
  }

  setCurrentSelection = (selection) => {
    this.state.selection = selection;
  };

  changeCurrentSelection = (node) => {
    const {instance} = currentInstance.state;

    const isRootNode = node.id === instance.id;
    // get the first flow node id (i.e. activity id) corresponding to the flowNodeId
    const flowNodeId = isRootNode ? null : node.activityId;
    const isRowAlreadySelected = this.state.selection.treeRowIds.includes(
      node.id
    );

    const newSelection =
      isRowAlreadySelected && !this.areMultipleNodesSelected
        ? {flowNodeId: null, treeRowIds: [instance.id]}
        : {flowNodeId, treeRowIds: [node.id]};

    this.setCurrentSelection(newSelection);
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
    this.intervalId = null;
  };

  completeInitialLoad = () => {
    this.state.isInitialLoadComplete = true;
  };

  reset = () => {
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.disposer?.(); // eslint-disable-line no-unused-expressions
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
