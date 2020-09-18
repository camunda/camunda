/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed, autorun} from 'mobx';
import {fetchWorkflowInstance} from 'modules/api/instances';
import {getWorkflowName} from 'modules/utils/instance';
import {isInstanceRunning} from './utils/isInstanceRunning';

import {PAGE_TITLE} from 'modules/constants';

const DEFAULT_STATE = {
  instance: null,
};

class CurrentInstance {
  state = {...DEFAULT_STATE};
  intervalId = null;
  disposer = null;

  async init(id) {
    const workflowInstance = await fetchWorkflowInstance(id);
    this.setCurrentInstance(workflowInstance);

    this.disposer = autorun(() => {
      if (isInstanceRunning(this.state.instance)) {
        if (this.intervalId === null) {
          this.startPolling(id);
        }
      } else {
        this.stopPolling();
      }
    });
  }

  setCurrentInstance = (currentInstance) => {
    this.state.instance = currentInstance;
  };

  get workflowTitle() {
    if (this.state.instance === null) {
      return null;
    }

    return PAGE_TITLE.INSTANCE(
      this.state.instance.id,
      getWorkflowName(this.state.instance)
    );
  }

  handlePolling = async (instanceId) => {
    const response = await fetchWorkflowInstance(instanceId);

    if (this.intervalId !== null) {
      this.setCurrentInstance(response);
    }
  };

  startPolling = async (instanceId) => {
    this.intervalId = setInterval(() => {
      this.handlePolling(instanceId);
    }, 5000);
  };

  stopPolling = () => {
    clearInterval(this.intervalId);
    this.intervalId = null;
  };

  reset = () => {
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    if (this.disposer !== null) {
      this.disposer();
    }
  };
}

decorate(CurrentInstance, {
  state: observable,
  reset: action,
  setCurrentInstance: action,
  workflowTitle: computed,
});

export const currentInstance = new CurrentInstance();
