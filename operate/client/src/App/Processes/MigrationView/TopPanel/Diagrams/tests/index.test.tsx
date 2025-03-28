/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {groupedProcessesMock, mockProcessXML} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.migration';

import {Wrapper} from './mocks';
import {Diagrams} from '..';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

describe('Target Diagram', () => {
  it('should set correct targetProcessDefinitionKey', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    await processesStore.fetchProcesses();
    const {user} = render(<Diagrams />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /element mapping/i}));
    await user.click(screen.getByRole('combobox', {name: 'Target'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));

    // expect latest version selected by default
    expect(processInstanceMigrationStore.state.targetProcessDefinitionKey).toBe(
      'demoProcess3',
    );

    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    await user.click(screen.getByRole('combobox', {name: 'Target Version'}));
    await user.click(screen.getByRole('option', {name: '2'}));
    expect(processInstanceMigrationStore.state.targetProcessDefinitionKey).toBe(
      'demoProcess2',
    );

    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    await user.click(screen.getByRole('combobox', {name: 'Target Version'}));
    await user.click(screen.getByRole('option', {name: '1'}));
    expect(processInstanceMigrationStore.state.targetProcessDefinitionKey).toBe(
      'demoProcess1',
    );

    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    await user.click(screen.getByRole('combobox', {name: 'Target Version'}));
    await user.click(screen.getByRole('option', {name: '3'}));
    expect(processInstanceMigrationStore.state.targetProcessDefinitionKey).toBe(
      'demoProcess3',
    );
  });
});
