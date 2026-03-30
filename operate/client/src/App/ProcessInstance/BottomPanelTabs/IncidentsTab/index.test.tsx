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
import {IncidentsTab} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchIncidentsByProcessInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByProcessInstance';
import {mockSearchIncidentsByElementInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByElementInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';
import {createIncident, searchResult} from 'modules/testUtils';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {getIncidentErrorName} from 'modules/utils/incidents';

const PROCESS_INSTANCE_ID = mockProcessInstance.processInstanceKey;

const firstIncident = createIncident({
  errorType: 'CONDITION_ERROR',
  creationTime: '2022-03-01T14:26:19',
  elementId: 'flowNodeId_exclusiveGateway',
});

const secondIncident = createIncident({
  errorType: 'EXTRACT_VALUE_ERROR',
  elementId: 'flowNodeId_alwaysFailingTask',
});

function getWrapper(searchParams?: Record<string, string>) {
  const params = new URLSearchParams(searchParams);
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter
        initialEntries={[
          `${Paths.processInstanceIncidents({processInstanceId: PROCESS_INSTANCE_ID})}?${params.toString()}`,
        ]}
      >
        <Routes>
          <Route path={Paths.processInstanceIncidents()} element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  return Wrapper;
}

describe('IncidentsTab', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockSearchProcessInstances().withSuccess(
      searchResult([mockProcessInstance]),
    );
  });

  afterEach(() => {
    incidentsPanelStore.clearSelection();
  });

  it('should render the incidents table with correct columns', async () => {
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      searchResult([firstIncident, secondIncident]),
    );

    render(<IncidentsTab />, {wrapper: getWrapper()});

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );

    const table = within(screen.getByRole('table'));
    expect(table.getByText(/^Type/)).toBeInTheDocument();
    expect(table.getByText(/^Failing Element/)).toBeInTheDocument();
    expect(table.getByText(/^Created/)).toBeInTheDocument();
    expect(table.getByText(/^Operations/)).toBeInTheDocument();
  });

  it('should render incident rows', async () => {
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      searchResult([firstIncident, secondIncident]),
    );

    render(<IncidentsTab />, {wrapper: getWrapper()});

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );

    const table = within(screen.getByRole('table'));
    expect(
      table.getByText(getIncidentErrorName(firstIncident.errorType)),
    ).toBeInTheDocument();
    expect(
      table.getByText(getIncidentErrorName(secondIncident.errorType)),
    ).toBeInTheDocument();
  });

  it('should show the result count in the panel header', async () => {
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      searchResult([firstIncident, secondIncident]),
    );

    render(<IncidentsTab />, {wrapper: getWrapper()});

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByText(/2 results/)).toBeInTheDocument();
  });

  it('should show skeleton state while loading', () => {
    mockSearchIncidentsByProcessInstance(':instanceId').withServerError();

    render(<IncidentsTab />, {wrapper: getWrapper()});

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();
  });

  it('should render incidents filtered by error type', async () => {
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      searchResult([firstIncident]),
    );
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      searchResult([firstIncident, secondIncident]),
    );

    const {user} = render(<IncidentsTab />, {wrapper: getWrapper()});

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByText(/2 results/)).toBeInTheDocument();
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

    expect(await screen.findByText(/1 result/)).toBeInTheDocument();
    table = within(screen.getByRole('table'));
    expect(
      table.getByText(getIncidentErrorName(firstIncident.errorType)),
    ).toBeInTheDocument();
    expect(
      table.queryByText(getIncidentErrorName(secondIncident.errorType)),
    ).not.toBeInTheDocument();
  });

  it('should fetch incidents by element instance when scoped to an element', async () => {
    mockSearchIncidentsByElementInstance(':elementInstanceId').withSuccess(
      searchResult([secondIncident]),
    );
    mockSearchElementInstances().withSuccess({
      items: [
        {
          elementInstanceKey: secondIncident.elementInstanceKey,
          processInstanceKey: PROCESS_INSTANCE_ID,
          processDefinitionKey: mockProcessInstance.processDefinitionKey,
          processDefinitionId: mockProcessInstance.processDefinitionId,
          startDate: '2024-01-01T00:00:00.000Z',
          endDate: null,
          state: 'ACTIVE',
          incidentKey: secondIncident.incidentKey,
          elementId: secondIncident.elementId,
          elementName: null,
          type: 'SERVICE_TASK',
          tenantId: '<default>',
          hasIncident: true,
          rootProcessInstanceKey: null,
        },
      ],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<IncidentsTab />, {
      wrapper: getWrapper({elementId: secondIncident.elementId}),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );

    const table = within(screen.getByRole('table'));
    expect(
      table.getByText(getIncidentErrorName(secondIncident.errorType)),
    ).toBeInTheDocument();
  });

  it('should show empty state when no incidents match', async () => {
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      searchResult([]),
    );

    render(<IncidentsTab />, {wrapper: getWrapper()});

    expect(
      await screen.findByText(
        'There are no incidents matching this filter set',
      ),
    ).toBeInTheDocument();
  });
});
