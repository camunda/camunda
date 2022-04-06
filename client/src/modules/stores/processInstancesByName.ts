/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {action, makeObservable, observable, override} from 'mobx';
import {fetchProcessInstancesByName} from 'modules/api/incidents';
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
type ProcessInstanceByName = {
  bpmnProcessId: string;
  processName: null | string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
  processes: Process[];
};
type State = {
  processInstances: ProcessInstanceByName[];
  status: 'initial' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  processInstances: [],
  status: 'initial',
};

class ProcessInstancesByName extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
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
    this.getProcessInstancesByName();

    if (this.intervalId === null) {
      this.startPolling();
    }
  }

  getProcessInstancesByName = this.retryOnConnectionLost(async () => {
    this.startFetching();
    try {
      const response = await fetchProcessInstancesByName();

      if (response.ok) {
        this.setProcessInstances(await response.json());
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
      const response = await fetchProcessInstancesByName();

      if (this.intervalId !== null) {
        if (response.ok) {
          const instances = await response.json();
          if (!isEqual(instances, this.state.processInstances)) {
            this.setProcessInstances(instances);
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

  setProcessInstances = (processInstances: ProcessInstanceByName[]) => {
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
