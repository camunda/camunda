/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, autorun} from 'mobx';
import {fetchWorkflowCoreStatistics} from 'modules/api/instances';
import {currentInstance} from 'modules/stores/currentInstance';
import {instances} from 'modules/stores/instances';

const DEFAULT_STATE = {
  running: 0,
  active: 0,
  withIncidents: 0,
  isLoaded: false,
  isFailed: false,
};

class Statistics {
  state = {...DEFAULT_STATE};
  pollingDisposer = null;
  fetchStatisticsDisposer = null;
  init() {
    this.fetchStatistics();

    this.pollingDisposer = autorun(() => {
      if (currentInstance.state.instance != null) {
        if (this.intervalId === null) {
          this.startPolling();
        }
      } else {
        this.stopPolling();
      }
    });

    this.fetchStatisticsDisposer = autorun(() => {
      if (instances.state.instancesWithCompletedOperations.length > 0) {
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

  setStatistics = (coreStatistics) => {
    const {running, active, withIncidents} = coreStatistics;
    this.state = {
      running,
      active,
      withIncidents,
    };

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
    clearInterval(this.intervalId);
    this.intervalId = null;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};

    this.pollingDisposer?.(); // eslint-disable-line no-unused-expressions
    this.fetchStatisticsDisposer?.(); // eslint-disable-line no-unused-expressions
  };
}

decorate(Statistics, {
  state: observable,
  setError: action,
  setStatistics: action,
  reset: action,
});

export const statistics = new Statistics();
