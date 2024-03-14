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

import {statisticsStore} from './statistics';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {waitFor} from 'modules/testing-library';
import {processInstancesStore} from './processInstances';
import {
  mockProcessXML,
  groupedProcessesMock,
  createInstance,
} from 'modules/testUtils';
import {statistics} from 'modules/mocks/statistics';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessCoreStatistics} from 'modules/mocks/api/processInstances/fetchProcessCoreStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';
import {checkPollingHeader} from 'modules/mocks/api/mockRequest';

const mockInstance = createInstance({id: '2251799813685625'});

describe('stores/statistics', () => {
  beforeEach(() => {
    // mock for initial fetch when statistics store is initialized
    mockFetchProcessCoreStatistics().withSuccess(statistics, {
      expectPolling: false,
    });
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    statisticsStore.reset();
    processInstancesStore.reset();
  });

  it('should reset state', async () => {
    await statisticsStore.fetchStatistics();
    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    statisticsStore.reset();
    expect(statisticsStore.state.running).toBe(0);
    expect(statisticsStore.state.active).toBe(0);
    expect(statisticsStore.state.withIncidents).toBe(0);
    expect(statisticsStore.state.status).toBe('initial');
  });

  it('should fetch statistics with error', async () => {
    mockFetchProcessCoreStatistics().withServerError();

    expect(statisticsStore.state.status).toBe('initial');

    await statisticsStore.fetchStatistics();

    expect(statisticsStore.state.status).toBe('error');
    expect(statisticsStore.state.running).toBe(0);
    expect(statisticsStore.state.active).toBe(0);
    expect(statisticsStore.state.withIncidents).toBe(0);
  });

  it('should fetch statistics with success', async () => {
    expect(statisticsStore.state.status).toBe('initial');

    await statisticsStore.fetchStatistics();
    expect(statisticsStore.state.status).toBe('fetched');
    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);
  });

  it('should fetch statistics', async () => {
    expect(statisticsStore.state.status).toBe('initial');
    statisticsStore.fetchStatistics();

    expect(statisticsStore.state.status).toBe('first-fetch');
    await waitFor(() => {
      expect(statisticsStore.state.running).toBe(1087);
    });
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);
  });

  it('should start polling on init', async () => {
    mockFetchProcessCoreStatistics().withSuccess(statistics, {
      expectPolling: true,
    });
    jest.useFakeTimers();
    statisticsStore.init();
    jest.runOnlyPendingTimers();
    await waitFor(() => expect(statisticsStore.state.status).toBe('fetched'));

    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    mockFetchProcessCoreStatistics().withSuccess(
      {
        running: 1088,
        active: 211,
        withIncidents: 878,
      },
      {expectPolling: true},
    );
    jest.runOnlyPendingTimers();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1088));
    expect(statisticsStore.state.active).toBe(211);
    expect(statisticsStore.state.withIncidents).toBe(878);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should fetch statistics depending on completed operations', async () => {
    mockFetchProcessCoreStatistics().withSuccess(statistics, {
      expectPolling: true,
    });
    jest.useFakeTimers();

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    // mock for refresh all instances
    mockFetchProcessInstances().withSuccess({
      processInstances: [{...mockInstance, hasActiveOperation: true}],
      totalCount: 1,
    });

    // mock for refresh running process instances count
    mockFetchProcessInstances().withSuccess({
      processInstances: [{...mockInstance, hasActiveOperation: true}],
      totalCount: 1,
    });

    statisticsStore.init();

    jest.runOnlyPendingTimers();
    await waitFor(() => expect(statisticsStore.state.status).toBe('fetched'));

    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched'),
    );

    // mock for next poll

    mockServer.use(
      rest.post('/api/process-instances', (req, res, ctx) => {
        checkPollingHeader({req, expectPolling: true});
        return res.once(
          ctx.json({
            processInstances: [{...mockInstance}],
            totalCount: 1,
          }),
        );
      }),
      rest.post('/api/process-instances', (req, res, ctx) => {
        checkPollingHeader({req, expectPolling: true});
        return res.once(
          ctx.json({
            processInstances: [{...mockInstance}],
            totalCount: 2,
          }),
        );
      }),
      rest.get('/api/process-instances/core-statistics', (req, res, ctx) => {
        checkPollingHeader({req, expectPolling: true});
        return res.once(
          ctx.json({
            ...statistics,
          }),
        );
      }),
      rest.get('/api/process-instances/core-statistics', (req, res, ctx) => {
        checkPollingHeader({req, expectPolling: true});
        return res.once(
          ctx.json({
            ...statistics,
            running: 1088,
          }),
        );
      }),
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1088));
    await waitFor(() =>
      expect(processInstancesStore.state.filteredProcessInstancesCount).toBe(2),
    );
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    statisticsStore.fetchStatistics();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1087));

    mockFetchProcessCoreStatistics().withSuccess({
      ...statistics,
      running: 1000,
    });

    eventListeners.online();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1000));

    window.addEventListener = originalEventListener;
  });
});
