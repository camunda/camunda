/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import useOperationApply from './useOperationApply';
import {renderHook} from '@testing-library/react-hooks';
import {waitFor} from '@testing-library/react';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {operationsStore} from 'modules/stores/operations';
import {instancesStore} from 'modules/stores/instances';
import {processesStore} from 'modules/stores/processes';
import {INSTANCE_SELECTION_MODE} from 'modules/constants';
import {mockData} from './useOperationApply.setup';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessInstances,
} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {isEqual} from 'lodash';
import {getSearchString} from 'modules/utils/getSearchString';

jest.mock('modules/utils/getSearchString');

const mockedGetSearchString = getSearchString as jest.MockedFunction<
  typeof getSearchString
>;

function renderUseOperationApply() {
  const {result} = renderHook(() => useOperationApply());

  result.current.applyBatchOperation('RESOLVE_INCIDENT', jest.fn());
}

describe('useOperationApply', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/processes/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedProcessesMock))
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      ),
      rest.post('/api/process-instances/batch-operation', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );
  });

  afterEach(() => {
    instanceSelectionStore.reset();
    instancesStore.reset();
    operationsStore.reset();
  });

  it('should call apply (no filter, select all ids)', async () => {
    const {mockOperationCreated, expectedQuery} = mockData.noFilterSelectAll;

    mockServer.use(
      rest.post('/api/process-instances/batch-operation', (req, res, ctx) => {
        // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
        if (isEqual(req.body.query, expectedQuery)) {
          return res.once(ctx.json(mockOperationCreated));
        }
      })
    );

    mockedGetSearchString.mockImplementation(
      () => '?active=true&running=true&incidents=true'
    );

    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
  });

  it('should call apply (set id filter, select all ids)', async () => {
    const {mockOperationCreated, expectedQuery} = mockData.setFilterSelectAll;
    instancesStore.init();
    instancesStore.fetchInstancesFromFilters();

    mockedGetSearchString.mockImplementation(
      () => '?active=true&running=true&incidents=true&ids=1'
    );

    mockServer.use(
      rest.post('/api/process-instances/batch-operation', (req, res, ctx) => {
        // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
        if (isEqual(req.body.query, expectedQuery)) {
          return res.once(ctx.json(mockOperationCreated));
        }
      })
    );

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

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
    instancesStore.init();
    instancesStore.fetchInstancesFromFilters();
    mockedGetSearchString.mockImplementation(
      () => '?active=true&running=true&incidents=true&ids=1'
    );

    mockServer.use(
      rest.post('/api/process-instances/batch-operation', (req, res, ctx) => {
        // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
        if (isEqual(req.body.query, expectedQuery)) {
          return res.once(ctx.json(mockOperationCreated));
        }
      })
    );

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    instanceSelectionStore.selectInstance('1');

    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
  });

  it('should call apply (set id filter, exclude one id)', async () => {
    const {mockOperationCreated, expectedQuery, ...context} =
      mockData.setFilterExcludeOne;
    instancesStore.init();
    instancesStore.fetchInstancesFromFilters();
    mockedGetSearchString.mockImplementation(
      () => '?active=true&running=true&incidents=true&ids=1,2'
    );

    mockServer.use(
      rest.post('/api/process-instances/batch-operation', (req, res, ctx) => {
        // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
        if (isEqual(req.body.query, expectedQuery)) {
          return res.once(ctx.json(mockOperationCreated));
        }
      })
    );

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    instanceSelectionStore.setMode(INSTANCE_SELECTION_MODE.EXCLUDE);
    instanceSelectionStore.selectInstance('1');

    expect(operationsStore.state.operations).toEqual([]);
    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
  });

  it('should call apply (set process filter, select one)', async () => {
    const {mockOperationCreated, expectedQuery, ...context} =
      mockData.setProcessFilterSelectOne;
    instancesStore.init();
    instancesStore.fetchInstancesFromFilters();
    mockedGetSearchString.mockImplementation(
      () =>
        '?active=true&running=true&incidents=true&process=demoProcess&version=1&ids=1'
    );
    await processesStore.fetchProcesses();

    mockServer.use(
      rest.post('/api/process-instances/batch-operation', (req, res, ctx) => {
        // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
        if (isEqual(req.body.query, expectedQuery)) {
          return res.once(ctx.json(mockOperationCreated));
        }
      })
    );

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

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

    jest.useFakeTimers();
    instancesStore.init();
    instancesStore.fetchInstancesFromFilters();

    await waitFor(() =>
      expect(instancesStore.state.processInstances).toHaveLength(3)
    );

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            totalCount: 100,
            processInstances: mockProcessInstances.processInstances,
          })
        )
      ),
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            totalCount: 200,
            processInstances: mockProcessInstances.processInstances,
          })
        )
      )
    );

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '2251799813685594',
      '2251799813685596',
      '2251799813685598',
    ]);

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(instancesStore.instanceIdsWithActiveOperations).toEqual([]);
      expect(instancesStore.state.filteredInstancesCount).toBe(200); // TODO: this second validation can be removed after  https://jira.camunda.com/browse/OPE-1169
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should poll the selected instances', async () => {
    const {expectedQuery, ...context} = mockData.setProcessFilterSelectOne;
    instanceSelectionStore.selectInstance('2251799813685594');

    jest.useFakeTimers();
    instancesStore.init();
    instancesStore.fetchInstancesFromFilters();
    await waitFor(() =>
      expect(instancesStore.state.processInstances).toHaveLength(3)
    );

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 0 arguments, but got 1.
    renderUseOperationApply(context);

    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '2251799813685594',
    ]);
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            totalCount: 100,
            processInstances: mockProcessInstances.processInstances,
          })
        )
      ),
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            totalCount: 200,
            processInstances: mockProcessInstances.processInstances,
          })
        )
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(instancesStore.instanceIdsWithActiveOperations).toEqual([]);
      expect(instancesStore.state.filteredInstancesCount).toBe(200); // TODO: this second validation can be removed after  https://jira.camunda.com/browse/OPE-1169
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
