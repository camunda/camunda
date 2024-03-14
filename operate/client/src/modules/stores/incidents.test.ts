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

import {incidentsStore} from './incidents';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {waitFor} from 'modules/testing-library';
import {createInstance} from 'modules/testUtils';
import {mockIncidents} from 'modules/mocks/incidents';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';

describe('stores/incidents', () => {
  afterEach(() => {
    processInstanceDetailsStore.reset();
    incidentsStore.reset();
  });
  beforeEach(() => {
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);
  });

  it('should poll for incidents if instance state is incident', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'INCIDENT'}),
    );

    jest.useFakeTimers();
    incidentsStore.init();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual(mockIncidents),
    );

    mockFetchProcessInstanceIncidents().withSuccess({
      ...mockIncidents,
      count: 2,
    });

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidents,
        count: 2,
      }),
    );

    mockFetchProcessInstanceIncidents().withSuccess({
      ...mockIncidents,
      count: 3,
    });

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidents,
        count: 3,
      }),
    );

    // stop polling when instance state is no longer an incident.
    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'CANCELED'}),
    );

    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidents,
        count: 3,
      }),
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should reset store', async () => {
    expect(incidentsStore.state.response).toEqual(null);
    expect(incidentsStore.state.isLoaded).toBe(false);

    incidentsStore.setIncidents(mockIncidents);
    expect(incidentsStore.state.response).toEqual(mockIncidents);
    expect(incidentsStore.state.isLoaded).toBe(true);

    incidentsStore.reset();
    expect(incidentsStore.state.response).toEqual(null);
    expect(incidentsStore.state.isLoaded).toBe(false);
  });

  it('should get incidents', async () => {
    expect(incidentsStore.incidents).toEqual([]);
    incidentsStore.setIncidents(mockIncidents);

    expect(incidentsStore.incidents).toEqual([
      {
        creationTime: '2020-10-08T09:18:58.258+0000',
        errorMessage: 'Cannot connect to server delivery05',
        errorType: {
          id: '1',
          name: 'No more retries left',
        },
        flowNodeId: 'Task_162x79i',
        flowNodeInstanceId: '2251799813699889',
        flowNodeName: 'Task_162x79i',
        hasActiveOperation: false,
        id: '2251799813700301',
        jobId: '2251799813699901',
        lastOperation: null,
        isSelected: false,
        rootCauseInstance: {
          instanceId: '2251799813695335',
          processDefinitionId: '2251799813687515',
          processDefinitionName: 'Event based gateway with timer start',
        },
      },
    ]);
  });

  it('should get flowNodes', async () => {
    incidentsStore.setIncidents(mockIncidents);

    expect(incidentsStore.flowNodes).toEqual([
      {
        id: 'Task_162x79i',
        name: 'Task_162x79i',
        count: 1,
      },
    ]);
  });

  it('should get errorTypes', async () => {
    incidentsStore.setIncidents(mockIncidents);

    expect(incidentsStore.errorTypes).toEqual([
      {
        id: 'NO_MORE_RETRIES',
        name: 'No more retries left',
        count: 1,
      },
    ]);
  });

  it('should get incidentsCount', async () => {
    expect(incidentsStore.incidentsCount).toBe(0);
    incidentsStore.setIncidents(mockIncidents);

    expect(incidentsStore.incidentsCount).toBe(1);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'INCIDENT',
      }),
    );
    incidentsStore.init();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual(mockIncidents),
    );

    mockFetchProcessInstanceIncidents().withSuccess({
      ...mockIncidents,
      count: 3,
    });

    eventListeners.online();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidents,
        count: 3,
      }),
    );

    window.addEventListener = originalEventListener;
  });
});
