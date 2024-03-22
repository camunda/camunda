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
import {fetchBatchOperations} from 'modules/api/fetchBatchOperations';
import {BatchOperationDto} from 'modules/api/sharedTypes';
import {
  BatchOperationQuery,
  MigrationPlan,
  Modifications,
  applyBatchOperation,
  applyOperation,
} from 'modules/api/processInstances/operations';
import {sortOperations} from '../utils/sortOperations';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from '../networkReconnectionHandler';
import {deleteDecisionDefinition} from 'modules/api/decisions/operations';
import {deleteProcessDefinition} from 'modules/api/processes/operations';

type Query = BatchOperationQuery;
type OperationPayload = Parameters<typeof applyOperation>['1'];
type ErrorHandler = ({
  operationType,
  statusCode,
}: {
  operationType: OperationEntityType;
  statusCode?: number;
}) => void;

type State = {
  operations: OperationEntity[];
  hasMoreOperations: boolean;
  page: number;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  operations: [],
  hasMoreOperations: true,
  page: 1,
  status: 'initial',
};

const MAX_OPERATIONS_PER_REQUEST = 20;

class Operations extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  isPollRequestRunning: boolean = false;
  intervalId: null | ReturnType<typeof setInterval> = null;
  disposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      setOperations: action,
      increasePage: action,
      prependOperations: action,
      setHasMoreOperations: action,
      hasRunningOperations: computed,
      startFetching: action,
      handleFetchSuccess: action,
      handleFetchError: action,
    });
  }

  init() {
    this.fetchOperations();

    this.disposer = autorun(() => {
      if (this.hasRunningOperations) {
        if (this.intervalId === null) {
          this.startPolling();
        }
      } else {
        this.stopPolling();
      }
    });
  }

  fetchOperations = this.retryOnConnectionLost(
    async (searchAfter?: OperationEntity['sortValues']) => {
      this.startFetching();

      const response = await fetchBatchOperations({
        pageSize: MAX_OPERATIONS_PER_REQUEST,
        searchAfter,
      });

      if (response.isSuccess) {
        const operations = response.data;
        this.setOperations(operations);
        this.setHasMoreOperations(operations.length);
        this.handleFetchSuccess();
      } else {
        this.handleFetchError();
      }
    },
  );

  fetchNextOperations = async () => {
    this.increasePage();
    this.fetchOperations(
      this.state.operations[this.state.operations.length - 1]!.sortValues,
    );
  };

  applyBatchOperation = async ({
    operationType,
    query,
    migrationPlan,
    modifications,
    onSuccess,
    onError,
  }: {
    operationType: OperationEntityType;
    query: Query;
    migrationPlan?: MigrationPlan;
    modifications?: Modifications;
    onSuccess: () => void;
    onError: ErrorHandler;
  }) => {
    try {
      const response = await applyBatchOperation({
        operationType,
        query,
        migrationPlan,
        modifications,
      });

      if (response.isSuccess) {
        this.prependOperations(response.data);
        onSuccess();
      } else {
        onError({operationType, statusCode: response.statusCode});
      }
    } catch {
      onError({operationType});
    }
  };

  applyOperation = async ({
    instanceId,
    payload,
    onError,
    onSuccess,
  }: {
    instanceId: string;
    payload: OperationPayload;
    onError?: ErrorHandler;
    onSuccess?: (operationType: OperationEntityType) => void;
  }) => {
    const response = await applyOperation(instanceId, payload);

    if (response.isSuccess) {
      this.prependOperations(response.data);
      onSuccess?.(payload.operationType);
    } else {
      onError?.({
        operationType: payload.operationType,
        statusCode: response.statusCode,
      });
    }
  };

  applyDeleteDecisionDefinitionOperation = async ({
    decisionDefinitionId,
    onError,
    onSuccess,
  }: {
    decisionDefinitionId: string;
    onError?: (statusCode: number) => void;
    onSuccess?: () => void;
  }) => {
    const response = await deleteDecisionDefinition(decisionDefinitionId);

    if (response.isSuccess) {
      this.prependOperations(response.data);
      onSuccess?.();
    } else {
      onError?.(response.statusCode);
    }
  };

  applyDeleteProcessDefinitionOperation = async ({
    processDefinitionId,
    onError,
    onSuccess,
  }: {
    processDefinitionId: string;
    onError?: (statusCode: number) => void;
    onSuccess?: () => void;
  }) => {
    const response = await deleteProcessDefinition(processDefinitionId);

    if (response.isSuccess) {
      this.prependOperations(response.data);
      onSuccess?.();
    } else {
      onError?.(response.statusCode);
    }
  };

  handlePolling = async () => {
    this.isPollRequestRunning = true;
    const response = await fetchBatchOperations({
      pageSize: MAX_OPERATIONS_PER_REQUEST * this.state.page,
    });

    if (this.intervalId !== null && response.isSuccess) {
      this.setOperations(response.data);
    }

    if (!response.isSuccess) {
      logger.error('Failed to poll operations');
    }

    this.isPollRequestRunning = false;
  };

  startFetching = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  handleFetchSuccess = () => {
    this.state.status = 'fetched';
  };

  handleFetchError = () => {
    this.state.status = 'error';
  };

  startPolling = async () => {
    this.intervalId = setInterval(() => {
      if (!this.isPollRequestRunning) {
        this.handlePolling();
      }
    }, 1000);
  };

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  prependOperations = (response: BatchOperationDto) => {
    this.state.operations.unshift({...response, sortValues: undefined});
  };

  setOperations(response: any) {
    const operations = [...this.state.operations, ...response].reduce(
      (accumulator, operation) => {
        accumulator[operation.id] = operation;
        return accumulator;
      },
      {},
    );

    this.state.operations = sortOperations(Object.values(operations));
  }

  setHasMoreOperations(operationCount: number) {
    this.state.hasMoreOperations =
      operationCount === MAX_OPERATIONS_PER_REQUEST;
  }

  increasePage() {
    this.state.page++;
  }

  get hasRunningOperations() {
    return this.state.operations.some(
      (operation) => operation.endDate === null,
    );
  }

  reset() {
    super.reset();
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
  }
}

export const operationsStore = new Operations();
export type {ErrorHandler};
