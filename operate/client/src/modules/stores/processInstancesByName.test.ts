/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {processInstancesByNameStore} from './processInstancesByName';
import {waitFor} from 'modules/testing-library';
import {mockFetchProcessInstancesByName} from 'modules/mocks/api/incidents/fetchProcessInstancesByName';

describe('stores/processInstancesByName', () => {
  const mockInstancesByProcess = [
    {
      bpmnProcessId: 'withoutIncidentsProcess',
      tenantId: '<default>',
      processName: 'Without Incidents Process',
      instancesWithActiveIncidentsCount: 0,
      activeInstancesCount: 28,
      processes: [
        {
          processId: '2251799813685668',
          tenantId: '<default>',
          version: 1,
          name: 'Without Incidents Process',
          bpmnProcessId: 'withoutIncidentsProcess',
          errorMessage: null,
          instancesWithActiveIncidentsCount: 0,
          activeInstancesCount: 14,
        },
        {
          processId: '2251799813685737',
          tenantId: '<default>',
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
      tenantId: '<default>',
      processName: 'Big variable process',
      instancesWithActiveIncidentsCount: 0,
      activeInstancesCount: 1,
      processes: [
        {
          processId: '2251799813686019',
          tenantId: '<default>',
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
    mockFetchProcessInstancesByName().withSuccess(mockInstancesByProcess);
  });

  afterEach(() => {
    processInstancesByNameStore.reset();
  });

  it('should get process instances by name', async () => {
    expect(processInstancesByNameStore.state.status).toBe('initial');
    processInstancesByNameStore.getProcessInstancesByName();

    expect(processInstancesByNameStore.state.status).toBe('first-fetch');
    await waitFor(() => {
      expect(processInstancesByNameStore.state.processInstances).toEqual(
        mockInstancesByProcess,
      );
    });
  });

  it('should start polling on init', async () => {
    jest.useFakeTimers();
    processInstancesByNameStore.init();
    await waitFor(() =>
      expect(processInstancesByNameStore.state.status).toBe('fetched'),
    );

    expect(processInstancesByNameStore.state.processInstances).toEqual(
      mockInstancesByProcess,
    );

    mockFetchProcessInstancesByName().withSuccess([]);

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(processInstancesByNameStore.state.processInstances).toEqual([]);
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should set failed response on error', async () => {
    mockFetchProcessInstancesByName().withServerError();

    await processInstancesByNameStore.getProcessInstancesByName();
    expect(processInstancesByNameStore.state.status).toBe('error');
    expect(processInstancesByNameStore.state.processInstances).toEqual([]);
  });

  it('should reset store', async () => {
    await processInstancesByNameStore.getProcessInstancesByName();
    expect(processInstancesByNameStore.state.status).toBe('fetched');
    expect(processInstancesByNameStore.state.processInstances).toEqual(
      mockInstancesByProcess,
    );

    processInstancesByNameStore.reset();
    expect(processInstancesByNameStore.state.status).toBe('initial');
    expect(processInstancesByNameStore.state.processInstances).toEqual([]);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    processInstancesByNameStore.getProcessInstancesByName();

    await waitFor(() =>
      expect(processInstancesByNameStore.state.processInstances).toEqual(
        mockInstancesByProcess,
      ),
    );

    const newMockInstancesByProcess = [
      ...mockInstancesByProcess,
      {
        bpmnProcessId: 'anotherProcess',
        tenantId: '<default>',
        processName: 'Another Process',
        instancesWithActiveIncidentsCount: 5,
        activeInstancesCount: 30,
        processes: [],
      },
    ];
    mockFetchProcessInstancesByName().withSuccess(newMockInstancesByProcess);

    eventListeners.online();

    await waitFor(() =>
      expect(processInstancesByNameStore.state.processInstances).toEqual(
        newMockInstancesByProcess,
      ),
    );

    window.addEventListener = originalEventListener;
  });
});
