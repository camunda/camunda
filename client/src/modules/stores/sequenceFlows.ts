/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  makeObservable,
  observable,
  action,
  when,
  autorun,
  IReactionDisposer,
  override,
} from 'mobx';
import {fetchSequenceFlows} from 'modules/api/instances';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {getProcessedSequenceFlows} from './mappers';
import {isInstanceRunning} from './utils/isInstanceRunning';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type State = {
  items: string[];
};

const DEFAULT_STATE: State = {
  items: [],
};

class SequenceFlows extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  intervalId: null | ReturnType<typeof setInterval> = null;
  disposer: null | IReactionDisposer = null;
  processSeqenceFlowsDisposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      setItems: action,
      reset: override,
    });
  }

  init() {
    this.processSeqenceFlowsDisposer = when(
      () => currentInstanceStore.state.instance?.id !== undefined,
      () => {
        const instanceId = currentInstanceStore.state.instance?.id;
        if (instanceId !== undefined) {
          this.fetchProcessSequenceFlows(instanceId);
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

  fetchProcessSequenceFlows = this.retryOnConnectionLost(
    async (instanceId: ProcessInstanceEntity['id']) => {
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
    }
  );

  handlePolling = async (instanceId: ProcessInstanceEntity['id']) => {
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

  startPolling = async (instanceId: ProcessInstanceEntity['id']) => {
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

  reset() {
    super.reset();
    this.stopPolling();
    this.state = {...DEFAULT_STATE};

    this.disposer?.();
    this.processSeqenceFlowsDisposer?.();
  }
}

export const sequenceFlowsStore = new SequenceFlows();
