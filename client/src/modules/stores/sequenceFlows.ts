/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  makeObservable,
  observable,
  action,
  when,
  autorun,
  IReactionDisposer,
} from 'mobx';
import {fetchSequenceFlows} from 'modules/api/instances';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {getProcessedSequenceFlows} from './mappers';
import {isInstanceRunning} from './utils/isInstanceRunning';
import {logger} from 'modules/logger';

type State = {
  items: string[];
};

const DEFAULT_STATE: State = {
  items: [],
};

class SequenceFlows {
  state: State = {...DEFAULT_STATE};
  intervalId: null | ReturnType<typeof setInterval> = null;
  disposer: null | IReactionDisposer = null;

  constructor() {
    makeObservable(this, {
      state: observable,
      setItems: action,
      reset: action,
    });
  }

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

  fetchWorkflowSequenceFlows = async (
    instanceId: WorkflowInstanceEntity['id']
  ) => {
    try {
      const response = await fetchSequenceFlows(instanceId);

      if (response.ok) {
        this.setItems(getProcessedSequenceFlows(await response.json()));
      } else {
        logger.error('Failed to fetch Sequence Flows');
      }
    } catch (error) {
      logger.error('Failed to fetch Sequence Flows');
      logger.error(error);
    }
  };

  handlePolling = async (instanceId: WorkflowInstanceEntity['id']) => {
    try {
      const response = await fetchSequenceFlows(instanceId);

      if (this.intervalId !== null && response.ok) {
        this.setItems(getProcessedSequenceFlows(await response.json()));
      }

      if (!response.ok) {
        logger.error('Failed to poll Sequence Flows');
      }
    } catch (error) {
      logger.error('Failed to poll Sequence Flows');
      logger.error(error);
    }
  };

  startPolling = async (instanceId: WorkflowInstanceEntity['id']) => {
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

  setItems(items: string[]) {
    this.state.items = items;
  }

  reset = () => {
    this.stopPolling();
    this.state = {...DEFAULT_STATE};

    this.disposer?.();
  };
}

export const sequenceFlowsStore = new SequenceFlows();
