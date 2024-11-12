/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  makeObservable,
  observable,
  action,
  IReactionDisposer,
  override,
} from 'mobx';
import {
  fetchProcessCoreStatistics,
  CoreStatisticsDto,
} from 'modules/api/processInstances/fetchProcessCoreStatistics';
import {processInstancesStore} from 'modules/stores/processInstances';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type State = CoreStatisticsDto & {
  status: 'initial' | 'first-fetch' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  running: 0,
  active: 0,
  withIncidents: 0,
  status: 'initial',
};

class Statistics extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  isPollRequestRunning: boolean = false;
  intervalId: null | ReturnType<typeof setInterval> = null;
  pollingDisposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      setError: action,
      setStatistics: action,
      startFirstFetch: action,
      reset: override,
    });
  }

  init() {
    if (this.intervalId === null) {
      this.startPolling();
    }

    processInstancesStore.addCompletedOperationsHandler(() =>
      this.fetchStatistics({isPolling: true}),
    );
  }

  fetchStatistics: (
    options?: Parameters<typeof fetchProcessCoreStatistics>[0],
  ) => Promise<void> = this.retryOnConnectionLost(
    async (options?: Parameters<typeof fetchProcessCoreStatistics>[0]) => {
      if (this.state.status === 'initial') {
        this.startFirstFetch();
      }

      const response = await fetchProcessCoreStatistics({
        isPolling: options?.isPolling,
      });

      if (response.isSuccess) {
        this.setStatistics(response.data);
      } else {
        this.setError();
      }
    },
  );

  startFirstFetch = () => {
    this.state.status = 'first-fetch';
  };

  setError = () => {
    this.state.status = 'error';
  };

  setStatistics = ({running, active, withIncidents}: CoreStatisticsDto) => {
    this.state.running = running;
    this.state.active = active;
    this.state.withIncidents = withIncidents;
    this.state.status = 'fetched';
  };

  handlePolling = async () => {
    this.isPollRequestRunning = true;
    const response = await fetchProcessCoreStatistics({isPolling: true});

    if (this.intervalId !== null) {
      if (response.isSuccess) {
        this.setStatistics(response.data);
      } else {
        this.setError();
      }
    }

    this.isPollRequestRunning = false;
  };

  startPolling = () => {
    this.intervalId = setInterval(() => {
      if (!this.isPollRequestRunning) {
        this.handlePolling();
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

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};

    this.stopPolling();
    this.pollingDisposer?.();
  }
}

export const statisticsStore = new Statistics();
