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

import useOperationApply from '.';
import {renderHook} from '@testing-library/react-hooks';
import {waitFor} from 'modules/testing-library';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {operationsStore} from 'modules/stores/operations';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processesStore} from 'modules/stores/processes/processes.list';
import {mockData} from './index.setup';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessInstances,
  createBatchOperation,
} from 'modules/testUtils';
import {getSearchString} from 'modules/utils/getSearchString';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockApplyBatchOperation} from 'modules/mocks/api/processInstances/operations';
import * as operationsApi from 'modules/api/processInstances/operations';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';

jest.mock('modules/utils/getSearchString');

const applyBatchOperationSpy = jest.spyOn(operationsApi, 'applyBatchOperation');

const mockedGetSearchString = getSearchString as jest.MockedFunction<
  typeof getSearchString
>;

function renderUseOperationApply() {
  const {result} = renderHook(() => useOperationApply());

  result.current.applyBatchOperation({
    operationType: 'RESOLVE_INCIDENT',
    onSuccess: jest.fn(),
  });
}

describe('useOperationApply', () => {
  beforeEach(async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess('');
    mockApplyBatchOperation().withSuccess(createBatchOperation());
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
    processInstancesStore.reset();
    operationsStore.reset();
  });

  it('should call apply (no filter, select all ids)', async () => {
    const {mockOperationCreated, expectedBody} = mockData.noFilterSelectAll;
    mockApplyBatchOperation().withSuccess(mockOperationCreated);

    mockedGetSearchString.mockImplementation(
      () => '?active=true&running=true&incidents=true',
    );

    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated]),
    );

    expect(applyBatchOperationSpy).toHaveBeenCalledWith({
      operationType: expectedBody.operationType,
      query: expectedBody.query,
    });
  });

  it('should call apply (set id filter, select all ids)', async () => {
    const {mockOperationCreated, expectedBody} = mockData.setFilterSelectAll;
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    mockApplyBatchOperation().withSuccess(mockOperationCreated);

    mockedGetSearchString.mockImplementation(
      () => '?active=true&running=true&incidents=true&ids=1',
    );

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched'),
    );

    processInstancesSelectionStore.setAllChecked(true);

    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated]),
    );
    expect(applyBatchOperationSpy).toHaveBeenCalledWith({
      operationType: expectedBody.operationType,
      query: expectedBody.query,
    });
  });

  it('should call apply (set id filter, select one id)', async () => {
    const {mockOperationCreated, expectedBody} = mockData.setFilterSelectOne;
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();
    mockedGetSearchString.mockImplementation(
      () => '?active=true&running=true&incidents=true&ids=2251799813685594',
    );

    mockApplyBatchOperation().withSuccess(mockOperationCreated);

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched'),
    );

    processInstancesSelectionStore.selectProcessInstance('2251799813685594');

    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated]),
    );
    expect(applyBatchOperationSpy).toHaveBeenCalledWith({
      operationType: expectedBody.operationType,
      query: expectedBody.query,
      migrationPlan: undefined,
      modifications: undefined,
    });
  });

  it('should call apply (set id filter, exclude one id)', async () => {
    const {mockOperationCreated, expectedBody, ...context} =
      mockData.setFilterExcludeOne;
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();
    mockedGetSearchString.mockImplementation(
      () => '?active=true&running=true&incidents=true&ids=1,2',
    );

    mockApplyBatchOperation().withSuccess(mockOperationCreated);

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched'),
    );

    processInstancesSelectionStore.setMode('EXCLUDE');
    processInstancesSelectionStore.selectProcessInstance('1');

    expect(operationsStore.state.operations).toEqual([]);
    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated]),
    );
    expect(applyBatchOperationSpy).toHaveBeenCalledWith({
      operationType: expectedBody.operationType,
      query: expectedBody.query,
    });
  });

  it('should call apply (set process filter, select one)', async () => {
    const {mockOperationCreated, expectedBody, ...context} =
      mockData.setProcessFilterSelectOne;
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();
    mockedGetSearchString.mockImplementation(
      () =>
        '?active=true&running=true&incidents=true&process=demoProcess&version=1&ids=2251799813685594',
    );
    await processesStore.fetchProcesses();

    mockApplyBatchOperation().withSuccess(mockOperationCreated);

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched'),
    );

    processInstancesSelectionStore.selectProcessInstance('2251799813685594');

    expect(operationsStore.state.operations).toEqual([]);
    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated]),
    );
    expect(applyBatchOperationSpy).toHaveBeenCalledWith({
      operationType: expectedBody.operationType,
      query: expectedBody.query,
      migrationPlan: undefined,
      modifications: undefined,
    });
  });

  it('should poll all visible instances', async () => {
    const {expectedBody, ...context} = mockData.setFilterSelectAll;
    processInstancesSelectionStore.setMode('ALL');

    jest.useFakeTimers();
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.processInstances).toHaveLength(3),
    );

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    expect(
      processInstancesStore.processInstanceIdsWithActiveOperations,
    ).toEqual(['2251799813685594', '2251799813685596', '2251799813685598']);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...mockProcessInstances,
            totalCount: 100,
          }),
        ),
      ),
      // mock for refreshing instances when an instance operation is complete
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...mockProcessInstances,
            totalCount: 200,
          }),
        ),
      ),
      // mock for refresh running process instances count
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...mockProcessInstances,
            totalCount: 200,
          }),
        ),
      ),
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(
        processInstancesStore.processInstanceIdsWithActiveOperations,
      ).toEqual([]);
      expect(processInstancesStore.state.filteredProcessInstancesCount).toBe(
        200,
      ); // TODO: this second validation can be removed after  https://jira.camunda.com/browse/OPE-1169
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should poll the selected instances', async () => {
    const {...context} = mockData.setProcessFilterSelectOne;
    processInstancesSelectionStore.selectProcessInstance('2251799813685594');

    jest.useFakeTimers();
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();
    await waitFor(() =>
      expect(processInstancesStore.state.processInstances).toHaveLength(3),
    );

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    expect(
      processInstancesStore.processInstanceIdsWithActiveOperations,
    ).toEqual(['2251799813685594']);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...mockProcessInstances,
            totalCount: 100,
          }),
        ),
      ),
      // mock for refreshing instances when an instance operation is complete
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...mockProcessInstances,
            totalCount: 200,
          }),
        ),
      ),
      // mock for refresh running process instances count
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...mockProcessInstances,
            totalCount: 200,
          }),
        ),
      ),
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(
        processInstancesStore.processInstanceIdsWithActiveOperations,
      ).toEqual([]);
      expect(processInstancesStore.state.filteredProcessInstancesCount).toBe(
        200,
      ); // TODO: this second validation can be removed after  https://jira.camunda.com/browse/OPE-1169
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
