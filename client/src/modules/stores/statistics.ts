/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, autorun, IReactionDisposer} from 'mobx';
import {fetchWorkflowCoreStatistics} from 'modules/api/instances';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {instancesStore} from 'modules/stores/instances';

type State = {
  running: number;
  active: number;
  withIncidents: number;
  isLoaded: boolean;
  isFailed: boolean;
};

const DEFAULT_STATE: State = {
  running: 0,
  active: 0,
  withIncidents: 0,
  isLoaded: false,
  isFailed: false,
};

class Statistics {
  state: State = {...DEFAULT_STATE};
  intervalId: null | number = null;
  pollingDisposer: null | IReactionDisposer = null;
  fetchStatisticsDisposer: null | IReactionDisposer = null;
  init() {
    this.fetchStatistics();

    this.pollingDisposer = autorun(() => {
      if (currentInstanceStore.state.instance != null) {
        if (this.intervalId === null) {
          this.startPolling();
        }
      } else {
        this.stopPolling();
      }
    });

    this.fetchStatisticsDisposer = autorun(() => {
      if (instancesStore.state.instancesWithCompletedOperations.length > 0) {
        this.fetchStatistics();
      }
    });
  }

  fetchStatistics = async () => {
    const {coreStatistics} = await fetchWorkflowCoreStatistics();
    if (coreStatistics.error) {
      this.setError();
    } else {
      this.setStatistics(coreStatistics);
    }
  };

  setError() {
    this.state.isLoaded = true;
    this.state.isFailed = true;
  }

  setStatistics = ({
    running,
    active,
    withIncidents,
  }: {
    running: number;
    active: number;
    withIncidents: number;
  }) => {
    this.state.running = running;
    this.state.active = active;
    this.state.withIncidents = withIncidents;
    this.state.isLoaded = true;
    this.state.isFailed = false;
  };

  handlePolling = async () => {
    const {coreStatistics} = await fetchWorkflowCoreStatistics();

    if (this.intervalId !== null) {
      if (coreStatistics.error) {
        this.setError();
      } else {
        this.setStatistics(coreStatistics);
      }
    }
  };

  startPolling = async () => {
    this.intervalId = setInterval(() => {
      this.handlePolling();
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
    this.state = {...DEFAULT_STATE};

    this.pollingDisposer?.();
    this.fetchStatisticsDisposer?.();
  };
}

decorate(Statistics, {
  state: observable,
  setError: action,
  setStatistics: action,
  reset: action,
});

export const statisticsStore = new Statistics();
