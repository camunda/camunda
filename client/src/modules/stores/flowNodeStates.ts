/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeAutoObservable, when} from 'mobx';
import {fetchFlowNodeStates} from 'modules/api/flowNodeStates';
import {logger} from 'modules/logger';

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

class FlowNodeStates {
  state: State = {...DEFAULT_STATE};
  intervalId: null | ReturnType<typeof setInterval> = null;

  constructor() {
    makeAutoObservable(this, {
      init: false,
      fetchFlowNodeStates: false,
      startPolling: false,
      stopPolling: false,
    });
  }

  init = (processInstanceId: string) => {
    this.fetchFlowNodeStates(processInstanceId);
    this.startPolling(processInstanceId);
    when(() => this.areAllFlowNodesCompleted, this.stopPolling);
  };

  fetchFlowNodeStates = async (processInstanceId: string) => {
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
  };

  reset = () => {
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
  };

  handleFetchSuccess = (flowNodes: State['flowNodes']) => {
    this.state.flowNodes = flowNodes;
    this.state.status = 'fetched';
  };

  handleFetchFailure = (error?: Error) => {
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
}

export const flowNodeStatesStore = new FlowNodeStates();
