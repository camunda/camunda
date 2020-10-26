/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {instancesByWorkflowStore} from './instancesByWorkflow';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

describe('stores/instancesByWorkflow', () => {
  const mockInstancesByWorkflow = [
    {
      bpmnProcessId: 'withoutIncidentsProcess',
      workflowName: 'Without Incidents Process',
      instancesWithActiveIncidentsCount: 0,
      activeInstancesCount: 28,
      workflows: [
        {
          workflowId: '2251799813685668',
          version: 1,
          name: 'Without Incidents Process',
          bpmnProcessId: 'withoutIncidentsProcess',
          errorMessage: null,
          instancesWithActiveIncidentsCount: 0,
          activeInstancesCount: 14,
        },
        {
          workflowId: '2251799813685737',
          version: 2,
          name: 'Without Incidents Process',
          bpmnProcessId: 'withoutIncidentsProcess',
          errorMessage: null,
          instancesWithActiveIncidentsCount: 0,
          activeInstancesCount: 14,
        },
      ],
    },
    {
      bpmnProcessId: 'bigVarProcess',
      workflowName: 'Big variable process',
      instancesWithActiveIncidentsCount: 0,
      activeInstancesCount: 1,
      workflows: [
        {
          workflowId: '2251799813686019',
          version: 1,
          name: 'Big variable process',
          bpmnProcessId: 'bigVarProcess',
          errorMessage: null,
          instancesWithActiveIncidentsCount: 0,
          activeInstancesCount: 1,
        },
      ],
    },
  ];

  beforeEach(() => {
    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.json(mockInstancesByWorkflow))
      )
    );
  });

  afterEach(() => {
    instancesByWorkflowStore.reset();
  });

  it('should get instances by workflow', async () => {
    await instancesByWorkflowStore.getInstancesByWorkflow();
    expect(instancesByWorkflowStore.state.isFailed).toBe(false);
    expect(instancesByWorkflowStore.state.isLoaded).toBe(true);
    expect(instancesByWorkflowStore.state.instances).toEqual(
      mockInstancesByWorkflow
    );
  });

  it('should set failed response on error', async () => {
    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.json({error: 'an error occured'}))
      )
    );
    await instancesByWorkflowStore.getInstancesByWorkflow();
    expect(instancesByWorkflowStore.state.isFailed).toBe(true);
    expect(instancesByWorkflowStore.state.isLoaded).toBe(true);
    expect(instancesByWorkflowStore.state.instances).toEqual([]);
  });

  it('should reset store', async () => {
    await instancesByWorkflowStore.getInstancesByWorkflow();
    expect(instancesByWorkflowStore.state.isLoaded).toBe(true);
    expect(instancesByWorkflowStore.state.instances).toEqual(
      mockInstancesByWorkflow
    );

    instancesByWorkflowStore.reset();
    expect(instancesByWorkflowStore.state.isLoaded).toBe(false);
    expect(instancesByWorkflowStore.state.instances).toEqual([]);
  });

  it('should get isDataAvalibale', async () => {
    expect(instancesByWorkflowStore.isDataAvailable).toBe(false);
    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.json({error: 'an error occured'}))
      )
    );
    await instancesByWorkflowStore.getInstancesByWorkflow();
    expect(instancesByWorkflowStore.isDataAvailable).toBe(false);

    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );
    await instancesByWorkflowStore.getInstancesByWorkflow();
    expect(instancesByWorkflowStore.isDataAvailable).toBe(false);

    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.json(mockInstancesByWorkflow))
      )
    );
    await instancesByWorkflowStore.getInstancesByWorkflow();
    expect(instancesByWorkflowStore.isDataAvailable).toBe(true);
  });
});
