/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, when, autorun} from 'mobx';
import {fetchEvents} from 'modules/api/events';
import {currentInstance} from 'modules/stores/currentInstance';
import {isInstanceRunning} from './utils/isInstanceRunning';

const DEFAULT_STATE = {
  items: [],
};

class Events {
  state = {...DEFAULT_STATE};
  intervalId = null;
  disposer = null;

  init() {
    when(
      () => currentInstance.state.instance?.id !== undefined,
      () => {
        this.fetchWorkflowEvents(currentInstance.state.instance.id);
      }
    );

    this.disposer = autorun(() => {
      if (isInstanceRunning(currentInstance.state.instance)) {
        if (this.intervalId === null) {
          this.startPolling(currentInstance.state.instance.id);
        }
      } else {
        this.stopPolling();
      }
    });
  }

  fetchWorkflowEvents = async (instanceId) => {
    this.setItems(await fetchEvents(instanceId));
  };

  handlePolling = async (instanceId) => {
    const response = await fetchEvents(instanceId);

    if (this.intervalId !== null) {
      this.setItems(response);
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
    if (this.disposer !== null) {
      this.disposer();
    }
  };
}

decorate(Events, {
  state: observable,
  setItems: action,
  reset: action,
});

export const events = new Events();
