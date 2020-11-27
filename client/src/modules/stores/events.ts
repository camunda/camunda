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
import {fetchEvents} from 'modules/api/events';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {isInstanceRunning} from './utils/isInstanceRunning';

type Event = {
  id: string;
  workflowId: string;
  workflowInstanceId: string;
  bpmnProcessId: string;
  activityId: string;
  activityInstanceId: string;
  eventSourceType: string;
  eventType: string;
  dateTime: string;
  metadata: {
    jobType: string;
    jobRetries: number;
    jobWorker: string;
    jobDeadline: string;
    jobCustomHeaders: unknown;
    incidentErrorType: null | string;
    incidentErrorMessage: null | string;
    jobId: string;
  };
};
type State = {
  items: Event[];
};

const DEFAULT_STATE: State = {
  items: [],
};

class Events {
  state: State = {
    ...DEFAULT_STATE,
  };
  intervalId: null | number = null;
  disposer: null | IReactionDisposer = null;

  init() {
    when(
      () => currentInstanceStore.state.instance?.id !== undefined,
      () => {
        const instanceId = currentInstanceStore.state.instance?.id;

        if (instanceId !== undefined) {
          this.fetchWorkflowEvents(instanceId);
        }
      }
    );

    this.disposer = autorun(() => {
      if (isInstanceRunning(currentInstanceStore.state.instance)) {
        const instanceId = currentInstanceStore.state.instance?.id;

        if (this.intervalId === null && instanceId !== undefined) {
          this.startPolling(instanceId);
        }
      } else {
        this.stopPolling();
      }
    });
  }

  fetchWorkflowEvents = async (instanceId: string) => {
    const response = await fetchEvents(instanceId);
    if (response.ok) {
      this.setItems(await response.json());
    }
  };

  handlePolling = async (instanceId: string) => {
    const response = await fetchEvents(instanceId);

    if (this.intervalId !== null && response.ok) {
      this.setItems(await response.json());
    }
  };

  startPolling = async (instanceId: string) => {
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

decorate(Events, {
  state: observable,
  setItems: action,
  reset: action,
});

export const eventsStore = new Events();
