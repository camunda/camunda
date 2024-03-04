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

import {processesStore} from './processes.list';
import {waitFor} from 'modules/testing-library';
import {groupedProcessesMock} from 'modules/testUtils';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {generateProcessKey} from 'modules/utils/generateProcessKey';

describe('stores/processes/processes.list', () => {
  afterEach(() => {
    processesStore.reset();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    processesStore.fetchProcesses();

    await waitFor(() =>
      expect(
        processesStore.state.processes.map((process) => {
          const {key, ...processDto} = process;
          return processDto;
        }),
      ).toEqual(groupedProcessesMock),
    );

    const firstGroupedProcess = groupedProcessesMock[0]!;
    const newGroupedProcessesResponse = [firstGroupedProcess];

    mockFetchGroupedProcesses().withSuccess(newGroupedProcessesResponse);

    eventListeners.online();

    await waitFor(() =>
      expect(
        processesStore.state.processes.map((process) => {
          const {key, ...processDto} = process;
          return processDto;
        }),
      ).toEqual(newGroupedProcessesResponse),
    );

    window.addEventListener = originalEventListener;
  });

  it('should get process id', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    processesStore.fetchProcesses();

    await waitFor(() => expect(processesStore.state.status).toBe('fetched'));

    expect(
      processesStore.getProcessId({process: 'demoProcess', version: '1'}),
    ).toBe('demoProcess1');
    expect(
      processesStore.getProcessId({process: 'demoProcess', version: '2'}),
    ).toBe('demoProcess2');
    expect(
      processesStore.getProcessId({process: 'demoProcess', version: '3'}),
    ).toBe('demoProcess3');
    expect(
      processesStore.getProcessId({
        process: 'eventBasedGatewayProcess',
        version: '1',
      }),
    ).toBe('2251799813685911');
    expect(processesStore.getProcessId()).toBeUndefined();
    expect(
      processesStore.getProcessId({process: 'demoProcess'}),
    ).toBeUndefined();
    expect(
      processesStore.getProcessId({process: 'demoProcess', version: 'all'}),
    ).toBeUndefined();
    expect(
      processesStore.getProcessId({
        process: 'bigVarProcess',
        version: '1',
        tenant: '<tenant-A>',
      }),
    ).toBe('2251799813685894');
  });

  it('should get versions by process and tenant', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    processesStore.fetchProcesses();

    await waitFor(() =>
      expect(
        processesStore.state.processes.map((process) => {
          const {key, ...processDto} = process;
          return processDto;
        }),
      ).toEqual(groupedProcessesMock),
    );

    expect(
      processesStore.versionsByProcessAndTenant[
        generateProcessKey('demoProcess')
      ]?.[1],
    ).toEqual({
      bpmnProcessId: 'demoProcess',
      id: 'demoProcess2',
      name: 'Demo process',
      version: 2,
    });

    expect(
      processesStore.versionsByProcessAndTenant[
        generateProcessKey('eventBasedGatewayProcess')
      ]?.[0],
    ).toEqual({
      bpmnProcessId: 'eventBasedGatewayProcess',
      id: '2251799813685911',
      name: 'Event based gateway with message start',
      version: 1,
    });

    expect(
      processesStore.versionsByProcessAndTenant[
        generateProcessKey('bigVarProcess', '<default>')
      ]?.[0],
    ).toEqual({
      id: '2251799813685892',
      name: 'Big variable process',
      version: 1,
      bpmnProcessId: 'bigVarProcess',
    });

    expect(
      processesStore.versionsByProcessAndTenant[
        generateProcessKey('bigVarProcess', '<tenant-A>')
      ]?.[0],
    ).toEqual({
      id: '2251799813685894',
      name: 'Big variable process',
      version: 1,
      bpmnProcessId: 'bigVarProcess',
    });
  });

  it('should get process', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    processesStore.fetchProcesses();

    await waitFor(() => expect(processesStore.state.status).toBe('fetched'));

    expect(
      processesStore.getProcess({bpmnProcessId: 'orderProcess'})?.key,
    ).toBe('{orderProcess}-{<default>}');
    expect(
      processesStore.getProcess({
        bpmnProcessId: 'orderProcess',
        tenantId: '<default>',
      })?.key,
    ).toBe('{orderProcess}-{<default>}');
    expect(
      processesStore.getProcess({
        bpmnProcessId: 'orderProcess',
        tenantId: '<some-tenant>',
      })?.key,
    ).toBeUndefined();
    expect(
      processesStore.getProcess({
        bpmnProcessId: 'bigVarProcess',
        tenantId: '<tenant-A>',
      })?.key,
    ).toBe('{bigVarProcess}-{<tenant-A>}');
    expect(
      processesStore.getProcess({
        bpmnProcessId: 'bigVarProcess',
        tenantId: '<default>',
      })?.key,
    ).toBe('{bigVarProcess}-{<default>}');
    expect(
      processesStore.getProcess({
        bpmnProcessId: 'bigVarProcess',
      })?.key,
    ).toBe('{bigVarProcess}-{<default>}');
  });
});
