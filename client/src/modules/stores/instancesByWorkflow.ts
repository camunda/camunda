/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed} from 'mobx';

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
  isLoaded: boolean;
  isFailed: boolean;
};

const DEFAULT_STATE: State = {
  instances: [],
  isLoaded: false,
  isFailed: false,
};

class InstancesByWorkflow {
  state: State = {...DEFAULT_STATE};

  getInstancesByWorkflow = async () => {
    const {data} = await fetchInstancesByWorkflow();

    if (data.error) {
      this.setError();
    } else {
      this.setInstances(data);
    }
  };

  setError() {
    this.state.isLoaded = true;
    this.state.isFailed = true;
  }

  setInstances(instances: any) {
    this.state.instances = instances;
    this.state.isLoaded = true;
    this.state.isFailed = false;
  }

  get isDataAvailable() {
    const {instances, isLoaded, isFailed} = this.state;
    return !isFailed && isLoaded && instances.length > 0;
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

decorate(InstancesByWorkflow, {
  state: observable,
  setError: action,
  setInstances: action,
  reset: action,
  isDataAvailable: computed,
});

export const instancesByWorkflowStore = new InstancesByWorkflow();
