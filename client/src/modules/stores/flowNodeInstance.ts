/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  observable,
  makeObservable,
  action,
  computed,
  when,
  IReactionDisposer,
} from 'mobx';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {fetchFlowNodeInstances} from 'modules/api/flowNodeInstances';
import {logger} from 'modules/logger';

type FlowNodeInstanceType = {
  id: string;
  type: string;
  state?: InstanceEntityState;
  flowNodeId: string;
  startDate: string;
  endDate: null | string;
  treePath: string;
  sortValues: any[];
};

type FlowNodeInstances = {
  [treePath: string]: {
    running: boolean | null;
    children: FlowNodeInstanceType[];
  };
};

type State = {
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
  flowNodeInstances: FlowNodeInstances;
};

const DEFAULT_STATE: State = {
  status: 'initial',
  flowNodeInstances: {},
};

class FlowNodeInstance {
  state: State = {...DEFAULT_STATE};
  intervalId: null | ReturnType<typeof setInterval> = null;
  disposer: null | IReactionDisposer = null;
  instanceExecutionHistoryDisposer: null | IReactionDisposer = null;

  constructor() {
    makeObservable(this, {
      state: observable,
      handleFetchSuccess: action,
      handleFetchFailure: action,
      handlePollSuccess: action,
      removeSubTree: action,
      startFetch: action,
      reset: action,
      isInstanceExecutionHistoryAvailable: computed,
      instanceExecutionHistory: computed,
    });
  }

  init() {
    this.instanceExecutionHistoryDisposer = when(
      () => currentInstanceStore.state.instance?.id !== undefined,
      () => {
        const instanceId = currentInstanceStore.state.instance?.id;
        if (instanceId !== undefined) {
          this.fetchInstanceExecutionHistory(instanceId);
          this.startPolling();
        }
      }
    );
  }

  pollInstances = async () => {
    const workflowInstanceId = currentInstanceStore.state.instance?.id;

    if (workflowInstanceId === undefined) {
      return;
    }

    const queries = Object.entries(this.state.flowNodeInstances)
      .filter(([treePath, flowNodeInstance]) => {
        return flowNodeInstance.running || treePath === workflowInstanceId;
      })
      .map(([treePath, flowNodeInstance]) => {
        return {treePath, workflowInstanceId};
      });

    try {
      const response = await fetchFlowNodeInstances(queries);

      if (response.ok) {
        if (this.intervalId !== null) {
          this.handlePollSuccess(await response.json());
        }
      } else {
        this.handleFetchFailure();
      }
    } catch (error) {
      this.handleFetchFailure(error);
    }
  };

  fetchSubTree = async ({
    treePath,
    pageSize,
  }: {
    treePath: string;
    pageSize?: number;
  }) => {
    const workflowInstanceId = currentInstanceStore.state.instance?.id;
    if (workflowInstanceId === undefined) {
      return;
    }

    try {
      const response = await fetchFlowNodeInstances([
        {
          workflowInstanceId: workflowInstanceId,
          treePath,
          pageSize,
        },
      ]);

      if (response.ok) {
        this.handleFetchSuccess(await response.json());
      } else {
        this.handleFetchFailure();
      }
    } catch (error) {
      this.handleFetchFailure(error);
    }
  };

  removeSubTree = ({treePath}: {treePath: string}) => {
    // remove all nested sub trees first
    Object.keys(this.state.flowNodeInstances)
      .filter((currentTreePath) => {
        return currentTreePath.match(new RegExp(`^${treePath}/`));
      })
      .forEach((currentTreePath) => {
        delete this.state.flowNodeInstances[currentTreePath];
      });

    delete this.state.flowNodeInstances[treePath];
  };

  fetchInstanceExecutionHistory = async (
    workflowInstanceId: WorkflowInstanceEntity['id']
  ) => {
    this.startFetch();
    this.fetchSubTree({
      treePath: workflowInstanceId,
    });
  };

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

  handleFetchSuccess = (flowNodeInstances: FlowNodeInstances) => {
    Object.entries(flowNodeInstances).forEach(
      ([treePath, flowNodeInstance]) => {
        this.state.flowNodeInstances[treePath] = flowNodeInstance;
      }
    );

    this.state.status = 'fetched';
  };

  handlePollSuccess = (flowNodeInstances: FlowNodeInstances) => {
    Object.entries(flowNodeInstances).forEach(
      ([treePath, flowNodeInstance]) => {
        // don't create new trees (this prevents showing a tree when the user collapsed it earlier)
        if (this.state.flowNodeInstances[treePath] !== undefined) {
          this.state.flowNodeInstances[treePath] = flowNodeInstance;
        }
      }
    );
  };

  startPolling = () => {
    this.intervalId = setInterval(() => {
      this.pollInstances();
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
    this.instanceExecutionHistoryDisposer?.();
  };

  get isInstanceExecutionHistoryAvailable() {
    const {status} = this.state;

    return (
      ['fetched', 'fetching-next', 'fetching-prev'].includes(status) &&
      this.instanceExecutionHistory !== null &&
      Object.keys(this.instanceExecutionHistory).length > 0
    );
  }

  get instanceExecutionHistory(): FlowNodeInstanceType | null {
    const {instance: workflowInstance} = currentInstanceStore.state;
    const {status} = this.state;

    if (
      workflowInstance === null ||
      ['initial', 'first-fetch'].includes(status)
    ) {
      return null;
    }

    return {
      id: workflowInstance.id,
      type: 'WORKFLOW',
      state: workflowInstance.state,
      treePath: workflowInstance.id,
      endDate: null,
      startDate: '',
      sortValues: [],
      flowNodeId: workflowInstance.workflowId,
    };
  }
}

export const flowNodeInstanceStore = new FlowNodeInstance();
export type {FlowNodeInstanceType as FlowNodeInstance, FlowNodeInstances};
