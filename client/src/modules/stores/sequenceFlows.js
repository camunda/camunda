/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, when} from 'mobx';
import {fetchSequenceFlows} from 'modules/api/instances';
import {currentInstance} from 'modules/stores/currentInstance';
import {getProcessedSequenceFlows} from './mappers';

const DEFAULT_STATE = {
  items: [],
};

class SequenceFlows {
  state = {...DEFAULT_STATE};
  intervalId = null;

  init() {
    when(
      () => currentInstance.state.instance?.id !== undefined,
      () => {
        this.fetchWorkflowSequenceFlows(currentInstance.state.instance.id);
        this.startPolling(currentInstance.state.instance.id);
      }
    );
  }

  fetchWorkflowSequenceFlows = async (workflowInstanceId) => {
    const response = await fetchSequenceFlows(workflowInstanceId);
    const processedSequenceFlows = getProcessedSequenceFlows(response);
    this.setItems(processedSequenceFlows);
  };

  handlePolling = async (workflowInstanceId) => {
    const response = await fetchSequenceFlows(workflowInstanceId);

    if (this.intervalId !== null) {
      const processedSequenceFlows = getProcessedSequenceFlows(response);
      this.setItems(processedSequenceFlows);
    }
  };

  startPolling = async (workflowInstanceId) => {
    this.intervalId = setInterval(() => {
      this.handlePolling(workflowInstanceId);
    }, 5000);
  };

  stopPolling = () => {
    clearInterval(this.intervalId);
  };

  setItems(items) {
    this.state.items = items;
  }

  reset = () => {
    this.stopPolling();
    this.intervalId = null;
    this.state = {...DEFAULT_STATE};
  };
}

decorate(SequenceFlows, {
  state: observable,
  setItems: action,
  reset: action,
});

export const sequenceFlows = new SequenceFlows();
