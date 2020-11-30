/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  observable,
  decorate,
  action,
  computed,
  autorun,
  IReactionDisposer,
} from 'mobx';
import {fetchWorkflowInstance} from 'modules/api/instances';
import {getWorkflowName} from 'modules/utils/instance';
import {isInstanceRunning} from './utils/isInstanceRunning';

import {PAGE_TITLE} from 'modules/constants';

type State = {
  instance: null | InstanceEntity;
};

const DEFAULT_STATE: State = {
  instance: null,
};

class CurrentInstance {
  state: State = {
    ...DEFAULT_STATE,
  };
  intervalId: null | number = null;
  disposer: null | IReactionDisposer = null;

  async init(id: any) {
    const response = await fetchWorkflowInstance(id);
    if (response.ok) {
      this.setCurrentInstance(await response.json());
    }

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

  setCurrentInstance = (currentInstance: any) => {
    this.state.instance = currentInstance;
  };

  activateOperation = () => {
    if (this.state.instance != null) {
      this.state.instance.hasActiveOperation = true;
    }
  };

  deactivateOperation = () => {
    if (this.state.instance != null) {
      this.state.instance.hasActiveOperation = false;
    }
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

  handlePolling = async (instanceId: any) => {
    const response = await fetchWorkflowInstance(instanceId);

    if (this.intervalId !== null && response.ok) {
      this.setCurrentInstance(await response.json());
    }
  };

  startPolling = async (instanceId: any) => {
    this.intervalId = setInterval(() => {
      this.handlePolling(instanceId);
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
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
  };
}

decorate(CurrentInstance, {
  state: observable,
  reset: action,
  setCurrentInstance: action,
  activateOperation: action,
  deactivateOperation: action,
  workflowTitle: computed,
});

export const currentInstanceStore = new CurrentInstance();
