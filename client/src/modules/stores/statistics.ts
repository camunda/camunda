/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, autorun, IReactionDisposer} from 'mobx';
import {fetchWorkflowCoreStatistics} from 'modules/api/instances';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {instancesStore} from 'modules/stores/instances';

type StatisticsType = {
  running: number;
  active: number;
  withIncidents: number;
};

type State = StatisticsType & {
  status: 'initial' | 'first-fetch' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  running: 0,
  active: 0,
  withIncidents: 0,
  status: 'initial',
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
    if (this.state.status === 'initial') {
      this.startFirstFetch();
    }

    try {
      const response = await fetchWorkflowCoreStatistics();

      if (response.ok) {
        this.setStatistics(await response.json());
      } else {
        this.setError();
      }
    } catch {
      this.setError();
    }
  };

  startFirstFetch = () => {
    this.state.status = 'first-fetch';
  };

  setError = () => {
    this.state.status = 'error';
  };

  setStatistics = ({running, active, withIncidents}: StatisticsType) => {
    this.state.running = running;
    this.state.active = active;
    this.state.withIncidents = withIncidents;
    this.state.status = 'fetched';
  };

  handlePolling = async () => {
    try {
      const response = await fetchWorkflowCoreStatistics();

      if (this.intervalId !== null) {
        if (response.ok) {
          this.setStatistics(await response.json());
        } else {
          this.setError();
        }
      }
    } catch {
      if (this.intervalId !== null) {
        this.setError();
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

    this.stopPolling();
    this.pollingDisposer?.();
    this.fetchStatisticsDisposer?.();
  };
}

decorate(Statistics, {
  state: observable,
  setError: action,
  setStatistics: action,
  startFirstFetch: action,
  reset: action,
});

export const statisticsStore = new Statistics();
