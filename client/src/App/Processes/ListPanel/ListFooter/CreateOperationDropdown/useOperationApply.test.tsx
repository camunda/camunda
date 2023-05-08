/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import useOperationApply from './useOperationApply';
import {renderHook} from '@testing-library/react-hooks';
import {waitFor} from 'modules/testing-library';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {operationsStore} from 'modules/stores/operations';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processesStore} from 'modules/stores/processes';
import {mockData} from './useOperationApply.setup';
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

jest.mock('modules/utils/getSearchString');

const applyBatchOperationSpy = jest.spyOn(operationsApi, 'applyBatchOperation');

const mockedGetSearchString = getSearchString as jest.MockedFunction<
  typeof getSearchString
>;

function renderUseOperationApply() {
  const {result} = renderHook(() => useOperationApply());

  result.current.applyBatchOperation('RESOLVE_INCIDENT', jest.fn());
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
      () => '?active=true&running=true&incidents=true'
    );

    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );

    expect(applyBatchOperationSpy).toHaveBeenCalledWith(
      expectedBody.operationType,
      expectedBody.query
    );
  });

  it('should call apply (set id filter, select all ids)', async () => {
    const {mockOperationCreated, expectedBody} = mockData.setFilterSelectAll;
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    mockApplyBatchOperation().withSuccess(mockOperationCreated);

    mockedGetSearchString.mockImplementation(
      () => '?active=true&running=true&incidents=true&ids=1'
    );

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    processInstancesSelectionStore.setAllChecked(true);

    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
    expect(applyBatchOperationSpy).toHaveBeenCalledWith(
      expectedBody.operationType,
      expectedBody.query
    );
  });

  it('should call apply (set id filter, select one id)', async () => {
    const {mockOperationCreated, expectedBody} = mockData.setFilterSelectOne;
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();
    mockedGetSearchString.mockImplementation(
      () => '?active=true&running=true&incidents=true&ids=1'
    );

    mockApplyBatchOperation().withSuccess(mockOperationCreated);

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    processInstancesSelectionStore.selectProcessInstance('1');

    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
    expect(applyBatchOperationSpy).toHaveBeenCalledWith(
      expectedBody.operationType,
      expectedBody.query
    );
  });

  it('should call apply (set id filter, exclude one id)', async () => {
    const {mockOperationCreated, expectedBody, ...context} =
      mockData.setFilterExcludeOne;
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();
    mockedGetSearchString.mockImplementation(
      () => '?active=true&running=true&incidents=true&ids=1,2'
    );

    mockApplyBatchOperation().withSuccess(mockOperationCreated);

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    processInstancesSelectionStore.setMode('EXCLUDE');
    processInstancesSelectionStore.selectProcessInstance('1');

    expect(operationsStore.state.operations).toEqual([]);
    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
    expect(applyBatchOperationSpy).toHaveBeenCalledWith(
      expectedBody.operationType,
      expectedBody.query
    );
  });

  it('should call apply (set process filter, select one)', async () => {
    const {mockOperationCreated, expectedBody, ...context} =
      mockData.setProcessFilterSelectOne;
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();
    mockedGetSearchString.mockImplementation(
      () =>
        '?active=true&running=true&incidents=true&process=demoProcess&version=1&ids=1'
    );
    await processesStore.fetchProcesses();

    mockApplyBatchOperation().withSuccess(mockOperationCreated);

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    processInstancesSelectionStore.selectProcessInstance('1');

    expect(operationsStore.state.operations).toEqual([]);
    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
    expect(applyBatchOperationSpy).toHaveBeenCalledWith(
      expectedBody.operationType,
      expectedBody.query
    );
  });

  it('should poll all visible instances', async () => {
    const {expectedBody, ...context} = mockData.setFilterSelectAll;
    processInstancesSelectionStore.setMode('ALL');

    jest.useFakeTimers();
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.processInstances).toHaveLength(3)
    );

    mockFetchProcessInstances().withSuccess({
      ...mockProcessInstances,
      totalCount: 100,
    });

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    expect(
      processInstancesStore.processInstanceIdsWithActiveOperations
    ).toEqual(['2251799813685594', '2251799813685596', '2251799813685598']);

    jest.runOnlyPendingTimers();

    mockFetchProcessInstances().withSuccess({
      ...mockProcessInstances,
      totalCount: 200,
    });

    await waitFor(() => {
      expect(
        processInstancesStore.processInstanceIdsWithActiveOperations
      ).toEqual([]);
      expect(processInstancesStore.state.filteredProcessInstancesCount).toBe(
        200
      ); // TODO: this second validation can be removed after  https://jira.camunda.com/browse/OPE-1169
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should poll the selected instances', async () => {
    const {expectedBody, ...context} = mockData.setProcessFilterSelectOne;
    processInstancesSelectionStore.selectProcessInstance('2251799813685594');

    jest.useFakeTimers();
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();
    await waitFor(() =>
      expect(processInstancesStore.state.processInstances).toHaveLength(3)
    );

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    expect(
      processInstancesStore.processInstanceIdsWithActiveOperations
    ).toEqual(['2251799813685594']);

    mockFetchProcessInstances().withSuccess({
      ...mockProcessInstances,
      totalCount: 100,
    });

    jest.runOnlyPendingTimers();

    mockFetchProcessInstances().withSuccess({
      ...mockProcessInstances,
      totalCount: 200,
    });

    await waitFor(() => {
      expect(
        processInstancesStore.processInstanceIdsWithActiveOperations
      ).toEqual([]);
      expect(processInstancesStore.state.filteredProcessInstancesCount).toBe(
        200
      ); // TODO: this second validation can be removed after  https://jira.camunda.com/browse/OPE-1169
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
