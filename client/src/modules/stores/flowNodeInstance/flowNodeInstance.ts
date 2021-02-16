/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// TODO (paddy): move to modules/stores/
import {
  observable,
  makeObservable,
  action,
  computed,
  when,
  IReactionDisposer,
  runInAction,
} from 'mobx';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {fetchFlowNodeInstances} from 'modules/api/flowNodeInstances';
import {logger} from 'modules/logger';

const MAX_INSTANCES_PER_REQUEST = 50;
const MAX_INSTANCES_STORED = 200;

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

type Selection = {
  treeRowIds: string[];
  flowNodeId: null | string;
};

type State = {
  selection: Selection;
  status:
    | 'initial'
    | 'first-fetch'
    | 'fetching'
    | 'fetching-next'
    | 'fetching-prev'
    | 'fetched'
    | 'error';
  flowNodeInstances: {[key: string]: FlowNodeInstanceType[]};
  shouldFetchNextInstances: boolean;
  shouldFetchPreviousInstances: boolean;
};

const DEFAULT_STATE: State = {
  selection: {
    treeRowIds: [],
    flowNodeId: null,
  },
  status: 'initial',
  flowNodeInstances: {},
  shouldFetchNextInstances: true,
  shouldFetchPreviousInstances: false,
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
      removeSubTree: action,
      startFetch: action,
      startFetchNext: action,
      startFetchPrevious: action,
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
        }
      }
    );
  }

  fetchSubTree = async ({parentTreePath}: {parentTreePath: string}) => {
    const workflowInstanceId = currentInstanceStore.state.instance?.id;
    if (workflowInstanceId === undefined) {
      return;
    }

    const response = await fetchFlowNodeInstances({
      workflowInstanceId: workflowInstanceId,
      pageSize: MAX_INSTANCES_PER_REQUEST,
      parentTreePath,
    });

    this.handleFetchSuccess({
      parentTreePath,
      flowNodeInstances: await response.json(),
    });
  };

  removeSubTree = ({parentTreePath}: {parentTreePath: string}) => {
    // remove all nested sub trees first
    Object.keys(this.state.flowNodeInstances)
      .filter((treePath) => {
        return treePath.match(new RegExp(`^${parentTreePath}/`));
      })
      .forEach((treePath) => {
        delete this.state.flowNodeInstances[treePath];
      });

    delete this.state.flowNodeInstances[parentTreePath];
  };

  fetchInstanceExecutionHistory = async (id: WorkflowInstanceEntity['id']) => {
    this.startFetch();

    try {
      const response = await fetchFlowNodeInstances({
        workflowInstanceId: id,
        pageSize: MAX_INSTANCES_PER_REQUEST,
        parentTreePath: id,
      });

      if (response.ok) {
        this.handleFetchSuccess({
          parentTreePath: id,
          flowNodeInstances: await response.json(),
        });
      } else {
        this.handleFetchFailure();
      }
    } catch (error) {
      this.handleFetchFailure(error);
    }
  };

  fetchPreviousInstances = async () => {
    const workflowInstanceId = currentInstanceStore.state.instance?.id;
    if (
      workflowInstanceId === undefined ||
      this.state.status === 'fetching-prev'
    ) {
      return;
    }

    const flowNodeInstances = this.state.flowNodeInstances[workflowInstanceId];

    this.startFetchPrevious();

    try {
      const response = await fetchFlowNodeInstances({
        workflowInstanceId: workflowInstanceId,
        pageSize: MAX_INSTANCES_PER_REQUEST,
        parentTreePath: workflowInstanceId,
        searchBefore: flowNodeInstances[0].sortValues,
      });

      if (response.ok) {
        const fetchedFlowNodeInstances = await response.json();

        this.handleFetchSuccess({
          parentTreePath: workflowInstanceId,
          flowNodeInstances: [
            ...fetchedFlowNodeInstances,
            ...flowNodeInstances,
          ].slice(0, MAX_INSTANCES_STORED),
        });

        runInAction(() => {
          this.state.shouldFetchNextInstances = true;
          if (fetchedFlowNodeInstances.length !== MAX_INSTANCES_PER_REQUEST) {
            this.state.shouldFetchPreviousInstances = false;
          }
        });

        return fetchedFlowNodeInstances;
      } else {
        this.handleFetchFailure();
      }
    } catch (error) {
      this.handleFetchFailure(error);
    }
  };

  fetchNextInstances = async () => {
    const workflowInstanceId = currentInstanceStore.state.instance?.id;
    if (
      workflowInstanceId === undefined ||
      this.state.status === 'fetching-next'
    ) {
      return;
    }

    const flowNodeInstances = this.state.flowNodeInstances[workflowInstanceId];

    this.startFetchNext();

    try {
      const response = await fetchFlowNodeInstances({
        workflowInstanceId: workflowInstanceId,
        pageSize: MAX_INSTANCES_PER_REQUEST,
        parentTreePath: workflowInstanceId,
        searchAfter: flowNodeInstances[flowNodeInstances.length - 1].sortValues,
      });

      if (response.ok) {
        const fetchedFlowNodeInstances = await response.json();
        this.handleFetchSuccess({
          parentTreePath: workflowInstanceId,
          flowNodeInstances: [
            ...flowNodeInstances,
            ...fetchedFlowNodeInstances,
          ].slice(-MAX_INSTANCES_STORED),
        });

        runInAction(() => {
          this.state.shouldFetchPreviousInstances = true;
          if (fetchedFlowNodeInstances.length !== MAX_INSTANCES_PER_REQUEST) {
            this.state.shouldFetchNextInstances = false;
          }
        });
      } else {
        this.handleFetchFailure();
      }
    } catch (error) {
      this.handleFetchFailure(error);
    }
  };

  startFetchNext = () => {
    this.state.status = 'fetching-next';
  };

  startFetchPrevious = () => {
    this.state.status = 'fetching-prev';
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

  handleFetchSuccess = ({
    parentTreePath,
    flowNodeInstances,
  }: {
    parentTreePath: string;
    flowNodeInstances: FlowNodeInstanceType[];
  }) => {
    this.state.flowNodeInstances[parentTreePath] = flowNodeInstances;
    this.state.status = 'fetched';
  };

  reset = () => {
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
export type {FlowNodeInstanceType as FlowNodeInstance};
