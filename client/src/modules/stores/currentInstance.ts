/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  makeObservable,
  observable,
  action,
  computed,
  autorun,
  IReactionDisposer,
} from 'mobx';
import {fetchProcessInstance} from 'modules/api/instances';
import {getProcessName} from 'modules/utils/instance';
import {isInstanceRunning} from './utils/isInstanceRunning';

import {PAGE_TITLE} from 'modules/constants';

type State = {
  instance: null | ProcessInstanceEntity;
};

const DEFAULT_STATE: State = {
  instance: null,
};

class CurrentInstance {
  state: State = {
    ...DEFAULT_STATE,
  };
  intervalId: null | ReturnType<typeof setInterval> = null;
  disposer: null | IReactionDisposer = null;

  constructor() {
    makeObservable(this, {
      state: observable,
      reset: action,
      setCurrentInstance: action,
      activateOperation: action,
      deactivateOperation: action,
      processTitle: computed,
    });
  }

  async init(id: any) {
    const response = await fetchProcessInstance(id);
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
    if (this.state.instance !== null) {
      this.state.instance.hasActiveOperation = true;
    }
  };

  deactivateOperation = () => {
    if (this.state.instance !== null) {
      this.state.instance.hasActiveOperation = false;
    }
  };

  get processTitle() {
    if (this.state.instance === null) {
      return null;
    }

    return PAGE_TITLE.INSTANCE(
      this.state.instance.id,
      getProcessName(this.state.instance)
    );
  }

  get isRunning() {
    const {instance} = this.state;

    if (instance === null) {
      return false;
    } else {
      return ['ACTIVE', 'INCIDENT'].includes(instance.state);
    }
  }

  handlePolling = async (instanceId: any) => {
    const response = await fetchProcessInstance(instanceId);

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

export const currentInstanceStore = new CurrentInstance();
