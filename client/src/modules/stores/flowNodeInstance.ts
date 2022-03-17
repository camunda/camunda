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
  override,
} from 'mobx';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {fetchFlowNodeInstances} from 'modules/api/flowNodeInstances';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {isEqual} from 'lodash';

const MAX_INSTANCES_STORED = 200;
const MAX_INSTANCES_PER_REQUEST = 50;

type FlowNodeInstanceType = {
  id: string;
  type: string;
  state?: InstanceEntityState;
  flowNodeId: string;
  startDate: string;
  endDate: null | string;
  treePath: string;
  sortValues: [string, string] | [];
};

type FlowNodeInstances = {
  [treePath: string]: {
    running: boolean | null;
    children: FlowNodeInstanceType[];
  };
};

type State = {
  status:
    | 'initial'
    | 'first-fetch'
    | 'fetching'
    | 'fetched'
    | 'error'
    | 'fetching-next'
    | 'fetching-prev';
  flowNodeInstances: FlowNodeInstances;
};

const DEFAULT_STATE: State = {
  status: 'initial',
  flowNodeInstances: {},
};

class FlowNodeInstance extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  intervalId: null | ReturnType<typeof setInterval> = null;
  disposer: null | IReactionDisposer = null;
  instanceExecutionHistoryDisposer: null | IReactionDisposer = null;
  instanceFinishedDisposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      handleFetchSuccess: action,
      handleFetchFailure: action,
      handlePollSuccess: action,
      removeSubTree: action,
      startFetch: action,
      startFetchNext: action,
      startFetchPrev: action,
      reset: override,
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

    this.instanceFinishedDisposer = when(
      () => currentInstanceStore.isRunning === false,
      () => this.stopPolling()
    );
  }

  pollInstances = async () => {
    const processInstanceId = currentInstanceStore.state.instance?.id;
    const {isRunning} = currentInstanceStore;
    if (processInstanceId === undefined || !isRunning) {
      return;
    }

    const queries = Object.entries(this.state.flowNodeInstances)
      .filter(([treePath, flowNodeInstance]) => {
        return flowNodeInstance.running || treePath === processInstanceId;
      })
      .map(([treePath, flowNodeInstance]) => {
        return {
          treePath,
          processInstanceId,
          pageSize:
            // round up to a multiple of MAX_INSTANCES_PER_REQUEST
            Math.ceil(
              flowNodeInstance.children.length / MAX_INSTANCES_PER_REQUEST
            ) * MAX_INSTANCES_PER_REQUEST,
          searchAfterOrEqual: flowNodeInstance.children[0]?.sortValues,
        };
      });

    if (queries.length === 0) {
      return;
    }

    try {
      const response = await fetchFlowNodeInstances(queries);

      if (response.ok) {
        if (this.intervalId !== null) {
          this.handlePollSuccess(await response.json());
        }
      } else {
        this.handlePollFailure();
      }
    } catch (error) {
      this.handlePollFailure(error);
    }
  };

  fetchNext = async (treePath: string) => {
    if (
      ['fetching-next', 'fetching-prev', 'fetching'].includes(this.state.status)
    ) {
      return;
    }

    this.startFetchNext();

    const children = this.state.flowNodeInstances[treePath]?.children;
    const sortValues = children && children[children.length - 1]?.sortValues;

    if (sortValues === undefined) {
      this.handleFetchFailure('sortValues not found');
      return;
    }

    const flowNodeInstances = await this.fetchFlowNodeInstances({
      treePath,
      pageSize: MAX_INSTANCES_PER_REQUEST,
      searchAfter: sortValues,
    });

    if (flowNodeInstances === undefined) {
      return;
    }

    const subTree = flowNodeInstances[treePath];
    const subTreeChildren = this.state.flowNodeInstances[treePath]?.children;

    if (subTree === undefined || subTreeChildren === undefined) {
      this.handleFetchFailure(`subTree not found: ${treePath}`);
      return;
    }

    flowNodeInstances[treePath]!.children = [
      ...subTreeChildren,
      ...subTree.children,
    ].slice(-MAX_INSTANCES_STORED);

    this.handleFetchSuccess(flowNodeInstances);
  };

  fetchPrevious = async (treePath: string) => {
    if (
      ['fetching-next', 'fetching-prev', 'fetching'].includes(this.state.status)
    ) {
      return;
    }

    this.startFetchPrev();

    const children = this.state.flowNodeInstances[treePath]?.children;
    const sortValues = children && children[0]?.sortValues;

    if (sortValues === undefined) {
      this.handleFetchFailure('sortValues not found');
      return;
    }

    const flowNodeInstances = await this.fetchFlowNodeInstances({
      treePath,
      pageSize: MAX_INSTANCES_PER_REQUEST,
      searchBefore: sortValues,
    });

    if (flowNodeInstances === undefined) {
      return;
    }

    const subTree = flowNodeInstances[treePath];
    const subTreeChildren = this.state.flowNodeInstances[treePath]?.children;

    if (subTree === undefined || subTreeChildren === undefined) {
      this.handleFetchFailure(`subTree not found: ${treePath}`);
      return;
    }

    const fetchedInstancesCount = subTree.children.length;

    flowNodeInstances[treePath]!.children = [
      ...subTree.children,
      ...subTreeChildren,
    ].slice(0, MAX_INSTANCES_STORED);

    this.handleFetchSuccess(flowNodeInstances);

    return fetchedInstancesCount;
  };

  fetchSubTree = async ({
    treePath,
    searchAfter,
  }: {
    treePath: string;
    searchAfter?: FlowNodeInstanceType['sortValues'];
  }) => {
    const flowNodeInstances = await this.fetchFlowNodeInstances({
      searchAfter,
      treePath,
      pageSize: MAX_INSTANCES_PER_REQUEST,
    });
    if (flowNodeInstances !== undefined) {
      this.handleFetchSuccess(flowNodeInstances);
    }
  };

  fetchFlowNodeInstances = async ({
    treePath,
    pageSize,
    searchAfter,
    searchBefore,
  }: {
    treePath: string;
    pageSize?: number;
    searchAfter?: FlowNodeInstanceType['sortValues'];
    searchBefore?: FlowNodeInstanceType['sortValues'];
  }): Promise<FlowNodeInstances | undefined> => {
    const processInstanceId = currentInstanceStore.state.instance?.id;

    if (processInstanceId === undefined) {
      return;
    }

    this.stopPolling();

    try {
      const response = await fetchFlowNodeInstances([
        {
          processInstanceId: processInstanceId,
          treePath,
          pageSize,
          searchAfter,
          searchBefore,
        },
      ]);

      if (response.ok) {
        return response.json();
      } else {
        this.handleFetchFailure();
      }
    } catch (error) {
      this.handleFetchFailure(error);
    } finally {
      this.startPolling();
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

  fetchInstanceExecutionHistory = this.retryOnConnectionLost(
    async (processInstanceId: ProcessInstanceEntity['id']) => {
      this.startFetch();
      const flowNodeInstances = await this.fetchFlowNodeInstances({
        treePath: processInstanceId,
        pageSize: MAX_INSTANCES_PER_REQUEST,
      });
      if (flowNodeInstances !== undefined) {
        this.handleFetchSuccess(flowNodeInstances);
      }
    }
  );

  getVisibleChildNodes = (flowNodeInstance: FlowNodeInstanceType) => {
    return (
      this.state.flowNodeInstances[
        flowNodeInstance.treePath || flowNodeInstance.id
      ]?.children || []
    );
  };

  startFetch = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  startFetchNext = () => {
    this.state.status = 'fetching-next';
  };

  startFetchPrev = () => {
    this.state.status = 'fetching-prev';
  };

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';
    logger.error('Failed to fetch flow node instances');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  handleFetchSuccess = (flowNodeInstances: FlowNodeInstances) => {
    Object.entries(flowNodeInstances).forEach(
      ([treePath, flowNodeInstance]) => {
        if (
          !isEqual(this.state.flowNodeInstances[treePath], flowNodeInstance)
        ) {
          this.state.flowNodeInstances[treePath] = flowNodeInstance;
        }
      }
    );

    this.state.status = 'fetched';
  };

  handlePollSuccess = (flowNodeInstances: FlowNodeInstances) => {
    if (this.intervalId === null) {
      return;
    }

    Object.entries(flowNodeInstances).forEach(
      ([treePath, flowNodeInstance]) => {
        // don't create new trees (this prevents showing a tree when the user collapsed it earlier)
        if (
          this.state.flowNodeInstances[treePath] !== undefined &&
          !isEqual(this.state.flowNodeInstances[treePath], flowNodeInstance)
        ) {
          this.state.flowNodeInstances[treePath] = flowNodeInstance;
        }
      }
    );
  };

  handlePollFailure = (error?: unknown) => {
    logger.error('Failed to poll flow node instances');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  startPolling = () => {
    if (currentInstanceStore.isRunning && this.intervalId === null) {
      this.intervalId = setInterval(this.pollInstances, 5000);
    }
  };

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  reset() {
    super.reset();
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
    this.instanceExecutionHistoryDisposer?.();
    this.instanceFinishedDisposer?.();
  }

  get isInstanceExecutionHistoryAvailable() {
    const {status} = this.state;

    return (
      ['fetched', 'fetching-next', 'fetching-prev'].includes(status) &&
      this.instanceExecutionHistory !== null &&
      Object.keys(this.instanceExecutionHistory).length > 0
    );
  }

  get instanceExecutionHistory(): FlowNodeInstanceType | null {
    const {instance: processInstance} = currentInstanceStore.state;
    const {status} = this.state;

    if (
      processInstance === null ||
      ['initial', 'first-fetch'].includes(status)
    ) {
      return null;
    }

    return {
      id: processInstance.id,
      type: 'PROCESS',
      state: processInstance.state,
      treePath: processInstance.id,
      endDate: null,
      startDate: '',
      sortValues: [],
      flowNodeId: processInstance.processId,
    };
  }
}

export const flowNodeInstanceStore = new FlowNodeInstance();
export type {FlowNodeInstanceType as FlowNodeInstance, FlowNodeInstances};
