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
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {IncidentsWrapper} from '../index';
import {firstIncident, mockIncidents, secondIncident, Wrapper} from './mocks';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';
import {mockSearchIncidentsByProcessInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByProcessInstance';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {getIncidentErrorName} from 'modules/utils/incidents';
import {mockSearchIncidentsByElementInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByElementInstance';

describe('IncidentsWrapper', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstanceV2().withSuccess(mockProcessInstance);
    mockSearchProcessInstances().withSuccess({
      page: {totalItems: 1},
      items: [mockProcessInstance],
    });
  });

  it('should render the table', async () => {
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      mockIncidents,
    );

    incidentsPanelStore.setPanelOpen(true);
    render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );
    const table = within(screen.getByRole('table'));

    expect(table.getByText(/^Incident Type/)).toBeInTheDocument();
    expect(table.getByText(/^Failing Element/)).toBeInTheDocument();
    expect(table.getByText(/^Job Id/)).toBeInTheDocument();
    expect(table.getByText(/^Creation Date/)).toBeInTheDocument();
    expect(table.getByText(/^Error Message/)).toBeInTheDocument();
    expect(table.getByText(/^Operations/)).toBeInTheDocument();
  });

  it('should render incidents with filters applied', async () => {
    // Note: MSW's "http.use" prepends handlers! The actual matching order is reversed.
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess({
      page: {totalItems: 1},
      items: [firstIncident],
    });
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      mockIncidents,
    );

    incidentsPanelStore.setPanelOpen(true);
    const {user} = render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByText('Incidents - 2 results')).toBeInTheDocument();
    let table = within(screen.getByRole('table'));
    expect(
      table.getByText(getIncidentErrorName(firstIncident.errorType)),
    ).toBeInTheDocument();
    expect(
      table.getByText(getIncidentErrorName(secondIncident.errorType)),
    ).toBeInTheDocument();

    await user.click(
      await screen.findByRole('combobox', {name: /filter by incident type/i}),
    );
    await user.click(
      screen.getByRole('option', {
        name: 'Condition error.',
      }),
    );

    expect(await screen.findByText('Incidents - 1 result')).toBeInTheDocument();
    table = within(screen.getByRole('table'));
    expect(
      table.getByText(getIncidentErrorName(firstIncident.errorType)),
    ).toBeInTheDocument();
    expect(
      table.queryByText(getIncidentErrorName(secondIncident.errorType)),
    ).not.toBeInTheDocument();
  });

  it('should render incidents fetched for the process instance', async () => {
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess({
      page: {totalItems: 1},
      items: [firstIncident],
    });

    incidentsPanelStore.setPanelOpen(true);
    render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );
    const table = within(screen.getByRole('table'));

    expect(screen.getByText('Incidents - 1 result')).toBeInTheDocument();
    expect(
      table.getByText(getIncidentErrorName(firstIncident.errorType)),
    ).toBeInTheDocument();
  });

  it('should render incidents fetched for a element instance when scoped to it', async () => {
    mockSearchIncidentsByElementInstance(':elementInstanceId').withSuccess({
      page: {totalItems: 1},
      items: [secondIncident],
    });

    incidentsPanelStore.showIncidentsForElementInstance(
      secondIncident.elementInstanceKey,
      'second element',
    );
    incidentsPanelStore.setPanelOpen(true);
    const {unmount} = render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );
    const table = within(screen.getByRole('table'));

    expect(
      screen.getByText('Incidents - Filtered by "second element" - 1 result'),
    ).toBeInTheDocument();
    expect(
      table.getByText(getIncidentErrorName(secondIncident.errorType)),
    ).toBeInTheDocument();

    unmount(); // Unmount avoids React reacting to the global state cleanup
    incidentsPanelStore.clearSelection();
  });

  it('should render incidents filtered for an elementId when scoped to it', async () => {
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess({
      page: {totalItems: 1},
      items: [firstIncident],
    });

    incidentsPanelStore.showIncidentsForElementId(firstIncident.elementId);
    incidentsPanelStore.setPanelOpen(true);
    const {unmount} = render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );
    const table = within(screen.getByRole('table'));

    expect(
      screen.getByText(
        `Incidents - Filtered by "${firstIncident.elementId}" - 1 result`,
      ),
    ).toBeInTheDocument();
    expect(
      table.getByText(getIncidentErrorName(firstIncident.errorType)),
    ).toBeInTheDocument();

    unmount(); // Unmount avoids React reacting to the global state cleanup
    incidentsPanelStore.clearSelection();
  });
});
