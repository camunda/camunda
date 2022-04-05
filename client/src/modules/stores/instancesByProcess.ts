/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {action, makeObservable, observable, override} from 'mobx';

import {fetchInstancesByProcess} from 'modules/api/incidents';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {isEqual} from 'lodash';

type Process = {
  processId: string;
  version: number;
  name: null | string;
  bpmnProcessId: string;
  errorMessage: null | string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
};
type InstanceByProcess = {
  bpmnProcessId: string;
  processName: null | string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
  processes: Process[];
};
type State = {
  instances: InstanceByProcess[];
  status: 'initial' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  instances: [],
  status: 'initial',
};

class InstancesByProcess extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  intervalId: null | ReturnType<typeof setInterval> = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      startFetching: action,
      setError: action,
      setInstances: action,
      reset: override,
    });
  }

  init() {
    this.getInstancesByProcess();

    if (this.intervalId === null) {
      this.startPolling();
    }
  }

  getInstancesByProcess = this.retryOnConnectionLost(async () => {
    this.startFetching();
    try {
      const response = await fetchInstancesByProcess();

      if (response.ok) {
        this.setInstances(await response.json());
      } else {
        this.setError();
      }
    } catch {
      this.setError();
    }
  });

  startFetching = () => {
    this.state.status = 'fetching';
  };

  setError = () => {
    this.state.status = 'error';
  };

  handlePolling = async () => {
    try {
      const response = await fetchInstancesByProcess();

      if (this.intervalId !== null) {
        if (response.ok) {
          const instances = await response.json();
          if (!isEqual(instances, this.state.instances)) {
            this.setInstances(instances);
          }
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

  setInstances = (instances: InstanceByProcess[]) => {
    this.state.instances = instances;
    this.state.status = 'fetched';
  };

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
    this.stopPolling();
  }
}

export const instancesByProcessStore = new InstancesByProcess();
