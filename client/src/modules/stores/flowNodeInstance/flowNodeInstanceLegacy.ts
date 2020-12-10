/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  makeObservable,
  observable,
  action,
  computed,
  when,
  autorun,
  IReactionDisposer,
} from 'mobx';
import {constructFlowNodeIdToFlowNodeInstanceMap} from 'modules/stores/mappers';
import {isInstanceRunning} from 'modules/stores/utils/isInstanceRunning';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {fetchActivityInstancesTree} from 'modules/api/activityInstances';
import {logger} from 'modules/logger';

type Node = {
  id: string;
  type: string;
  name?: string;
  state?: InstanceEntityState;
  activityId: string;
  startDate: string;
  endDate: null | string;
  parentId: string;
  children: Node[];
  isLastChild: boolean;
};

type Response = {
  children: Node[];
};

type Selection = {
  treeRowIds: string[];
  flowNodeId: null | string;
};

type State = {
  selection: Selection;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
  response: null | Response[];
};

const DEFAULT_STATE: State = {
  selection: {
    treeRowIds: [],
    flowNodeId: null,
  },
  status: 'initial',
  response: null,
};

class FlowNodeInstance {
  state: State = {...DEFAULT_STATE};
  intervalId: null | number = null;
  disposer: null | IReactionDisposer = null;

  constructor() {
    makeObservable(this, {
      state: observable,
      handleFetchSuccess: action,
      handleFetchFailure: action,
      startFetch: action,
      reset: action,
      setCurrentSelection: action,
      areMultipleNodesSelected: computed,
      instanceExecutionHistory: computed,
      flowNodeIdToFlowNodeInstanceMap: computed,
      isInstanceExecutionHistoryAvailable: computed,
      setResponse: action,
    });
  }

  init() {
    when(
      () => currentInstanceStore.state.instance?.id !== undefined,
      () => {
        const instanceId = currentInstanceStore.state.instance?.id;

        this.setCurrentSelection({
          flowNodeId: null,
          treeRowIds: instanceId === undefined ? [] : [instanceId],
        });

        if (instanceId !== undefined) {
          this.fetchInstanceExecutionHistory(instanceId);
        }
      }
    );

    this.disposer = autorun(() => {
      const {instance} = currentInstanceStore.state;

      if (isInstanceRunning(instance)) {
        if (this.intervalId === null && instance?.id !== undefined) {
          this.startPolling(instance.id);
        }
      } else {
        this.stopPolling();
      }
    });
  }

  setCurrentSelection = (selection: Selection) => {
    this.state.selection = selection;
  };

  changeCurrentSelection = (node: Node) => {
    const {instance} = currentInstanceStore.state;

    const isRootNode = node.id === instance?.id;
    // get the first flow node id (i.e. activity id) corresponding to the flowNodeId
    const flowNodeId = isRootNode ? null : node.activityId;
    const isRowAlreadySelected = this.state.selection.treeRowIds.includes(
      node.id
    );

    const newSelection =
      isRowAlreadySelected && !this.areMultipleNodesSelected
        ? {
            flowNodeId: null,
            treeRowIds: instance?.id === undefined ? [] : [instance.id],
          }
        : {flowNodeId, treeRowIds: [node.id]};

    this.setCurrentSelection(newSelection);
  };

  fetchInstanceExecutionHistory = async (id: WorkflowInstanceEntity['id']) => {
    this.startFetch();

    try {
      const response = await fetchActivityInstancesTree(id);

      if (response.ok) {
        this.setResponse(await response.json());
        this.handleFetchSuccess();
      } else {
        this.handleFetchFailure();
      }
    } catch (error) {
      this.handleFetchFailure(error);
    }
  };

  get isInstanceExecutionHistoryAvailable() {
    const {status} = this.state;

    return (
      status === 'fetched' &&
      this.instanceExecutionHistory !== null &&
      Object.keys(this.instanceExecutionHistory).length > 0
    );
  }
  get instanceExecutionHistory() {
    const {instance} = currentInstanceStore.state;
    const {response, status} = this.state;

    if (instance === null || ['initial', 'first-fetch'].includes(status)) {
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
    const {response, status} = this.state;

    if (['initial', 'first-fetch', 'error'].includes(status)) {
      return new Map();
    }

    return constructFlowNodeIdToFlowNodeInstanceMap(response);
  }

  startFetch = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  handleFetchFailure = (error?: Error) => {
    this.state.status = 'error';

    logger.error('Failed to fetch Instances activity');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  handleFetchSuccess = () => {
    this.state.status = 'fetched';
  };

  setResponse = (response: Response[]) => {
    this.state.response = response;
  };

  startPolling = async (workflowInstanceId: WorkflowInstanceEntity['id']) => {
    this.intervalId = setInterval(async () => {
      try {
        const response = await fetchActivityInstancesTree(workflowInstanceId);

        if (response.ok && this.intervalId !== null) {
          this.setResponse(await response.json());
          this.handleFetchSuccess();
        }

        if (!response.ok) {
          logger.error('Failed to poll Instances activity');
        }
      } catch (error) {
        logger.error('Failed to poll Instances activity');
        logger.error(error);
      }
    }, 5000);
  };

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  reset = () => {
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
  };

  get areMultipleNodesSelected() {
    return this.state.selection.treeRowIds.length > 1;
  }
}

export const flowNodeInstanceStore = new FlowNodeInstance();
