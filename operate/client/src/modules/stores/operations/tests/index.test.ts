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

import {operationsStore} from '../';
import {waitFor} from 'modules/testing-library';
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';
import {operations} from 'modules/testUtils';

describe('stores/operations', () => {
  afterEach(() => {
    operationsStore.reset();
  });

  it('should reset state', async () => {
    mockFetchBatchOperations().withSuccess(operations);

    await operationsStore.fetchOperations();
    expect(operationsStore.state.operations).toEqual(operations);
    expect(operationsStore.state.status).toBe('fetched');
    operationsStore.reset();
    expect(operationsStore.state.operations).toEqual([]);
    expect(operationsStore.state.status).toEqual('initial');
  });

  it('should set hasMoreOperations', async () => {
    expect(operationsStore.state.hasMoreOperations).toBe(true);
    operationsStore.setHasMoreOperations(10);
    expect(operationsStore.state.hasMoreOperations).toBe(false);
    operationsStore.setHasMoreOperations(20);
    expect(operationsStore.state.hasMoreOperations).toBe(true);
  });

  it('should increase page if next operations are requested', async () => {
    mockFetchBatchOperations().withSuccess(operations);

    await operationsStore.fetchOperations();
    expect(operationsStore.state.page).toBe(1);

    mockFetchBatchOperations().withSuccess(operations);

    await operationsStore.fetchNextOperations();
    expect(operationsStore.state.page).toBe(2);

    await waitFor(() => expect(operationsStore.state.status).toBe('fetched'));
  });

  it('should increase page', () => {
    expect(operationsStore.state.page).toBe(1);

    operationsStore.increasePage();
    expect(operationsStore.state.page).toBe(2);

    operationsStore.increasePage();
    expect(operationsStore.state.page).toBe(3);
  });

  it('should get hasRunningOperations', async () => {
    mockFetchBatchOperations().withSuccess(operations);

    await operationsStore.fetchOperations();
    expect(operationsStore.hasRunningOperations).toBe(false);

    mockFetchBatchOperations().withSuccess([
      {
        id: '6255ced4-f570-46ce-b5c0-4b88a785fb9a',
        name: null,
        type: 'RESOLVE_INCIDENT',
        startDate: '2020-09-30T06:14:55.185+0000',
        endDate: null,
        instancesCount: 2,
        operationsTotalCount: 0,
        operationsFinishedCount: 0,
        sortValues: ['1601446495209', '1601446495185'],
      },
      ...operations,
    ]);

    await operationsStore.fetchOperations();
    expect(operationsStore.hasRunningOperations).toBe(true);
  });

  it('should poll when there are running operations', async () => {
    mockFetchBatchOperations().withSuccess(operations);

    operationsStore.init();
    jest.useFakeTimers();
    await waitFor(() => expect(operationsStore.state.status).toBe('fetched'));

    // no polling occurs in the next 2 polling
    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

    const runningOperation: OperationEntity = {
      id: '6255ced4-f570-46ce-b5c0-4b88a785fb9a',
      name: null,
      type: 'RESOLVE_INCIDENT',
      startDate: '2020-09-30T06:14:55.185+0000',
      endDate: null,
      instancesCount: 2,
      operationsTotalCount: 0,
      operationsFinishedCount: 0,
      sortValues: ['1601446495209', '1601446495185'],
    };

    const operationsWithRunningOperation: OperationEntity[] = [
      runningOperation,
      ...operations,
    ];
    mockFetchBatchOperations().withSuccess(operationsWithRunningOperation);

    operationsStore.fetchOperations();
    await waitFor(() =>
      expect(operationsStore.hasRunningOperations).toBe(true),
    );

    mockFetchBatchOperations().withSuccess([
      {...runningOperation, endDate: '2020-09-2930T15:38:34.372+0000'},
      ...operations,
    ]);

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(operationsStore.hasRunningOperations).toBe(false),
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    mockFetchBatchOperations().withSuccess(operations);

    operationsStore.init();

    await waitFor(() =>
      expect(operationsStore.state.status).toEqual('fetched'),
    );

    mockFetchBatchOperations().withSuccess(operations);

    eventListeners.online();

    expect(operationsStore.state.status).toEqual('fetching');

    await waitFor(() =>
      expect(operationsStore.state.status).toEqual('fetched'),
    );

    window.addEventListener = originalEventListener;
  });
});
