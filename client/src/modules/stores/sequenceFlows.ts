/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  observable,
  decorate,
  action,
  when,
  autorun,
  IReactionDisposer,
} from 'mobx';
import {fetchSequenceFlows} from 'modules/api/instances';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {getProcessedSequenceFlows} from './mappers';
import {isInstanceRunning} from './utils/isInstanceRunning';

type State = {
  items: string[];
};

const DEFAULT_STATE: State = {
  items: [],
};

class SequenceFlows {
  state: State = {...DEFAULT_STATE};
  intervalId: null | number = null;
  disposer: null | IReactionDisposer = null;

  init() {
    when(
      () => currentInstanceStore.state.instance?.id !== undefined,
      () => {
        const instanceId = currentInstanceStore.state.instance?.id;
        if (instanceId !== undefined) {
          this.fetchWorkflowSequenceFlows(instanceId);
        }
      }
    );

    this.disposer = autorun(() => {
      const {instance} = currentInstanceStore.state;

      if (isInstanceRunning(instance)) {
        if (this.intervalId === null && instance?.id !== undefined) {
          this.startPolling(instance?.id);
        }
      } else {
        this.stopPolling();
      }
    });
  }

  fetchWorkflowSequenceFlows = async (instanceId: any) => {
    const response = await fetchSequenceFlows(instanceId);
    if (response.ok) {
      const processedSequenceFlows = getProcessedSequenceFlows(
        await response.json()
      );
      this.setItems(processedSequenceFlows);
    }
  };

  handlePolling = async (instanceId: any) => {
    const response = await fetchSequenceFlows(instanceId);
    if (this.intervalId !== null && response.ok) {
      const processedSequenceFlows = getProcessedSequenceFlows(
        await response.json()
      );
      this.setItems(processedSequenceFlows);
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

  setItems(items: any) {
    this.state.items = items;
  }

  reset = () => {
    this.stopPolling();
    this.state = {...DEFAULT_STATE};

    this.disposer?.();
  };
}

decorate(SequenceFlows, {
  state: observable,
  setItems: action,
  reset: action,
});

export const sequenceFlowsStore = new SequenceFlows();
