/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {
  createProcessDefinition,
  mockProcessStatistics,
  mockProcessWithInputOutputMappingsXML,
  mockProcessXML,
  searchResult,
} from 'modules/testUtils';
import {TargetDiagram} from './TargetDiagram';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {Wrapper} from './tests/mocks';
import * as filterModule from 'modules/hooks/useProcessInstanceStatisticsFilters';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';

vi.mock('modules/hooks/useFilters');
vi.mock('modules/hooks/useProcessInstanceStatisticsFilters');

describe('Target Diagram', () => {
  beforeEach(() => {
    vi.spyOn(
      filterModule,
      'useProcessInstanceStatisticsFilters',
    ).mockReturnValue({filter: {}});

    processInstanceMigrationStore.enable();
    processInstanceMigrationStore.setSourceProcessDefinition(
      createProcessDefinition({
        processDefinitionKey: 'sourceKey',
        processDefinitionId: 'sourceId',
      }),
    );
  });

  it('should display initial state in the diagram header and diagram panel', async () => {
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    render(<TargetDiagram />, {wrapper: Wrapper});

    expect(screen.getByText('Target')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();
    expect(
      screen.getByRole('combobox', {
        name: 'Target Version',
      }),
    ).toBeDisabled();
    expect(
      screen.getByRole('combobox', {
        name: 'Target Version',
      }),
    ).toHaveTextContent('-');

    expect(
      screen.getByText('Select a target process and version'),
    ).toBeInTheDocument();
  });

  it('should render process and version components according to the step number', async () => {
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

    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Target'})).toBeEnabled(),
    );
    await user.click(screen.getByRole('button', {name: 'Element Mapping'}));
    await user.click(screen.getByRole('combobox', {name: 'Target'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));

    expect(
      screen.getByRole('combobox', {
        name: 'Target',
      }),
    ).toHaveValue('New demo process');
    expect(
      screen.getByRole('combobox', {
        name: 'Target Version',
      }),
    ).toHaveTextContent('3');
    expect(
      screen.getByRole('combobox', {
        name: 'Target Version',
      }),
    ).toBeEnabled();

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Summary'}));

    expect(
      screen.queryByRole('combobox', {
        name: 'Target',
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('combobox', {
        name: 'Target Version',
      }),
    ).not.toBeInTheDocument();

    expect(screen.queryByText('New demo process')).not.toBeInTheDocument();
    expect(screen.queryByText('3')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Element Mapping'}));

    expect(
      screen.getByRole('combobox', {
        name: 'Target',
      }),
    ).toHaveValue('New demo process');
    expect(
      screen.getByRole('combobox', {
        name: 'Target Version',
      }),
    ).toHaveTextContent('3');
  });

  it('should render diagram on selection and re-render on version change', async () => {
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

    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Target'})).toBeEnabled(),
    );
    await user.click(screen.getByRole('button', {name: 'Element Mapping'}));
    await user.click(screen.getByRole('combobox', {name: 'Target'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Reset diagram zoom'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Zoom in diagram'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Zoom out diagram'}),
    ).toBeInTheDocument();

    mockFetchProcessDefinitionXml().withDelay(
      mockProcessWithInputOutputMappingsXML,
    );

    await user.click(screen.getByRole('combobox', {name: 'Target Version'}));
    await user.click(screen.getByRole('option', {name: '2'}));

    expect(await screen.findByTestId('diagram-spinner')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('diagram-spinner'),
    );
  });

  it('should display error message on selection if diagram could not be fetched', async () => {
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
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
    mockFetchProcessDefinitionXml().withServerError();

    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Target'})).toBeEnabled(),
    );
    await user.click(screen.getByRole('button', {name: 'Element Mapping'}));
    await user.click(screen.getByRole('combobox', {name: 'Target'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should render element overlays', async () => {
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);

    processInstanceMigrationStore.setSourceProcessDefinition(
      createProcessDefinition({
        processDefinitionKey: '1',
        processDefinitionId: 'sourceId',
      }),
    );
    processInstanceMigrationStore.setTargetProcessDefinition(
      createProcessDefinition({
        processDefinitionKey: 'demoProcess3',
        processDefinitionId: 'demoProcess',
        name: 'New demo process',
        version: 3,
      }),
    );
    processInstanceMigrationStore.setBatchOperationQuery({ids: ['1']});
    processInstanceMigrationStore.setSelectedInstancesCount(1);

    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: 'map elements'}));

    expect(await screen.findByText(/diagram mock/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Summary'}));

    expect(
      await screen.findByTestId('modifications-overlay'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('modifications-overlay')).toHaveTextContent('1');

    await user.click(screen.getByRole('button', {name: 'Element Mapping'}));

    expect(await screen.findByText(/diagram mock/i)).toBeInTheDocument();
    expect(
      screen.queryByTestId('modifications-overlay'),
    ).not.toBeInTheDocument();
  });
});
