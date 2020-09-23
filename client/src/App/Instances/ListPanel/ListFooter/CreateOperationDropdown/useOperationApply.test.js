/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* eslint-disable react/prop-types */

import useOperationApply from './useOperationApply';
import {renderHook} from '@testing-library/react-hooks';
import {createMemoryHistory} from 'history';

import {instanceSelection} from 'modules/stores/instanceSelection';
import {filters} from 'modules/stores/filters';
import {INSTANCE_SELECTION_MODE} from 'modules/constants';
import {
  mockUseDataManager,
  mockData,
  mockUseInstancesPollContext,
} from './useOperationApply.setup';
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
jest.mock('modules/contexts/InstancesPollContext', () => ({
  useInstancesPollContext: () => mockUseInstancesPollContext,
}));

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
  });

  afterEach(() => {
    instanceSelection.reset();
    filters.reset();
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
    instanceSelection.selectInstance('1');
    renderUseOperationApply(context);

    expect(mockUseDataManager.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE,
      expectedQuery
    );
  });

  it('should poll all visible instances', () => {
    const {expectedQuery, ...context} = mockData.setFilterSelectAll;

    renderUseOperationApply(context);

    expect(mockUseInstancesPollContext.addAllVisibleIds).toHaveBeenCalled();
  });

  it('should poll the selected instances', () => {
    const {expectedQuery, ...context} = mockData.setWorkflowFilterSelectOne;
    instanceSelection.selectInstance('1');

    renderUseOperationApply(context);

    expect(mockUseInstancesPollContext.addIds).toHaveBeenCalled();
  });
});
