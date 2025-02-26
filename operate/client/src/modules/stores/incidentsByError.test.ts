/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {incidentsByErrorStore} from './incidentsByError';
import {waitFor} from 'modules/testing-library';
import {mockFetchIncidentsByError} from 'modules/mocks/api/incidents/fetchIncidentsByError';

describe('stores/incidentsByError', () => {
  const mockIncidentsByError = [
    {
      errorMessage:
        "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
      incidentErrorHashCode: 1111,
      instancesWithErrorCount: 121,
      processes: [
        {
          processId: '2251799813698839',
          tenantId: '<default>',
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
          tenantId: '<default>',
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
      incidentErrorHashCode: 1112,
      instancesWithErrorCount: 101,
      processes: [
        {
          processId: '2251799813698839',
          tenantId: '<default>',
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
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError, {
      expectPolling: false,
    });
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
        mockIncidentsByError,
      );
    });
  });

  it('should start polling on init', async () => {
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError, {
      expectPolling: true,
    });
    jest.useFakeTimers();
    incidentsByErrorStore.init();
    await waitFor(() =>
      expect(incidentsByErrorStore.state.status).toBe('fetched'),
    );

    expect(incidentsByErrorStore.state.incidents).toEqual(mockIncidentsByError);

    mockFetchIncidentsByError().withSuccess([], {expectPolling: true});

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(incidentsByErrorStore.state.incidents).toEqual([]);
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should set failed response on error', async () => {
    mockFetchIncidentsByError().withServerError();

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
        mockIncidentsByError,
      ),
    );

    const newMockIncidentsByError = [
      ...mockIncidentsByError,
      {
        errorMessage: 'some other error',
        incidentErrorHashCode: 1113,
        instancesWithErrorCount: 100,
        processes: [],
      },
    ];

    mockFetchIncidentsByError().withSuccess(newMockIncidentsByError);

    eventListeners.online();

    await waitFor(() =>
      expect(incidentsByErrorStore.state.incidents).toEqual(
        newMockIncidentsByError,
      ),
    );

    window.addEventListener = originalEventListener;
  });
});
