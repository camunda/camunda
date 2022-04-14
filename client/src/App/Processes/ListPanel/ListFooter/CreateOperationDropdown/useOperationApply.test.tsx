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
    processInstancesSelectionStore.reset();
    processInstancesStore.reset();
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
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

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

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
    processInstancesSelectionStore.setAllChecked();

    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
  });

  it('should call apply (set id filter, select one id)', async () => {
    const {mockOperationCreated, expectedQuery} = mockData.setFilterSelectOne;
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();
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

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    processInstancesSelectionStore.selectProcessInstance('1');

    expect(operationsStore.state.operations).toEqual([]);
    renderUseOperationApply();

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperationCreated])
    );
  });

  it('should call apply (set id filter, exclude one id)', async () => {
    const {mockOperationCreated, expectedQuery, ...context} =
      mockData.setFilterExcludeOne;
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();
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

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    processInstancesSelectionStore.setMode(INSTANCE_SELECTION_MODE.EXCLUDE);
    processInstancesSelectionStore.selectProcessInstance('1');

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
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();
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

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

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
  });

  it('should poll all visible instances', async () => {
    const {expectedQuery, ...context} = mockData.setFilterSelectAll;
    processInstancesSelectionStore.setMode(INSTANCE_SELECTION_MODE.ALL);

    jest.useFakeTimers();
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.processInstances).toHaveLength(3)
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

    expect(
      processInstancesStore.processInstanceIdsWithActiveOperations
    ).toEqual(['2251799813685594', '2251799813685596', '2251799813685598']);

    jest.runOnlyPendingTimers();

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
    const {expectedQuery, ...context} = mockData.setProcessFilterSelectOne;
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
