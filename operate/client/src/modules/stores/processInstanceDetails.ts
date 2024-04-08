/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import isEqual from 'lodash/isEqual';

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

  handlePolling = async (instanceId: any) => {
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
