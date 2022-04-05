/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  makeObservable,
  when,
  IReactionDisposer,
  override,
  action,
  computed,
  observable,
} from 'mobx';
import {fetchFlowNodeStates} from 'modules/api/flowNodeStates';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type State = {
  flowNodes: {
    [flowNodeId: string]: InstanceEntityState;
  };
  status: 'initial' | 'fetched' | 'error';
};
const DEFAULT_STATE: State = {
  flowNodes: {},
  status: 'initial',
};

class FlowNodeStates extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  intervalId: null | ReturnType<typeof setInterval> = null;
  flowNodeStatesDisposer: null | IReactionDisposer = null;
  completedFlowNodesDisposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      handleFetchSuccess: action,
      handleFetchFailure: action,
      areAllFlowNodesCompleted: computed,
      selectableFlowNodes: computed,
      reset: override,
    });
  }

  init = (processInstanceId: string) => {
    this.flowNodeStatesDisposer = when(
      () => currentInstanceStore.state.instance !== null,
      () => {
        this.fetchFlowNodeStates(processInstanceId);
        this.startPolling(processInstanceId);
      }
    );

    this.completedFlowNodesDisposer = when(
      () => this.intervalId !== null && this.areAllFlowNodesCompleted,
      this.stopPolling
    );
  };

  fetchFlowNodeStates = this.retryOnConnectionLost(
    async (processInstanceId: string) => {
      try {
        const response = await fetchFlowNodeStates(processInstanceId);
        if (response.ok) {
          this.handleFetchSuccess(await response.json());
        } else {
          this.handleFetchFailure();
        }
      } catch (error) {
        this.handleFetchFailure(error);
      }
    }
  );

  handleFetchSuccess = (flowNodes: State['flowNodes']) => {
    this.state.flowNodes = flowNodes;
    this.state.status = 'fetched';
  };

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';

    logger.error('Failed to fetch flow node states');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  startPolling = (processInstanceId: string) => {
    this.intervalId = setInterval(() => {
      this.fetchFlowNodeStates(processInstanceId);
    }, 5000);
  };

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  get areAllFlowNodesCompleted() {
    return (
      this.state.status !== 'initial' &&
      Object.values(this.state.flowNodes).every((flowNodeState) =>
        ['COMPLETED', 'TERMINATED', 'CANCELED'].includes(flowNodeState)
      )
    );
  }

  get selectableFlowNodes() {
    return Object.keys(this.state.flowNodes);
  }

  reset() {
    super.reset();
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.flowNodeStatesDisposer?.();
    this.completedFlowNodesDisposer?.();
  }
}

export const flowNodeStatesStore = new FlowNodeStates();
