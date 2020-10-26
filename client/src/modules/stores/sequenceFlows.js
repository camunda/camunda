/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, when, autorun} from 'mobx';
import {fetchSequenceFlows} from 'modules/api/instances';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {getProcessedSequenceFlows} from './mappers';
import {isInstanceRunning} from './utils/isInstanceRunning';

const DEFAULT_STATE = {
  items: [],
};

class SequenceFlows {
  state = {...DEFAULT_STATE};
  intervalId = null;
  disposer = null;

  init() {
    when(
      () => currentInstanceStore.state.instance?.id !== undefined,
      () => {
        this.fetchWorkflowSequenceFlows(currentInstanceStore.state.instance.id);
      }
    );

    this.disposer = autorun(() => {
      const {instance} = currentInstanceStore.state;

      if (isInstanceRunning(instance)) {
        if (this.intervalId === null) {
          this.startPolling(instance.id);
        }
      } else {
        this.stopPolling();
      }
    });
  }

  fetchWorkflowSequenceFlows = async (instanceId) => {
    const response = await fetchSequenceFlows(instanceId);
    const processedSequenceFlows = getProcessedSequenceFlows(response);
    this.setItems(processedSequenceFlows);
  };

  handlePolling = async (instanceId) => {
    const response = await fetchSequenceFlows(instanceId);
    if (this.intervalId !== null) {
      const processedSequenceFlows = getProcessedSequenceFlows(response);
      this.setItems(processedSequenceFlows);
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

  setItems(items) {
    this.state.items = items;
  }

  reset = () => {
    this.stopPolling();
    this.state = {...DEFAULT_STATE};

    this.disposer?.(); // eslint-disable-line no-unused-expressions
  };
}

decorate(SequenceFlows, {
  state: observable,
  setItems: action,
  reset: action,
});

export const sequenceFlowsStore = new SequenceFlows();
