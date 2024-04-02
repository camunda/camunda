/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {incidentsByErrorStore} from './incidentsByError';
import {waitFor} from 'modules/testing-library';
import {mockFetchIncidentsByError} from 'modules/mocks/api/incidents/fetchIncidentsByError';

describe('stores/incidentsByError', () => {
  const mockIncidentsByError = [
    {
      errorMessage:
        "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
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
