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

import {groupedProcessesMock} from 'modules/testUtils';
import {processesStore} from './processes.migration';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {generateProcessKey} from 'modules/utils/generateProcessKey';

describe('processes.migration store', () => {
  const originalWindow = {...window};
  const locationSpy = jest.spyOn(window, 'location', 'get');

  afterEach(() => {
    processesStore.reset();
    locationSpy.mockClear();
  });

  it('should get targetProcessVersions', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    await processesStore.fetchProcesses();

    expect(processesStore.targetProcessVersions).toEqual([]);

    processesStore.setSelectedTargetProcess(generateProcessKey('demoProcess'));

    expect(processesStore.targetProcessVersions).toEqual([1, 2, 3]);

    processesStore.setSelectedTargetProcess(
      generateProcessKey('bigVarProcess', '<tenant-A>'),
    );

    expect(processesStore.targetProcessVersions).toEqual([1, 2]);
  });

  it('should get selectedTargetProcessId', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    await processesStore.fetchProcesses();

    expect(processesStore.selectedTargetProcessId).toBeUndefined();

    processesStore.setSelectedTargetProcess(generateProcessKey('demoProcess'));
    processesStore.setSelectedTargetVersion(2);

    expect(processesStore.selectedTargetProcessId).toEqual('demoProcess2');

    processesStore.setSelectedTargetProcess(
      generateProcessKey('bigVarProcess', '<tenant-A>'),
    );
    processesStore.setSelectedTargetVersion(1);

    expect(processesStore.selectedTargetProcessId).toEqual('2251799813685894');
  });

  it('should get selectable target processes when resource based permissions are enabled', async () => {
    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    await processesStore.fetchProcesses();

    expect(processesStore.filteredProcesses).toEqual([
      {
        bpmnProcessId: 'demoProcess',
        key: '{demoProcess}-{<default>}',
        name: 'New demo process',
        tenantId: '<default>',
        processes: [
          {
            id: 'demoProcess3',
            name: 'New demo process',
            version: 3,
            bpmnProcessId: 'demoProcess',
          },
          {
            id: 'demoProcess2',
            name: 'Demo process',
            version: 2,
            bpmnProcessId: 'demoProcess',
          },
          {
            id: 'demoProcess1',
            name: 'Demo process',
            version: 1,
            bpmnProcessId: 'demoProcess',
          },
        ],
        permissions: ['UPDATE_PROCESS_INSTANCE'],
      },
    ]);
    window.clientConfig = undefined;
  });

  it('should get selectable target processes when a process with single version is selected', async () => {
    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search:
        '?active=true&incidents=true&process=bigVarProcess&version=1&tenant=<default>',
    }));
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    await processesStore.fetchProcesses();

    expect(processesStore.filteredProcesses).toEqual([
      {
        bpmnProcessId: 'demoProcess',
        key: '{demoProcess}-{<default>}',
        name: 'New demo process',
        permissions: ['UPDATE_PROCESS_INSTANCE'],
        processes: [
          {
            bpmnProcessId: 'demoProcess',
            id: 'demoProcess3',
            name: 'New demo process',
            version: 3,
          },
          {
            bpmnProcessId: 'demoProcess',
            id: 'demoProcess2',
            name: 'Demo process',
            version: 2,
          },
          {
            bpmnProcessId: 'demoProcess',
            id: 'demoProcess1',
            name: 'Demo process',
            version: 1,
          },
        ],
        tenantId: '<default>',
      },
      {
        bpmnProcessId: 'eventBasedGatewayProcess',
        key: '{eventBasedGatewayProcess}-{<default>}',
        name: null,
        permissions: ['DELETE'],
        processes: [
          {
            bpmnProcessId: 'eventBasedGatewayProcess',
            id: '2251799813696866',
            name: 'Event based gateway with timer start',
            version: 2,
          },
          {
            bpmnProcessId: 'eventBasedGatewayProcess',
            id: '2251799813685911',
            name: 'Event based gateway with message start',
            version: 1,
          },
        ],
        tenantId: '<default>',
      },

      {
        bpmnProcessId: 'bigVarProcess',
        key: '{bigVarProcess}-{<tenant-A>}',
        name: 'Big variable process',
        permissions: ['DELETE_PROCESS_INSTANCE'],
        processes: [
          {
            bpmnProcessId: 'bigVarProcess',
            id: '2251799813685893',
            name: 'Big variable process',
            version: 2,
          },
          {
            bpmnProcessId: 'bigVarProcess',
            id: '2251799813685894',
            name: 'Big variable process',
            version: 1,
          },
        ],
        tenantId: '<tenant-A>',
      },

      {
        bpmnProcessId: 'orderProcess',
        key: '{orderProcess}-{<default>}',
        name: 'Order',
        processes: [],
        tenantId: '<default>',
      },
    ]);
    window.clientConfig = undefined;
  });

  it('should get selectable target processes when a process with multiple versions is selected', async () => {
    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: '?active=true&incidents=true&process=demoProcess&version=3',
    }));
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    await processesStore.fetchProcesses();

    expect(processesStore.filteredProcesses).toEqual([
      {
        bpmnProcessId: 'demoProcess',
        key: '{demoProcess}-{<default>}',
        name: 'New demo process',
        permissions: ['UPDATE_PROCESS_INSTANCE'],
        processes: [
          {
            bpmnProcessId: 'demoProcess',
            id: 'demoProcess2',
            name: 'Demo process',
            version: 2,
          },
          {
            bpmnProcessId: 'demoProcess',
            id: 'demoProcess1',
            name: 'Demo process',
            version: 1,
          },
        ],
        tenantId: '<default>',
      },
      {
        bpmnProcessId: 'eventBasedGatewayProcess',
        key: '{eventBasedGatewayProcess}-{<default>}',
        name: null,
        permissions: ['DELETE'],
        processes: [
          {
            bpmnProcessId: 'eventBasedGatewayProcess',
            id: '2251799813696866',
            name: 'Event based gateway with timer start',
            version: 2,
          },
          {
            bpmnProcessId: 'eventBasedGatewayProcess',
            id: '2251799813685911',
            name: 'Event based gateway with message start',
            version: 1,
          },
        ],
        tenantId: '<default>',
      },
      {
        bpmnProcessId: 'bigVarProcess',
        key: '{bigVarProcess}-{<default>}',
        name: 'Big variable process',
        permissions: ['DELETE_PROCESS_INSTANCE'],
        processes: [
          {
            bpmnProcessId: 'bigVarProcess',
            id: '2251799813685892',
            name: 'Big variable process',
            version: 1,
          },
        ],
        tenantId: '<default>',
      },
      {
        bpmnProcessId: 'bigVarProcess',
        key: '{bigVarProcess}-{<tenant-A>}',
        name: 'Big variable process',
        permissions: ['DELETE_PROCESS_INSTANCE'],
        processes: [
          {
            bpmnProcessId: 'bigVarProcess',
            id: '2251799813685893',
            name: 'Big variable process',
            version: 2,
          },
          {
            bpmnProcessId: 'bigVarProcess',
            id: '2251799813685894',
            name: 'Big variable process',
            version: 1,
          },
        ],
        tenantId: '<tenant-A>',
      },
      {
        bpmnProcessId: 'orderProcess',
        key: '{orderProcess}-{<default>}',
        name: 'Order',
        processes: [],
        tenantId: '<default>',
      },
    ]);
    window.clientConfig = undefined;
  });
});
