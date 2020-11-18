/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action} from 'mobx';

import {fetchInstancesByWorkflow} from 'modules/api/incidents';

type Workflow = {
  workflowId: string;
  version: number;
  name: null | string;
  bpmnProcessId: string;
  errorMessage: null | string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
};
type InstanceByWorkflow = {
  bpmnProcessId: string;
  workflowName: null | string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
  workflows: Workflow[];
};
type State = {
  instances: InstanceByWorkflow[];
  status: 'initial' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  instances: [],
  status: 'initial',
};

class InstancesByWorkflow {
  state: State = {...DEFAULT_STATE};

  getInstancesByWorkflow = async () => {
    this.startFetching();
    try {
      const response = await fetchInstancesByWorkflow();

      if (response.ok) {
        this.setInstances(await response.json());
      } else {
        this.setError();
      }
    } catch {
      this.setError();
    }
  };

  startFetching = () => {
    this.state.status = 'fetching';
  };

  setError = () => {
    this.state.status = 'error';
  };

  setInstances = (instances: InstanceByWorkflow[]) => {
    this.state.instances = instances;
    this.state.status = 'fetched';
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

decorate(InstancesByWorkflow, {
  state: observable,
  startFetching: action,
  setError: action,
  setInstances: action,
  reset: action,
});

export const instancesByWorkflowStore = new InstancesByWorkflow();
