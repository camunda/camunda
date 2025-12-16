/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processesStore} from './processes.list';
import {waitFor} from 'modules/testing-library';
import {mockProcessDefinitions, searchResult} from 'modules/testUtils';
import {generateProcessKey} from 'modules/utils/generateProcessKey';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import {mapProcessDefinitionsToProcessesV1} from 'modules/api/processes/fetchGroupedProcesses';

describe('stores/processes/processes.list', () => {
  afterEach(() => {
    processesStore.reset();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: Record<string, () => void> = {};
    vi.spyOn(window, 'addEventListener').mockImplementation(
      (event: string, cb: EventListenerOrEventListenerObject) => {
        eventListeners[event] = cb as () => void;
      },
    );

    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions, {
      expectPolling: false,
    });

    processesStore.fetchProcesses();

    await waitFor(() =>
      expect(
        processesStore.state.processes.map((process) => {
          const {key, ...processDto} = process;
          return processDto;
        }),
      ).toEqual(
        mapProcessDefinitionsToProcessesV1(mockProcessDefinitions.items),
      ),
    );

    const firstGroupedProcesses = mockProcessDefinitions.items.slice(0, 3);

    mockSearchProcessDefinitions().withSuccess(
      searchResult(firstGroupedProcesses),
      {expectPolling: false},
    );

    eventListeners.online();

    await waitFor(() =>
      expect(
        processesStore.state.processes.map((process) => {
          const {key, ...processDto} = process;
          return processDto;
        }),
      ).toEqual(mapProcessDefinitionsToProcessesV1(firstGroupedProcesses)),
    );

    mockSearchProcessDefinitions().withSuccess(
      searchResult(firstGroupedProcesses),
      {expectPolling: false},
    );

    eventListeners.online();
  });

  it('should get process id', async () => {
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);

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
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);

    processesStore.fetchProcesses();

    await waitFor(() =>
      expect(
        processesStore.state.processes.map((process) => {
          const {key, ...processDto} = process;
          return processDto;
        }),
      ).toEqual(
        mapProcessDefinitionsToProcessesV1(mockProcessDefinitions.items),
      ),
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
      versionTag: null,
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
      versionTag: null,
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
      versionTag: 'MyVersionTag',
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
      versionTag: null,
    });
  });

  it('should get process', async () => {
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);

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
