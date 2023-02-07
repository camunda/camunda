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
import {fetchSequenceFlows} from 'modules/api/processInstances/sequenceFlows';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {getProcessedSequenceFlows} from './mappers';
import {isInstanceRunning} from './utils/isInstanceRunning';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {isEqual} from 'lodash';

type State = {
  items: string[];
};

const DEFAULT_STATE: State = {
  items: [],
};

class SequenceFlows extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  isPollRequestRunning: boolean = false;
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
      () => processInstanceDetailsStore.state.processInstance?.id !== undefined,
      () => {
        const instanceId =
          processInstanceDetailsStore.state.processInstance?.id;
        if (instanceId !== undefined) {
          this.fetchProcessSequenceFlows(instanceId);
        }
      }
    );

    this.disposer = autorun(() => {
      const {processInstance} = processInstanceDetailsStore.state;

      if (isInstanceRunning(processInstance)) {
        if (this.intervalId === null && processInstance?.id !== undefined) {
          this.startPolling(processInstance?.id);
        }
      } else {
        this.stopPolling();
      }
    });
  }

  fetchProcessSequenceFlows = this.retryOnConnectionLost(
    async (instanceId: ProcessInstanceEntity['id']) => {
      const response = await fetchSequenceFlows(instanceId);

      if (response.isSuccess) {
        this.setItems(getProcessedSequenceFlows(response.data));
      }
    }
  );

  handlePolling = async (instanceId: ProcessInstanceEntity['id']) => {
    this.isPollRequestRunning = true;
    const {isSuccess, data} = await fetchSequenceFlows(instanceId);

    if (this.intervalId !== null && isSuccess) {
      this.setItems(getProcessedSequenceFlows(data));
    }

    if (!isSuccess) {
      logger.error('Failed to poll Sequence Flows');
    }

    this.isPollRequestRunning = false;
  };

  startPolling = async (instanceId: ProcessInstanceEntity['id']) => {
    this.intervalId = setInterval(() => {
      if (!this.isPollRequestRunning) {
        this.handlePolling(instanceId);
      }
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
    if (!isEqual(this.state.items, items)) {
      this.state.items = items;
    }
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
