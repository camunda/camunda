/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {incidentsByErrorStore} from './incidentsByError';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

describe('stores/incidentsByError', () => {
  const mockIncidentsByError = [
    {
      errorMessage:
        "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
      instancesWithErrorCount: 121,
      workflows: [
        {
          workflowId: '2251799813698839',
          version: 3,
          name: null,
          bpmnProcessId: 'complexProcess',
          errorMessage:
            "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
          instancesWithActiveIncidentsCount: 101,
          activeInstancesCount: 0,
        },
        {
          workflowId: '2251799813695224',
          version: 2,
          name: 'Event based gateway with timer start',
          bpmnProcessId: 'eventBasedGatewayProcess',
          errorMessage:
            "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
          instancesWithActiveIncidentsCount: 20,
          activeInstancesCount: 0,
        },
      ],
    },
    {
      errorMessage:
        'Expected at least one condition to evaluate to true, or to have a default flow',
      instancesWithErrorCount: 101,
      workflows: [
        {
          workflowId: '2251799813698839',
          version: 3,
          name: null,
          bpmnProcessId: 'complexProcess',
          errorMessage:
            'Expected at least one condition to evaluate to true, or to have a default flow',
          instancesWithActiveIncidentsCount: 101,
          activeInstancesCount: 0,
        },
      ],
    },
  ];

  beforeEach(() => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByError))
      )
    );
  });

  afterEach(() => {
    incidentsByErrorStore.reset();
  });

  it('should get incidents by error', async () => {
    await incidentsByErrorStore.getIncidentsByError();
    expect(incidentsByErrorStore.state.isFailed).toBe(false);
    expect(incidentsByErrorStore.state.isLoaded).toBe(true);
    expect(incidentsByErrorStore.state.incidents).toEqual(mockIncidentsByError);
  });

  it('should set failed response on error', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json({error: 'an error occured'}))
      )
    );
    await incidentsByErrorStore.getIncidentsByError();
    expect(incidentsByErrorStore.state.isFailed).toBe(true);
    expect(incidentsByErrorStore.state.isLoaded).toBe(true);
    expect(incidentsByErrorStore.state.incidents).toEqual([]);
  });

  it('should reset store', async () => {
    await incidentsByErrorStore.getIncidentsByError();
    expect(incidentsByErrorStore.state.isLoaded).toBe(true);
    expect(incidentsByErrorStore.state.incidents).toEqual(mockIncidentsByError);

    incidentsByErrorStore.reset();
    expect(incidentsByErrorStore.state.isLoaded).toBe(false);
    expect(incidentsByErrorStore.state.incidents).toEqual([]);
  });

  it('should get isDataAvalibale', async () => {
    expect(incidentsByErrorStore.isDataAvailable).toBe(false);
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json({error: 'an error occured'}))
      )
    );
    await incidentsByErrorStore.getIncidentsByError();
    expect(incidentsByErrorStore.isDataAvailable).toBe(false);

    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );
    await incidentsByErrorStore.getIncidentsByError();
    expect(incidentsByErrorStore.isDataAvailable).toBe(false);

    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByError))
      )
    );
    await incidentsByErrorStore.getIncidentsByError();
    expect(incidentsByErrorStore.isDataAvailable).toBe(true);
  });
});
