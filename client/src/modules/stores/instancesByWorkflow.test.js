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
    expect(instancesByWorkflowStore.state.status).toBe('fetched');
    expect(instancesByWorkflowStore.state.instances).toEqual(
      mockInstancesByWorkflow
    );
  });

  it('should set failed response on error', async () => {
    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({error: 'an error occurred'}))
      )
    );
    await instancesByWorkflowStore.getInstancesByWorkflow();
    expect(instancesByWorkflowStore.state.status).toBe('error');
    expect(instancesByWorkflowStore.state.instances).toEqual([]);
  });

  it('should reset store', async () => {
    await instancesByWorkflowStore.getInstancesByWorkflow();
    expect(instancesByWorkflowStore.state.status).toBe('fetched');
    expect(instancesByWorkflowStore.state.instances).toEqual(
      mockInstancesByWorkflow
    );

    instancesByWorkflowStore.reset();
    expect(instancesByWorkflowStore.state.status).toBe('initial');
    expect(instancesByWorkflowStore.state.instances).toEqual([]);
  });
});
