/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import useOperationApply from './useOperationApply';
import {renderHook} from '@testing-library/react-hooks';
import {waitFor} from '@testing-library/react';
import {createMemoryHistory} from 'history';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {operationsStore} from 'modules/stores/operations';
import {filtersStore} from 'modules/stores/filters';
import {instancesStore} from 'modules/stores/instances';
import {INSTANCE_SELECTION_MODE} from 'modules/constants';
import {mockData} from './useOperationApply.setup';
import {
  groupedWorkflowsMock,
  mockWorkflowStatistics,
  mockWorkflowInstances,
} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {isEqual} from 'lodash';

const OPERATION_TYPE = 'RESOLVE_INCIDENT';

function renderUseOperationApply() {
  const {result} = renderHook(() => useOperationApply());

  result.current.applyBatchOperation(OPERATION_TYPE, jest.fn());
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
      ),
      rest.post('/api/workflow-instances/batch-operation', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );

    filtersStore.setUrlParameters(historyMock, locationMock);
    await filtersStore.init();
    instancesStore.init();
  });

  afterEach(() => {
    instanceSelectionStore.reset();
    filtersStore.reset();
    instancesStore.reset();
    operationsStore.reset();
  });

  it('should call apply (no filter, select all ids)', async () => {
    const {mockOperationCreated, expectedQuery} = mockData.noFilterSelectAll;

    mockServer.use(
      rest.post('/api/workflow-instances/batch-operation', (req, res, ctx) => {
        // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
        if (isEqual(req.body.query, expectedQuery)) {
          return res.once(ctx.json(mockOperationCreated));
        }
      })
    );

    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
  });

  it('should call apply (set id filter, select all ids)', async () => {
    const {mockOperationCreated, expectedQuery} = mockData.setFilterSelectAll;

    mockServer.use(
      rest.post('/api/workflow-instances/batch-operation', (req, res, ctx) => {
        // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
        if (isEqual(req.body.query, expectedQuery)) {
          return res.once(ctx.json(mockOperationCreated));
        }
      })
    );

    filtersStore.setFilter({
      // @ts-expect-error
      ...filtersStore.state.filter,
      ids: '1',
    });

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      )
    );

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
    instanceSelectionStore.setAllChecked();

    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
  });

  it('should call apply (set id filter, select one id)', async () => {
    const {mockOperationCreated, expectedQuery} = mockData.setFilterSelectOne;

    mockServer.use(
      rest.post('/api/workflow-instances/batch-operation', (req, res, ctx) => {
        // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
        if (isEqual(req.body.query, expectedQuery)) {
          return res.once(ctx.json(mockOperationCreated));
        }
      })
    );

    filtersStore.setFilter({
      // @ts-expect-error
      ...filtersStore.state.filter,
      ids: '1, 2',
    });
    instanceSelectionStore.selectInstance('1');

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      )
    );
    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
  });

  it('should call apply (set id filter, exclude one id)', async () => {
    const {
      mockOperationCreated,
      expectedQuery,
      ...context
    } = mockData.setFilterExcludeOne;

    mockServer.use(
      rest.post('/api/workflow-instances/batch-operation', (req, res, ctx) => {
        // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
        if (isEqual(req.body.query, expectedQuery)) {
          return res.once(ctx.json(mockOperationCreated));
        }
      })
    );

    filtersStore.setFilter({
      // @ts-expect-error
      ...filtersStore.state.filter,
      ids: '1, 2',
    });
    instanceSelectionStore.setMode(INSTANCE_SELECTION_MODE.EXCLUDE);
    instanceSelectionStore.selectInstance('1');

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      )
    );

    expect(operationsStore.state.operations).toEqual([]);
    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
  });

  it('should call apply (set workflow filter, select one)', async () => {
    const {
      mockOperationCreated,
      expectedQuery,
      ...context
    } = mockData.setWorkflowFilterSelectOne;

    mockServer.use(
      rest.post('/api/workflow-instances/batch-operation', (req, res, ctx) => {
        // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
        if (isEqual(req.body.query, expectedQuery)) {
          return res.once(ctx.json(mockOperationCreated));
        }
      })
    );

    filtersStore.setFilter({
      // @ts-expect-error
      ...filtersStore.state.filter,
      workflow: 'demoProcess',
      version: '1',
    });

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      )
    );

    instanceSelectionStore.selectInstance('1');

    expect(operationsStore.state.operations).toEqual([]);
    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
  });

  it('should poll all visible instances', async () => {
    const {expectedQuery, ...context} = mockData.setFilterSelectAll;
    instanceSelectionStore.setMode(INSTANCE_SELECTION_MODE.ALL);

    await waitFor(() =>
      expect(instancesStore.state.workflowInstances.length).toBe(2)
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
    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    expect(instancesStore.state.instancesWithActiveOperations).toEqual([
      '2251799813685594',
      '2251799813685596',
    ]);

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(instancesStore.state.instancesWithActiveOperations).toEqual([])
    );
    jest.useRealTimers();
  });

  it('should poll the selected instances', async () => {
    const {expectedQuery, ...context} = mockData.setWorkflowFilterSelectOne;
    instanceSelectionStore.selectInstance('2251799813685594');

    await waitFor(() =>
      expect(instancesStore.state.workflowInstances.length).toBe(2)
    );

    jest.useFakeTimers();
    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    expect(instancesStore.state.instancesWithActiveOperations).toEqual([
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
    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(instancesStore.state.instancesWithActiveOperations).toEqual([])
    );
    jest.useRealTimers();
  });
});
