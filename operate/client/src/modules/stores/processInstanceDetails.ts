/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  makeObservable,
  observable,
  action,
  computed,
  autorun,
  override,
  type IReactionDisposer,
} from 'mobx';
import {fetchProcessInstance} from 'modules/api/processInstances/fetchProcessInstance';
import {createOperation, getProcessName} from 'modules/utils/instance';
import {isInstanceRunning} from './utils/isInstanceRunning';
import {PAGE_TITLE, PERMISSIONS} from 'modules/constants';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {hasActiveOperations} from './utils/hasActiveOperations';
import {tracking} from 'modules/tracking';
import isEqual from 'lodash/isEqual';
import isNil from 'lodash/isNil';
import type {
  ProcessInstanceEntity,
  ResourceBasedPermissionDto,
  OperationEntityType,
} from 'modules/types/operate';

type State = {
  processInstance: null | ProcessInstanceEntity;
  status:
    | 'initial'
    | 'first-fetch'
    | 'fetching'
    | 'fetched'
    | 'error'
    | 'forbidden';
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
  refetchTimeout: ReturnType<typeof setTimeout> | null = null;
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
      if (this.isRunning || this.state.processInstance?.hasActiveOperation) {
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
          this.handleFetchFailure(response.statusCode);
        }
      }
    },
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
        createOperation(operationType),
      );
    }
  };

  deactivateOperation = (operationType: OperationEntityType) => {
    if (this.state.processInstance !== null) {
      this.state.processInstance.operations =
        this.state.processInstance.operations.filter(
          ({type, id}) => !(type === operationType && id === undefined),
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
      getProcessName(this.state.processInstance),
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

  get latestMigrationDate() {
    const migrateOperations = this.state.processInstance?.operations
      .filter((operation) => {
        return (
          operation.type === 'MIGRATE_PROCESS_INSTANCE' &&
          // this filters for operations with a completedDate
          !isNil(operation.completedDate)
        );
      })
      .sort(({completedDate: dateA}, {completedDate: dateB}) => {
        // It is safe use the a non-null assertion operator (!) for dateA and dateB here.
        // The value will never be null or undefined, because only operations with a
        // completedDate are filtered above.
        return new Date(dateA!).getTime() - new Date(dateB!).getTime();
      });

    if (migrateOperations !== undefined) {
      const lastMigrationDate =
        migrateOperations[migrateOperations.length - 1]?.completedDate;

      return lastMigrationDate ?? undefined;
    }

    return undefined;
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
      this.getPermissions()?.includes(permission),
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

  handleFetchFailure = (statusCode?: number) => {
    if (statusCode === 403) {
      this.state.status = 'forbidden';
      return;
    }

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

  handlePolling = async (instanceId: ProcessInstanceEntity['id']) => {
    this.isPollRequestRunning = true;
    const response = await fetchProcessInstance(instanceId, {isPolling: true});

    if (this.intervalId !== null) {
      if (response.isSuccess) {
        this.setProcessInstance(response.data);
      } else if (response.statusCode === 403) {
        this.handleFetchFailure(403);
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

  startPolling = async (
    instanceId: ProcessInstanceEntity['id'],
    options: {runImmediately?: boolean} = {runImmediately: false},
  ) => {
    if (
      document.visibilityState === 'hidden' ||
      (!this.isRunning && !this.state.processInstance?.hasActiveOperation)
    ) {
      return;
    }

    if (options.runImmediately) {
      this.handlePolling(instanceId);
    }

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
