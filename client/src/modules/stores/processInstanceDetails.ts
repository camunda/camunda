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
  computed,
  autorun,
  IReactionDisposer,
  override,
} from 'mobx';
import {fetchProcessInstance} from 'modules/api/processInstances/fetchProcessInstance';
import {createOperation, getProcessName} from 'modules/utils/instance';
import {isInstanceRunning} from './utils/isInstanceRunning';
import {PAGE_TITLE, PERMISSIONS} from 'modules/constants';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {hasActiveOperations} from './utils/hasActiveOperations';
import {tracking} from 'modules/tracking';
import {isEqual} from 'lodash';

type State = {
  processInstance: null | ProcessInstanceEntity;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  processInstance: null,
  status: 'initial',
};

class ProcessInstanceDetails extends NetworkReconnectionHandler {
  state: State = {
    ...DEFAULT_STATE,
  };
  isPollRequestRunning: boolean = false;
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
      setProcessInstance: action,
      activateOperation: action,
      deactivateOperation: action,
      startFetch: action,
      handleFetchSuccess: action,
      handleFetchFailure: action,
      processTitle: computed,
      isRunning: computed,
    });
  }

  init = async ({
    id,
    onRefetchFailure,
    onPollingFailure,
  }: {
    id: ProcessInstanceEntity['id'];
    onRefetchFailure?: () => void;
    onPollingFailure?: () => void;
  }) => {
    await this.fetchProcessInstance(id);
    this.onRefetchFailure = onRefetchFailure;
    this.onPollingFailure = onPollingFailure;

    this.disposer = autorun(() => {
      if (
        isInstanceRunning(this.state.processInstance) ||
        this.state.processInstance?.hasActiveOperation
      ) {
        if (this.intervalId === null) {
          this.startPolling(id);
        }
      } else {
        this.stopPolling();
      }
    });

    const {processInstance} = this.state;

    if (processInstance !== null) {
      tracking.track({
        eventName: 'process-instance-details-loaded',
        state: processInstance.state,
      });
    }
  };

  fetchProcessInstance = this.retryOnConnectionLost(
    async (id: ProcessInstanceEntity['id']) => {
      this.startFetch();
      const response = await fetchProcessInstance(id);

      if (response.isSuccess) {
        this.handleFetchSuccess(response.data);
        this.resetRefetch();
      } else {
        if (response.statusCode === 404) {
          this.handleRefetch(id);
        } else {
          this.handleFetchFailure();
        }
      }
    }
  );

  setProcessInstance = (processInstance: ProcessInstanceEntity | null) => {
    if (!isEqual(this.state.processInstance, processInstance)) {
      this.state.processInstance = processInstance;
    }
  };

  activateOperation = (operationType: OperationEntityType) => {
    if (this.state.processInstance !== null) {
      this.state.processInstance.hasActiveOperation = true;
      this.state.processInstance.operations.push(
        createOperation(operationType)
      );
    }
  };

  deactivateOperation = (operationType: OperationEntityType) => {
    if (this.state.processInstance !== null) {
      this.state.processInstance.operations =
        this.state.processInstance.operations.filter(
          ({type, id}) => !(type === operationType && id === undefined)
        );

      if (!hasActiveOperations(this.state.processInstance.operations)) {
        this.state.processInstance.hasActiveOperation = false;
      }
    }
  };

  get processTitle() {
    if (this.state.processInstance === null) {
      return null;
    }

    return PAGE_TITLE.INSTANCE(
      this.state.processInstance.id,
      getProcessName(this.state.processInstance)
    );
  }

  get isRunning() {
    const {processInstance} = this.state;

    if (processInstance === null) {
      return false;
    } else {
      return ['ACTIVE', 'INCIDENT'].includes(processInstance.state);
    }
  }

  getPermissions = () => {
    const {processInstance} = this.state;
    if (!window.clientConfig?.resourcePermissionsEnabled) {
      return PERMISSIONS;
    }

    if (processInstance === null) {
      return [];
    }

    return processInstance.permissions;
  };

  hasPermission = (scopes: ResourceBasedPermissionDto[]) => {
    return scopes.some((permission) =>
      this.getPermissions()?.includes(permission)
    );
  };

  startFetch = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  handleFetchSuccess = (processInstance: ProcessInstanceEntity | null) => {
    this.state.processInstance = processInstance;
    this.state.status = 'fetched';
  };

  handleFetchFailure = () => {
    this.state.status = 'error';
  };

  handleRefetch = (id: ProcessInstanceEntity['id']) => {
    if (this.retryCount < 3) {
      this.retryCount += 1;

      this.refetchTimeout = setTimeout(() => {
        this.fetchProcessInstance(id);
      }, 5000);
    } else {
      this.retryCount = 0;
      this.handleFetchFailure();
      this.onRefetchFailure?.();
    }
  };

  handlePolling = async (instanceId: any) => {
    this.isPollRequestRunning = true;
    const response = await fetchProcessInstance(instanceId);

    if (this.intervalId !== null) {
      if (response.isSuccess) {
        this.setProcessInstance(response.data);
      } else {
        if (
          !isInstanceRunning(this.state.processInstance) &&
          response.statusCode === 404
        ) {
          this.onPollingFailure?.();
        }
        logger.error('Failed to poll process instance');
      }
    }

    this.isPollRequestRunning = false;
  };

  startPolling = async (instanceId: any) => {
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

export const processInstanceDetailsStore = new ProcessInstanceDetails();
