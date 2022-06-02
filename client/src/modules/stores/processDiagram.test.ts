/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from '@testing-library/react';
import {mockServer} from 'modules/mock-server/node';
import {mockProcessInstances} from 'modules/mocks/mockProcessInstances';
import {mockProcessStatistics} from 'modules/mocks/mockProcessStatistics';
import {mockProcessXml} from 'modules/mocks/mockProcessXml';
import {rest} from 'msw';
import {processDiagramStore} from './processDiagram';
import {processInstancesStore} from './processInstances';

describe('stores/processDiagram', () => {
  afterEach(() => {
    processDiagramStore.reset();
    processInstancesStore.reset();
  });

  it('should fetch xml and process statistics', async () => {
    mockServer.use(
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXml))
      )
    );

    expect(processDiagramStore.state.status).toBe('initial');
    expect(processDiagramStore.state.statistics).toEqual([]);

    processDiagramStore.fetchProcessDiagram('1');
    expect(processDiagramStore.state.status).toBe('fetching');
    await waitFor(() =>
      expect(processDiagramStore.state.status).toBe('fetched')
    );

    expect(processDiagramStore.state.statistics).toEqual(mockProcessStatistics);
    expect(processDiagramStore.state.xml).toBe(mockProcessXml);
  });

  it('should get flowNodeFilterOptions', async () => {
    mockServer.use(
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXml))
      )
    );

    processDiagramStore.fetchProcessDiagram('1');

    await waitFor(() =>
      expect(processDiagramStore.state.status).toBe('fetched')
    );

    expect(processDiagramStore.flowNodeFilterOptions).toEqual([
      {label: 'EndEvent_0crvjrk', value: 'EndEvent_0crvjrk'},
      {label: 'ServiceTask_0kt6c5i', value: 'ServiceTask_0kt6c5i'},
      {label: 'StartEvent_1', value: 'StartEvent_1'},
    ]);
  });

  it('should get flowNodeStates', async () => {
    mockServer.use(
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXml))
      )
    );

    processDiagramStore.fetchProcessDiagram('1');

    await waitFor(() =>
      expect(processDiagramStore.state.status).toBe('fetched')
    );

    expect(processDiagramStore.flowNodeStates).toEqual([
      {
        count: 1,
        flowNodeId: 'ServiceTask_0kt6c5i',
        flowNodeState: 'active',
      },
      {
        count: 2,
        flowNodeId: 'ServiceTask_0kt6c5i',
        flowNodeState: 'canceled',
      },
      {
        count: 3,
        flowNodeId: 'EndEvent_0crvjrk',
        flowNodeState: 'incidents',
      },
      {
        count: 4,
        flowNodeId: 'EndEvent_0crvjrk',
        flowNodeState: 'canceled',
      },
    ]);
  });

  it('should get overlaysData', async () => {
    mockServer.use(
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXml))
      )
    );

    processDiagramStore.fetchProcessDiagram('1');

    await waitFor(() =>
      expect(processDiagramStore.state.status).toBe('fetched')
    );

    expect(processDiagramStore.overlaysData).toEqual([
      {
        flowNodeId: 'ServiceTask_0kt6c5i',
        payload: {
          count: 1,
          flowNodeState: 'active',
        },
        position: {
          bottom: 9,
          left: 0,
        },
        type: 'statistics-active',
      },
      {
        flowNodeId: 'ServiceTask_0kt6c5i',
        payload: {
          count: 2,
          flowNodeState: 'canceled',
        },
        position: {
          left: 0,
          top: -16,
        },
        type: 'statistics-canceled',
      },
      {
        flowNodeId: 'EndEvent_0crvjrk',
        payload: {
          count: 3,
          flowNodeState: 'incidents',
        },
        position: {
          bottom: 9,
          right: 0,
        },
        type: 'statistics-incidents',
      },
      {
        flowNodeId: 'EndEvent_0crvjrk',
        payload: {
          count: 4,
          flowNodeState: 'canceled',
        },
        position: {
          left: 0,
          top: -16,
        },
        type: 'statistics-canceled',
      },
    ]);
  });

  it('should handle errors', async () => {
    mockServer.use(
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.status(500))
      )
    );
    processDiagramStore.fetchProcessDiagram('1');

    await waitFor(() => expect(processDiagramStore.state.status).toBe('error'));

    processDiagramStore.reset();

    expect(processDiagramStore.state.status).toBe('initial');

    mockServer.use(
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.status(500))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXml))
      )
    );

    processDiagramStore.fetchProcessDiagram('1');

    await waitFor(() => expect(processDiagramStore.state.status).toBe('error'));
  });

  it('should fetch process statistics depending on completed operations', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [
              {
                ...mockProcessInstances.processInstances[0],
                hasActiveOperation: true,
              },
            ],
            totalCount: 1,
          })
        )
      )
    );

    processDiagramStore.init();
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    expect(processDiagramStore.state.statistics).toEqual([]);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [{...mockProcessInstances.processInstances[0]}],
            totalCount: 1,
          })
        )
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXml))
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      )
    );

    processDiagramStore.fetchProcessDiagram('1');

    await waitFor(() =>
      expect(processDiagramStore.state.statistics).toEqual(
        mockProcessStatistics
      )
    );
  });

  it('should not fetch process statistics depending on completed operations if process and version filter does not exist', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [
              {
                ...mockProcessInstances.processInstances[0],
                hasActiveOperation: true,
              },
            ],
            totalCount: 1,
          })
        )
      )
    );
    processDiagramStore.init();
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );
    expect(processDiagramStore.state.statistics).toEqual([]);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [{...mockProcessInstances.processInstances[0]}],
            totalCount: 2,
          })
        )
      )
    );

    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.filteredProcessInstancesCount).toBe(2)
    );

    expect(processDiagramStore.state.statistics).toEqual([]);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    mockServer.use(
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXml))
      )
    );

    processDiagramStore.init();
    processDiagramStore.fetchProcessDiagram('1');

    await waitFor(() =>
      expect(processDiagramStore.state.statistics).toEqual(
        mockProcessStatistics
      )
    );

    const newStatisticsResponse = [
      ...mockProcessStatistics,
      {
        activityId: 'ServiceTask_0kt6c5i_new',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ];
    mockServer.use(
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(newStatisticsResponse))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXml))
      )
    );

    eventListeners.online();

    await waitFor(() =>
      expect(processDiagramStore.state.statistics).toEqual(
        newStatisticsResponse
      )
    );

    window.addEventListener = originalEventListener;
  });
});
