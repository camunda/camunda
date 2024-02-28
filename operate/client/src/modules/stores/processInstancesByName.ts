/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {action, makeObservable, observable, override} from 'mobx';
import {
  fetchProcessInstancesByName,
  ProcessInstanceByNameDto,
} from 'modules/api/incidents/fetchProcessInstancesByName';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import isEqual from 'lodash/isEqual';

type State = {
  processInstances: ProcessInstanceByNameDto[];
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  processInstances: [],
  status: 'initial',
};

class ProcessInstancesByName extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  isPollRequestRunning: boolean = false;
  intervalId: null | ReturnType<typeof setInterval> = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      startFetching: action,
      setError: action,
      setProcessInstances: action,
      reset: override,
    });
  }

  init() {
    if (this.intervalId === null) {
      this.startPolling();
    }
  }

  getProcessInstancesByName = this.retryOnConnectionLost(async () => {
    this.startFetching();

    const response = await fetchProcessInstancesByName();

    if (response.isSuccess) {
      this.setProcessInstances(response.data);
    } else {
      this.setError();
    }
  });

  get hasNoInstances() {
    const {processInstances, status} = this.state;
    return status === 'fetched' && processInstances.length === 0;
  }

  startFetching = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };
  setError = () => {
    this.state.status = 'error';
  };

  handlePolling = async () => {
    this.isPollRequestRunning = true;
    const response = await fetchProcessInstancesByName();

    if (this.intervalId !== null) {
      if (response.isSuccess) {
        const instances = response.data;
        if (!isEqual(instances, this.state.processInstances)) {
          this.setProcessInstances(instances);
        }
      } else {
        this.setError();
      }
    }

    this.isPollRequestRunning = false;
  };

  startPolling = async () => {
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

  setProcessInstances = (processInstances: ProcessInstanceByNameDto[]) => {
    this.state.processInstances = processInstances;
    this.state.status = 'fetched';
  };

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
    this.stopPolling();
  }
}

export const processInstancesByNameStore = new ProcessInstancesByName();
