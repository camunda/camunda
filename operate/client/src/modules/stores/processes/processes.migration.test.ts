/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockProcessDefinitions} from 'modules/testUtils';
import {processesStore} from './processes.migration';
import {generateProcessKey} from 'modules/utils/generateProcessKey';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';

describe('processes.migration store', () => {
  afterEach(() => {
    processesStore.reset();
  });

  it('should get targetProcessVersions', async () => {
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);

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
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);

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

  it('should get selectable target processes when a process with single version is selected', async () => {
    vi.stubGlobal('location', {
      ...window.location,
      search:
        '?active=true&incidents=true&process=bigVarProcess&version=1&tenant=<default>',
    });
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);

    await processesStore.fetchProcesses();

    expect(processesStore.filteredProcesses).toEqual([
      {
        bpmnProcessId: 'demoProcess',
        key: '{demoProcess}-{<default>}',
        name: 'New demo process',
        processes: [
          {
            bpmnProcessId: 'demoProcess',
            id: 'demoProcess3',
            name: 'New demo process',
            version: 3,
            versionTag: null,
          },
          {
            bpmnProcessId: 'demoProcess',
            id: 'demoProcess2',
            name: 'Demo process',
            version: 2,
            versionTag: null,
          },
          {
            bpmnProcessId: 'demoProcess',
            id: 'demoProcess1',
            name: 'Demo process',
            version: 1,
            versionTag: null,
          },
        ],
        tenantId: '<default>',
      },
      {
        bpmnProcessId: 'eventBasedGatewayProcess',
        key: '{eventBasedGatewayProcess}-{<default>}',
        name: null,
        processes: [
          {
            bpmnProcessId: 'eventBasedGatewayProcess',
            id: '2251799813696866',
            name: 'eventBasedGatewayProcess',
            version: 2,
            versionTag: null,
          },
          {
            bpmnProcessId: 'eventBasedGatewayProcess',
            id: '2251799813685911',
            name: 'Event based gateway with message start',
            version: 1,
            versionTag: null,
          },
        ],
        tenantId: '<default>',
      },

      {
        bpmnProcessId: 'bigVarProcess',
        key: '{bigVarProcess}-{<tenant-A>}',
        name: 'Big variable process',
        processes: [
          {
            bpmnProcessId: 'bigVarProcess',
            id: '2251799813685893',
            name: 'Big variable process',
            version: 2,
            versionTag: null,
          },
          {
            bpmnProcessId: 'bigVarProcess',
            id: '2251799813685894',
            name: 'Big variable process',
            version: 1,
            versionTag: null,
          },
        ],
        tenantId: '<tenant-A>',
      },

      {
        bpmnProcessId: 'orderProcess',
        key: '{orderProcess}-{<default>}',
        name: 'Order',
        processes: [
          {
            bpmnProcessId: 'orderProcess',
            id: 'orderProcess1',
            name: 'Order',
            version: 1,
            versionTag: null,
          },
        ],
        tenantId: '<default>',
      },
    ]);
    window.clientConfig = undefined;
  });

  it('should get selectable target processes when a process with multiple versions is selected', async () => {
    vi.stubGlobal('location', {
      ...window.location,
      search: '?active=true&incidents=true&process=demoProcess&version=3',
    });
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);

    await processesStore.fetchProcesses();

    expect(processesStore.filteredProcesses).toEqual([
      {
        bpmnProcessId: 'demoProcess',
        key: '{demoProcess}-{<default>}',
        name: 'New demo process',
        processes: [
          {
            bpmnProcessId: 'demoProcess',
            id: 'demoProcess2',
            name: 'Demo process',
            version: 2,
            versionTag: null,
          },
          {
            bpmnProcessId: 'demoProcess',
            id: 'demoProcess1',
            name: 'Demo process',
            version: 1,
            versionTag: null,
          },
        ],
        tenantId: '<default>',
      },
      {
        bpmnProcessId: 'eventBasedGatewayProcess',
        key: '{eventBasedGatewayProcess}-{<default>}',
        name: null,
        processes: [
          {
            bpmnProcessId: 'eventBasedGatewayProcess',
            id: '2251799813696866',
            name: 'eventBasedGatewayProcess',
            version: 2,
            versionTag: null,
          },
          {
            bpmnProcessId: 'eventBasedGatewayProcess',
            id: '2251799813685911',
            name: 'Event based gateway with message start',
            version: 1,
            versionTag: null,
          },
        ],
        tenantId: '<default>',
      },
      {
        bpmnProcessId: 'bigVarProcess',
        key: '{bigVarProcess}-{<default>}',
        name: 'Big variable process',
        processes: [
          {
            bpmnProcessId: 'bigVarProcess',
            id: '2251799813685892',
            name: 'Big variable process',
            version: 1,
            versionTag: 'MyVersionTag',
          },
        ],
        tenantId: '<default>',
      },
      {
        bpmnProcessId: 'bigVarProcess',
        key: '{bigVarProcess}-{<tenant-A>}',
        name: 'Big variable process',
        processes: [
          {
            bpmnProcessId: 'bigVarProcess',
            id: '2251799813685893',
            name: 'Big variable process',
            version: 2,
            versionTag: null,
          },
          {
            bpmnProcessId: 'bigVarProcess',
            id: '2251799813685894',
            name: 'Big variable process',
            version: 1,
            versionTag: null,
          },
        ],
        tenantId: '<tenant-A>',
      },
      {
        bpmnProcessId: 'orderProcess',
        key: '{orderProcess}-{<default>}',
        name: 'Order',
        processes: [
          {
            bpmnProcessId: 'orderProcess',
            id: 'orderProcess1',
            name: 'Order',
            version: 1,
            versionTag: null,
          },
        ],
        tenantId: '<default>',
      },
    ]);
    window.clientConfig = undefined;
  });

  it('should pre-set target process version', async () => {
    // given: demoProcess version 1 as source process
    vi.stubGlobal('location', {
      ...window.location,
      search: '?active=true&incidents=true&process=demoProcess&version=1',
    });

    // when initializing processesStore
    processesStore.init();
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);

    await processesStore.fetchProcesses();

    // then expect demoProcess version 3 to be pre-selected
    expect(processesStore.selectedTargetProcessId).toBe('demoProcess3');
    expect(processesStore.latestProcessVersion).toEqual(3);
  });

  it('should pre-set latest previous target process version', async () => {
    // given: demoProcess version 3 (latest version) as source process
    vi.stubGlobal('location', {
      ...window.location,
      search: '?active=true&incidents=true&process=demoProcess&version=3',
    });

    // when initializing processesStore
    processesStore.init();
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);

    await processesStore.fetchProcesses();

    // then expect no target version to be selected
    expect(processesStore.selectedTargetProcessId).toBe(undefined);
    expect(processesStore.latestProcessVersion).toEqual(undefined);
  });
});
