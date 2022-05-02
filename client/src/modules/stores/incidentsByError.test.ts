/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {incidentsByErrorStore} from './incidentsByError';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/dom';

describe('stores/incidentsByError', () => {
  const mockIncidentsByError = [
    {
      errorMessage:
        "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
      instancesWithErrorCount: 121,
      processes: [
        {
          processId: '2251799813698839',
          version: 3,
          name: null,
          bpmnProcessId: 'complexProcess',
          errorMessage:
            "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
          instancesWithActiveIncidentsCount: 101,
          activeInstancesCount: 0,
        },
        {
          processId: '2251799813695224',
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
      processes: [
        {
          processId: '2251799813698839',
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
    expect(incidentsByErrorStore.state.status).toBe('initial');
    incidentsByErrorStore.getIncidentsByError();

    expect(incidentsByErrorStore.state.status).toBe('first-fetch');
    await waitFor(() => {
      expect(incidentsByErrorStore.state.incidents).toEqual(
        mockIncidentsByError
      );
    });
  });

  it('should start polling on init', async () => {
    jest.useFakeTimers();
    incidentsByErrorStore.init();
    await waitFor(() =>
      expect(incidentsByErrorStore.state.status).toBe('fetched')
    );

    expect(incidentsByErrorStore.state.incidents).toEqual(mockIncidentsByError);

    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(incidentsByErrorStore.state.incidents).toEqual([]);
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should set failed response on error', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({error: 'an error occurred'}))
      )
    );
    await incidentsByErrorStore.getIncidentsByError();
    expect(incidentsByErrorStore.state.status).toBe('error');
    expect(incidentsByErrorStore.state.incidents).toEqual([]);
  });

  it('should reset store', async () => {
    await incidentsByErrorStore.getIncidentsByError();
    expect(incidentsByErrorStore.state.status).toBe('fetched');
    expect(incidentsByErrorStore.state.incidents).toEqual(mockIncidentsByError);

    incidentsByErrorStore.reset();
    expect(incidentsByErrorStore.state.status).toBe('initial');
    expect(incidentsByErrorStore.state.incidents).toEqual([]);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    incidentsByErrorStore.getIncidentsByError();

    await waitFor(() =>
      expect(incidentsByErrorStore.state.incidents).toEqual(
        mockIncidentsByError
      )
    );

    const newMockIncidentsByError = [
      ...mockIncidentsByError,
      {
        errorMessage: 'some other error',
        instancesWithErrorCount: 100,
        processes: [],
      },
    ];
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(newMockIncidentsByError))
      )
    );

    eventListeners.online();

    await waitFor(() =>
      expect(incidentsByErrorStore.state.incidents).toEqual(
        newMockIncidentsByError
      )
    );

    window.addEventListener = originalEventListener;
  });
});
