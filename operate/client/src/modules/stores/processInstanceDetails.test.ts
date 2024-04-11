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

import {processInstanceDetailsStore} from './processInstanceDetails';
import {createInstance} from 'modules/testUtils';
import {waitFor} from 'modules/testing-library';
import {createOperation} from 'modules/utils/instance';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {IS_INSTANCE_LIST_OPERATION_ERROR_ENABLED} from 'modules/feature-flags';

const currentInstanceMock = createInstance();

describe('stores/currentInstance', () => {
  afterEach(() => {
    processInstanceDetailsStore.reset();
  });

  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(currentInstanceMock);
  });

  it('should fetch current instance on init state', async () => {
    processInstanceDetailsStore.init({id: '1'});
    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        currentInstanceMock,
      ),
    );
  });

  it('should poll if current instance is running', async () => {
    jest.useFakeTimers();
    processInstanceDetailsStore.init({id: '1'});
    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        currentInstanceMock,
      ),
    );

    const secondCurrentInstanceMock = createInstance();

    mockFetchProcessInstance().withSuccess(secondCurrentInstanceMock);

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        secondCurrentInstanceMock,
      ),
    );

    const thirdCurrentInstanceMock = createInstance();

    mockFetchProcessInstance().withSuccess(thirdCurrentInstanceMock);

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        thirdCurrentInstanceMock,
      ),
    );

    const finishedCurrentInstanceMock = createInstance({state: 'CANCELED'});

    mockFetchProcessInstance().withSuccess(finishedCurrentInstanceMock);

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        finishedCurrentInstanceMock,
      ),
    );

    // do not poll since instance is not running anymore
    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        finishedCurrentInstanceMock,
      ),
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should set current instance', async () => {
    const mockInstance = createInstance({id: '123', state: 'ACTIVE'});
    expect(processInstanceDetailsStore.state.processInstance).toEqual(null);
    processInstanceDetailsStore.setProcessInstance(mockInstance);
    expect(processInstanceDetailsStore.state.processInstance).toEqual(
      mockInstance,
    );
  });

  it('should get process title', async () => {
    expect(processInstanceDetailsStore.processTitle).toBe(null);
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processName: 'processName',
      }),
    );
    expect(processInstanceDetailsStore.processTitle).toBe(
      'Operate: Process Instance 123 of processName',
    );
  });

  it('should reset store', async () => {
    const mockInstance = createInstance({
      id: '123',
      state: 'ACTIVE',
      processName: 'processName',
    });

    expect(processInstanceDetailsStore.processTitle).toBe(null);
    processInstanceDetailsStore.setProcessInstance(mockInstance);
    expect(processInstanceDetailsStore.state.processInstance).toEqual(
      mockInstance,
    );
    processInstanceDetailsStore.reset();
    expect(processInstanceDetailsStore.processTitle).toBe(null);
  });

  it('should set active operation state', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        hasActiveOperation: false,
        operations: [],
      }),
    );

    expect(
      processInstanceDetailsStore.state.processInstance?.hasActiveOperation,
    ).toBe(false);
    processInstanceDetailsStore.activateOperation('CANCEL_PROCESS_INSTANCE');

    expect(
      processInstanceDetailsStore.state.processInstance?.hasActiveOperation,
    ).toBe(true);
    expect(
      processInstanceDetailsStore.state.processInstance?.operations,
    ).toEqual([createOperation('CANCEL_PROCESS_INSTANCE')]);

    processInstanceDetailsStore.deactivateOperation('CANCEL_PROCESS_INSTANCE');

    expect(
      processInstanceDetailsStore.state.processInstance?.hasActiveOperation,
    ).toBe(false);
    expect(
      processInstanceDetailsStore.state.processInstance?.operations,
    ).toEqual([]);
  });

  (IS_INSTANCE_LIST_OPERATION_ERROR_ENABLED ? it.skip : it)(
    'should not set active operation state to false if there are still running operations',
    async () => {
      processInstanceDetailsStore.setProcessInstance(
        createInstance({
          id: '123',
          hasActiveOperation: false,
        }),
      );

      expect(
        processInstanceDetailsStore.state.processInstance?.hasActiveOperation,
      ).toBe(false);
      processInstanceDetailsStore.activateOperation('CANCEL_PROCESS_INSTANCE');

      expect(
        processInstanceDetailsStore.state.processInstance?.hasActiveOperation,
      ).toBe(true);
      expect(
        processInstanceDetailsStore.state.processInstance?.operations,
      ).toEqual([
        {
          batchOperationId: 'fe19ed17-a213-4b8d-ad10-2fb6d2bd89e5',
          errorMessage: 'string',
          id: 'id_25',
          state: 'SENT',
          type: 'RESOLVE_INCIDENT',
        },
        createOperation('CANCEL_PROCESS_INSTANCE'),
      ]);

      processInstanceDetailsStore.deactivateOperation(
        'CANCEL_PROCESS_INSTANCE',
      );

      expect(
        processInstanceDetailsStore.state.processInstance?.hasActiveOperation,
      ).toBe(true);
      expect(
        processInstanceDetailsStore.state.processInstance?.operations,
      ).toEqual([
        {
          batchOperationId: 'fe19ed17-a213-4b8d-ad10-2fb6d2bd89e5',
          errorMessage: 'string',
          id: 'id_25',
          state: 'SENT',
          type: 'RESOLVE_INCIDENT',
        },
      ]);
    },
  );

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    processInstanceDetailsStore.init({id: '1'});

    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        currentInstanceMock,
      ),
    );

    mockFetchProcessInstance().withSuccess({
      ...currentInstanceMock,
      state: 'INCIDENT',
    });

    eventListeners.online();

    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual({
        ...currentInstanceMock,
        state: 'INCIDENT',
      }),
    );

    window.addEventListener = originalEventListener;
  });

  it('should poll with polling header', async () => {
    jest.useFakeTimers();

    // expect the first request not to be a polling request
    mockFetchProcessInstance().withSuccess(currentInstanceMock, {
      expectPolling: false,
    });

    processInstanceDetailsStore.init({id: '1'});
    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        currentInstanceMock,
      ),
    );

    const secondCurrentInstanceMock = createInstance();

    // expect the second request to be a polling request
    mockFetchProcessInstance().withSuccess(secondCurrentInstanceMock, {
      expectPolling: true,
    });

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        secondCurrentInstanceMock,
      ),
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
