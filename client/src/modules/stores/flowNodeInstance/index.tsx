/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {
  fetchFlowNodeInstances,
  FlowNodeInstanceDto,
  FlowNodeInstancesDto,
} from 'modules/api/fetchFlowNodeInstances';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from '../networkReconnectionHandler';
import isEqual from 'lodash/isEqual';
import {modificationsStore} from '../modifications';

const MAX_PROCESS_INSTANCES_STORED = 200;
const MAX_INSTANCES_PER_REQUEST = 50;

type FlowNodeInstanceType = FlowNodeInstanceDto & {isPlaceholder?: boolean};
type FlowNodeInstances = FlowNodeInstancesDto<FlowNodeInstanceType>;

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
  isPollRequestRunning: boolean = false;
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
      () => processInstanceDetailsStore.state.processInstance?.id !== undefined,
      () => {
        const instanceId =
          processInstanceDetailsStore.state.processInstance?.id;
        if (instanceId !== undefined) {
          this.fetchInstanceExecutionHistory(instanceId);
          this.startPolling();
        }
      }
    );

    this.instanceFinishedDisposer = when(
      () => processInstanceDetailsStore.isRunning === false,
      () => this.stopPolling()
    );
  }

  pollInstances = async () => {
    const processInstanceId =
      processInstanceDetailsStore.state.processInstance?.id;
    const {isRunning} = processInstanceDetailsStore;
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

    this.isPollRequestRunning = true;
    const response = await fetchFlowNodeInstances(queries);

    if (response.isSuccess) {
      if (this.intervalId !== null) {
        this.handlePollSuccess(response.data ?? {});
      }
    }

    this.isPollRequestRunning = false;
  };

  fetchNext = async (treePath: string) => {
    if (
      ['fetching-next', 'fetching-prev', 'fetching'].includes(this.state.status)
    ) {
      return;
    }

    const children = this.state.flowNodeInstances[treePath]?.children;
    if (children === undefined) {
      return;
    }

    this.startFetchNext();

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
    ].slice(-MAX_PROCESS_INSTANCES_STORED);

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
    if (children === undefined) {
      return;
    }

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
    ].slice(0, MAX_PROCESS_INSTANCES_STORED);

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
    const processInstanceId =
      processInstanceDetailsStore.state.processInstance?.id;

    if (processInstanceId === undefined) {
      return;
    }

    this.stopPolling();

    const response = await fetchFlowNodeInstances([
      {
        processInstanceId: processInstanceId,
        treePath,
        pageSize,
        searchAfter,
        searchBefore,
      },
    ]);

    if (!response.isSuccess) {
      this.handleFetchFailure();
    }

    if (!modificationsStore.isModificationModeEnabled) {
      this.startPolling();
    }

    return response.data;
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

  startPolling = () => {
    if (processInstanceDetailsStore.isRunning && this.intervalId === null) {
      this.intervalId = setInterval(() => {
        if (!this.isPollRequestRunning) {
          this.pollInstances();
        }
      }, 5000);
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
    const {processInstance} = processInstanceDetailsStore.state;
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
      flowNodeId: processInstance.bpmnProcessId,
    };
  }
}

export const flowNodeInstanceStore = new FlowNodeInstance();
export type {FlowNodeInstanceType as FlowNodeInstance, FlowNodeInstances};
