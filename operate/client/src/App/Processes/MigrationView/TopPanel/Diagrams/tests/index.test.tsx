/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {
  createProcessDefinition,
  mockProcessXML,
  searchResult,
} from 'modules/testUtils';
import {Wrapper} from './mocks';
import {Diagrams} from '..';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';

describe('Target Diagram', () => {
  it('should set correct targetProcessDefinition', async () => {
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({
          processDefinitionKey: 'demoProcess3',
          processDefinitionId: 'demoProcess',
          name: 'New demo process',
          version: 3,
        }),
        createProcessDefinition({
          processDefinitionKey: 'demoProcess2',
          processDefinitionId: 'demoProcess',
          name: 'New demo process',
          version: 2,
        }),
        createProcessDefinition({
          processDefinitionKey: 'demoProcess1',
          processDefinitionId: 'demoProcess',
          name: 'New demo process',
          version: 1,
        }),
      ]),
    );
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({
          processDefinitionKey: 'demoProcess3',
          processDefinitionId: 'demoProcess',
          name: 'New demo process',
          version: 3,
        }),
      ]),
    );
    mockSearchProcessDefinitions().withSuccess(searchResult([]));

    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    processInstanceMigrationStore.setSourceProcessDefinition(
      createProcessDefinition({
        processDefinitionKey: 'sourceKey',
        processDefinitionId: 'sourceId',
      }),
    );
    const {user} = render(<Diagrams />, {wrapper: Wrapper});

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Target'})).toBeEnabled(),
    );
    await user.click(screen.getByRole('button', {name: 'Element Mapping'}));
    await user.click(screen.getByRole('combobox', {name: 'Target'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));

    expect(
      processInstanceMigrationStore.state.targetProcessDefinition
        ?.processDefinitionKey,
    ).toBe('demoProcess3');

    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    await waitFor(() =>
      expect(
        screen.getByRole('combobox', {name: 'Target Version'}),
      ).toBeEnabled(),
    );
    await user.click(screen.getByRole('combobox', {name: 'Target Version'}));
    await user.click(screen.getByRole('option', {name: '2'}));
    expect(
      processInstanceMigrationStore.state.targetProcessDefinition
        ?.processDefinitionKey,
    ).toBe('demoProcess2');

    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    await user.click(screen.getByRole('combobox', {name: 'Target Version'}));
    await user.click(screen.getByRole('option', {name: '1'}));
    expect(
      processInstanceMigrationStore.state.targetProcessDefinition
        ?.processDefinitionKey,
    ).toBe('demoProcess1');

    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    await user.click(screen.getByRole('combobox', {name: 'Target Version'}));
    await user.click(screen.getByRole('option', {name: '3'}));
    expect(
      processInstanceMigrationStore.state.targetProcessDefinition
        ?.processDefinitionKey,
    ).toBe('demoProcess3');
  });
});
