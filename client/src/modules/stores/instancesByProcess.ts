/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {action, makeObservable, observable, override} from 'mobx';

import {fetchInstancesByProcess} from 'modules/api/incidents';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

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

  setInstances = (instances: InstanceByProcess[]) => {
    this.state.instances = instances;
    this.state.status = 'fetched';
  };

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
  }
}

export const instancesByProcessStore = new InstancesByProcess();
