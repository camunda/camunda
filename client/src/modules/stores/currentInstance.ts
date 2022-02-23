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
  override,
} from 'mobx';
import {fetchProcessInstance} from 'modules/api/instances';
import {createOperation, getProcessName} from 'modules/utils/instance';
import {isInstanceRunning} from './utils/isInstanceRunning';
import {PAGE_TITLE} from 'modules/constants';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {hasActiveOperations} from './utils/hasActiveOperations';

type State = {
  instance: null | ProcessInstanceEntity;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  instance: null,
  status: 'initial',
};

class CurrentInstance extends NetworkReconnectionHandler {
  state: State = {
    ...DEFAULT_STATE,
  };
  intervalId: null | ReturnType<typeof setInterval> = null;
  disposer: null | IReactionDisposer = null;
  retryCount: number = 0;
  refetchTimeout: NodeJS.Timeout | null = null;
  onRefetchFailure?: () => void;
  onPollingFailure?: () => void;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      setCurrentInstance: action,
      activateOperation: action,
      deactivateOperation: action,
      startFetch: action,
      handleFetchSuccess: action,
      handleFetchFailure: action,
      processTitle: computed,
      isRunning: computed,
    });
  }

  init({
    id,
    onRefetchFailure,
    onPollingFailure,
  }: {
    id: ProcessInstanceEntity['id'];
    onRefetchFailure?: () => void;
    onPollingFailure?: () => void;
  }) {
    this.fetchCurrentInstance(id);
    this.onRefetchFailure = onRefetchFailure;
    this.onPollingFailure = onPollingFailure;

    this.disposer = autorun(() => {
      if (
        isInstanceRunning(this.state.instance) ||
        this.state.instance?.hasActiveOperation
      ) {
        if (this.intervalId === null) {
          this.startPolling(id);
        }
      } else {
        this.stopPolling();
      }
    });
  }

  fetchCurrentInstance = this.retryOnConnectionLost(
    async (id: ProcessInstanceEntity['id']) => {
      this.startFetch();
      try {
        const response = await fetchProcessInstance(id);

        if (response.ok) {
          this.handleFetchSuccess(await response.json());
          this.resetRefetch();
        } else {
          if (response.status === 404) {
            this.handleRefetch(id);
          } else {
            this.handleFetchFailure();
          }
        }
      } catch (error) {
        this.handleFetchFailure(error);
      }
    }
  );

  setCurrentInstance = (currentInstance: ProcessInstanceEntity | null) => {
    this.state.instance = currentInstance;
  };

  activateOperation = (operationType: OperationEntityType) => {
    if (this.state.instance !== null) {
      this.state.instance.hasActiveOperation = true;
      this.state.instance.operations.push(createOperation(operationType));
    }
  };

  deactivateOperation = (operationType: OperationEntityType) => {
    if (this.state.instance !== null) {
      this.state.instance.operations = this.state.instance.operations.filter(
        ({type, id}) => !(type === operationType && id === undefined)
      );

      if (!hasActiveOperations(this.state.instance.operations)) {
        this.state.instance.hasActiveOperation = false;
      }
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

  startFetch = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  handleFetchSuccess = (currentInstance: ProcessInstanceEntity | null) => {
    this.state.instance = currentInstance;
    this.state.status = 'fetched';
  };

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';

    logger.error('Failed to fetch process instance');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  handleRefetch = (id: ProcessInstanceEntity['id']) => {
    if (this.retryCount < 3) {
      this.retryCount += 1;

      this.refetchTimeout = setTimeout(() => {
        this.fetchCurrentInstance(id);
      }, 5000);
    } else {
      this.retryCount = 0;
      this.handleFetchFailure();
      this.onRefetchFailure?.();
    }
  };

  handlePolling = async (instanceId: any) => {
    try {
      const response = await fetchProcessInstance(instanceId);

      if (this.intervalId !== null) {
        if (response.ok) {
          this.setCurrentInstance(await response.json());
        } else {
          if (
            !isInstanceRunning(this.state.instance) &&
            response.status === 404
          ) {
            this.onPollingFailure?.();
          }
          logger.error('Failed to poll process instance');
        }
      }
    } catch (error) {
      logger.error('Failed to poll process instance');
      logger.error(error);
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

  resetRefetch = () => {
    if (this.refetchTimeout !== null) {
      clearTimeout(this.refetchTimeout);
    }

    this.retryCount = 0;
  };

  reset() {
    super.reset();
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
    this.resetRefetch();
    this.onRefetchFailure = undefined;
    this.onPollingFailure = undefined;
  }
}

export const currentInstanceStore = new CurrentInstance();
