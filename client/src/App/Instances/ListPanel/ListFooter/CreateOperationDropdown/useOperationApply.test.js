/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* eslint-disable react/prop-types */

import useOperationApply from './useOperationApply';
import {renderHook} from '@testing-library/react-hooks';
import {waitFor} from '@testing-library/react';
import {createMemoryHistory} from 'history';

import {instanceSelection} from 'modules/stores/instanceSelection';
import {filters} from 'modules/stores/filters';
import {instances} from 'modules/stores/instances';
import {INSTANCE_SELECTION_MODE} from 'modules/constants';
import {mockUseDataManager, mockData} from './useOperationApply.setup';
import {
  groupedWorkflowsMock,
  mockWorkflowStatistics,
  mockWorkflowInstances,
} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

const OPERATION_TYPE = 'DUMMY';

jest.mock('modules/utils/bpmn');
jest.mock('modules/hooks/useDataManager', () => () => mockUseDataManager);

function renderUseOperationApply() {
  const {result} = renderHook(() => useOperationApply());

  result.current.applyBatchOperation(OPERATION_TYPE);
}

describe('useOperationApply', () => {
  const locationMock = {pathname: '/instances'};
  const historyMock = createMemoryHistory();

  beforeEach(async () => {
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );

    filters.setUrlParameters(historyMock, locationMock);
    await filters.init();
    instances.init();
  });

  afterEach(() => {
    instanceSelection.reset();
    filters.reset();
    instances.reset();
  });

  it('should call apply (no filter, select all ids)', () => {
    const {expectedQuery} = mockData.noFilterSelectAll;

    renderUseOperationApply();

    expect(mockUseDataManager.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE,
      expectedQuery
    );
  });

  it('should call apply (set id filter, select all ids)', () => {
    const {expectedQuery} = mockData.setFilterSelectAll;
    filters.setFilter({
      ...filters.state.filter,
      ids: '1',
    });

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      )
    );

    instanceSelection.setAllChecked();
    renderUseOperationApply();

    expect(mockUseDataManager.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE,
      expectedQuery
    );
  });

  it('should call apply (set id filter, select one id)', () => {
    const {expectedQuery} = mockData.setFilterSelectOne;
    filters.setFilter({
      ...filters.state.filter,
      ids: '1, 2',
    });
    instanceSelection.selectInstance('1');

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      )
    );
    renderUseOperationApply();

    expect(mockUseDataManager.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE,
      expectedQuery
    );
  });

  it('should call apply (set id filter, exclude one id)', () => {
    const {expectedQuery, ...context} = mockData.setFilterExcludeOne;
    filters.setFilter({
      ...filters.state.filter,
      ids: '1, 2',
    });
    instanceSelection.setMode(INSTANCE_SELECTION_MODE.EXCLUDE);
    instanceSelection.selectInstance('1');

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      )
    );

    renderUseOperationApply(context);

    expect(mockUseDataManager.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE,
      expectedQuery
    );
  });

  it('should call apply (set workflow filter, select one)', () => {
    const {expectedQuery, ...context} = mockData.setWorkflowFilterSelectOne;
    filters.setFilter({
      ...filters.state.filter,
      workflow: 'demoProcess',
      version: '1',
    });

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      )
    );

    instanceSelection.selectInstance('1');
    renderUseOperationApply(context);

    expect(mockUseDataManager.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE,
      expectedQuery
    );
  });

  it('should poll all visible instances', async () => {
    const {expectedQuery, ...context} = mockData.setFilterSelectAll;
    instanceSelection.setMode(INSTANCE_SELECTION_MODE.ALL);

    await waitFor(() =>
      expect(instances.state.workflowInstances.length).toBe(2)
    );

    jest.useFakeTimers();
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=2',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=50',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      )
    );
    renderUseOperationApply(context);

    expect(instances.state.instancesWithActiveOperations).toEqual([
      '2251799813685594',
      '2251799813685596',
    ]);

    jest.advanceTimersByTime(5000);

    await waitFor(() =>
      expect(instances.state.instancesWithActiveOperations).toEqual([])
    );
    jest.useRealTimers();
  });

  it('should poll the selected instances', async () => {
    const {expectedQuery, ...context} = mockData.setWorkflowFilterSelectOne;
    instanceSelection.selectInstance('2251799813685594');

    await waitFor(() =>
      expect(instances.state.workflowInstances.length).toBe(2)
    );

    jest.useFakeTimers();
    renderUseOperationApply(context);

    expect(instances.state.instancesWithActiveOperations).toEqual([
      '2251799813685594',
    ]);
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=0&maxResults=1',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.post(
        '/api/workflow-instances?firstResult=0&maxResults=50',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      )
    );
    jest.advanceTimersByTime(5000);

    await waitFor(() =>
      expect(instances.state.instancesWithActiveOperations).toEqual([])
    );
    jest.useRealTimers();
  });
});
