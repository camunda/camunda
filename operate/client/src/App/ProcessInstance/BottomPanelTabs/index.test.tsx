/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {
  createMemoryRouter,
  Navigate,
  RouterProvider,
  useLocation,
} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';
import {BottomPanelTabs} from './index';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {createProcessInstance, searchResult} from 'modules/testUtils';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockSearchIncidentsByProcessInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByProcessInstance';
import {mockSearchIncidentsByElementInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByElementInstance';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {LocationLog} from 'modules/utils/LocationLog';

const PROCESS_INSTANCE_ID = '123';

const RedirectToVariables: React.FC = () => {
  const location = useLocation();
  return (
    <Navigate to={{pathname: 'variables', search: location.search}} replace />
  );
};

function getWrapper(initialPath?: string) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    const router = createMemoryRouter(
      [
        {
          path: Paths.processInstance(undefined, true),
          element: children,
          children: [
            {index: true, element: <RedirectToVariables />},
            {
              path: Paths.processInstanceVariables({isRelative: true}),
              element: <LocationLog />,
            },
            {
              path: Paths.processInstanceDetails({isRelative: true}),
              element: <LocationLog />,
            },
            {
              path: Paths.processInstanceIncidents({isRelative: true}),
              element: <LocationLog />,
            },
            {
              path: Paths.processInstanceInputMappings({isRelative: true}),
              element: <LocationLog />,
            },
            {
              path: Paths.processInstanceOutputMappings({isRelative: true}),
              element: <LocationLog />,
            },
            {
              path: Paths.processInstanceListeners({isRelative: true}),
              element: <LocationLog />,
            },
            {
              path: Paths.processInstanceOperationsLog({isRelative: true}),
              element: <LocationLog />,
            },
            {
              path: Paths.processInstanceHistory({isRelative: true}),
              element: <LocationLog />,
            },
          ],
        },
      ],
      {
        initialEntries: [
          initialPath ??
            Paths.processInstanceVariables({
              processInstanceId: PROCESS_INSTANCE_ID,
            }),
        ],
      },
    );

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

const SELECTED_PATH = `${Paths.processInstance(PROCESS_INSTANCE_ID)}?elementId=someElement`;

describe('<BottomPanelTabs isHistoryTabVisible />', () => {
  it('should render always visible tabs', async () => {
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    render(<BottomPanelTabs isHistoryTabVisible />, {wrapper: getWrapper()});

    expect(screen.getByRole('link', {name: /^Variables$/i})).toBeVisible();
    expect(screen.getByRole('link', {name: /^Listeners$/i})).toBeVisible();
    expect(screen.getByRole('link', {name: /^Operations Log$/i})).toBeVisible();
  });

  it('should not show Details, Input Mappings, or Output Mappings when no element is selected', async () => {
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    render(<BottomPanelTabs isHistoryTabVisible />, {wrapper: getWrapper()});

    expect(screen.getByRole('link', {name: /^Variables$/i})).toBeVisible();
    expect(
      screen.queryByRole('link', {name: /^Details$/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: /^Input Mappings$/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: /^Output Mappings$/i}),
    ).not.toBeInTheDocument();
  });

  it('should show Details, Input Mappings, and Output Mappings when an element is selected', async () => {
    mockSearchElementInstances().withSuccess(searchResult([]));

    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(SELECTED_PATH),
    });

    expect(screen.getByRole('link', {name: /^Variables$/i})).toBeVisible();
    expect(screen.getByRole('link', {name: /^Details$/i})).toBeVisible();
    expect(screen.getByRole('link', {name: /^Input Mappings$/i})).toBeVisible();
    expect(
      screen.getByRole('link', {name: /^Output Mappings$/i}),
    ).toBeVisible();
  });

  it('should not show Incidents tab when process instance has no incident', async () => {
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    render(<BottomPanelTabs isHistoryTabVisible />, {wrapper: getWrapper()});

    expect(screen.getByRole('link', {name: /^Variables$/i})).toBeVisible();
    expect(
      screen.queryByRole('link', {name: /^Incidents$/i}),
    ).not.toBeInTheDocument();
  });

  it('should not show Incidents tab when selected element instance has no incidents', async () => {
    const elementInstanceKey = '456';
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: true,
      }),
    );
    mockFetchElementInstance(':key').withSuccess({
      elementInstanceKey,
      processInstanceKey: PROCESS_INSTANCE_ID,
      processDefinitionKey: '2223894723423800',
      processDefinitionId: 'someKey',
      startDate: '2024-01-01T00:00:00.000Z',
      endDate: null,
      state: 'ACTIVE',
      incidentKey: null,
      elementId: 'someElement',
      elementName: null,
      type: 'SERVICE_TASK',
      tenantId: '<default>',
      hasIncident: false,
      rootProcessInstanceKey: null,
    });
    mockSearchIncidentsByElementInstance(':key').withSuccess(
      searchResult([], 0),
    );

    const path = `${Paths.processInstance(PROCESS_INSTANCE_ID)}?elementId=someElement&elementInstanceKey=${elementInstanceKey}`;
    render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(path),
    });

    expect(screen.getByRole('link', {name: /^Variables$/i})).toBeVisible();
    expect(
      screen.queryByRole('link', {name: /^Incidents$/i}),
    ).not.toBeInTheDocument();
  });

  it('should show Incidents tab when process instance has an incident', async () => {
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      searchResult([], 3),
    );
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: true,
      }),
    );

    render(<BottomPanelTabs isHistoryTabVisible />, {wrapper: getWrapper()});

    expect(
      await screen.findByRole('link', {name: /^Incidents$/i}),
    ).toBeVisible();
  });

  it('should render all tabs in correct order when all are visible', async () => {
    mockSearchElementInstances().withSuccess(searchResult([]));
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      searchResult([], 2),
    );

    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: true,
      }),
    );

    render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(SELECTED_PATH),
    });

    expect(
      await screen.findByRole('link', {name: /^Incidents$/i}),
    ).toBeVisible();

    const tabs = screen.getAllByRole('link');
    expect(tabs).toHaveLength(8);
    expect(tabs[0]).toHaveAccessibleName('Incidents');
    expect(tabs[1]).toHaveAccessibleName('Details');
    expect(tabs[2]).toHaveAccessibleName('Variables');
    expect(tabs[3]).toHaveAccessibleName('Input Mappings');
    expect(tabs[4]).toHaveAccessibleName('Output Mappings');
    expect(tabs[5]).toHaveAccessibleName('Listeners');
    expect(tabs[6]).toHaveAccessibleName('Operations Log');
    expect(tabs[7]).toHaveAccessibleName('Instance History');
  });

  it('should navigate to the correct route when clicking always visible tabs', async () => {
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    const {user} = render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceVariables({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );

    await user.click(screen.getByRole('link', {name: /^Listeners$/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceListeners({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );

    await user.click(screen.getByRole('link', {name: /^Operations Log$/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceOperationsLog({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );

    await user.click(screen.getByRole('link', {name: /^Variables$/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceVariables({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );
  });

  it('should navigate to the correct route when clicking element-dependent tabs', async () => {
    mockSearchElementInstances().withSuccess(searchResult([]));

    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    const {user} = render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(SELECTED_PATH),
    });

    await user.click(screen.getByRole('link', {name: /^Details$/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceDetails({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );

    await user.click(screen.getByRole('link', {name: /^Input Mappings$/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceInputMappings({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );

    await user.click(screen.getByRole('link', {name: /^Output Mappings$/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceOutputMappings({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );
  });

  it('should navigate to the Incidents route when clicking the Incidents tab', async () => {
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      searchResult([], 1),
    );
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: true,
      }),
    );

    const {user} = render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(),
    });

    const incidentsTab = await screen.findByRole('link', {
      name: /^Incidents$/i,
    });

    await user.click(incidentsTab);

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceIncidents({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );
  });

  it('should navigate to the Instance History route when clicking the Instance History tab', async () => {
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    const {user} = render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(),
    });

    const instanceHistoryTab = screen.getByRole('link', {
      name: /^Instance History$/i,
    });

    await user.click(instanceHistoryTab);

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceHistory({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );
  });

  it('should not show Instance History tab (large screen)', async () => {
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    render(<BottomPanelTabs isHistoryTabVisible={false} />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByRole('link', {name: /^Variables$/i})).toBeVisible();
    expect(
      screen.queryByRole('link', {name: /^Instance History$/i}),
    ).not.toBeInTheDocument();
  });

  it('should preserve search params when navigating between tabs', async () => {
    mockSearchElementInstances().withSuccess(searchResult([]));

    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    const {user} = render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(SELECTED_PATH),
    });

    expect(screen.getByTestId('search')).toHaveTextContent(
      '?elementId=someElement',
    );

    await user.click(screen.getByRole('link', {name: /^Listeners$/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceListeners({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );
    expect(screen.getByTestId('search')).toHaveTextContent(
      '?elementId=someElement',
    );

    await user.click(screen.getByRole('link', {name: /^Variables$/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceVariables({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );
    expect(screen.getByTestId('search')).toHaveTextContent(
      '?elementId=someElement',
    );
  });

  it('should mark the navigated-to tab as selected', async () => {
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    const {user} = render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByRole('link', {name: /^Variables$/i})).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(
      screen.getByRole('link', {name: /^Listeners$/i}),
    ).not.toHaveAttribute('aria-current');

    await user.click(screen.getByRole('link', {name: /^Listeners$/i}));

    expect(screen.getByRole('link', {name: /^Listeners$/i})).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(
      screen.getByRole('link', {name: /^Variables$/i}),
    ).not.toHaveAttribute('aria-current');
  });

  it('should display incident count badge on the Incidents tab', async () => {
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      searchResult([], 5),
    );
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: true,
      }),
    );

    render(<BottomPanelTabs isHistoryTabVisible />, {wrapper: getWrapper()});

    const incidentsTab = await screen.findByRole('link', {
      name: /^Incidents$/i,
    });
    expect(incidentsTab).toBeVisible();
    expect(await screen.findByText('5')).toBeVisible();
  });

  it('should display an incident count filtered to a selected element instance', async () => {
    const elementInstanceKey = '456';
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: true,
      }),
    );
    mockFetchElementInstance(':key').withSuccess({
      elementInstanceKey,
      processInstanceKey: PROCESS_INSTANCE_ID,
      processDefinitionKey: '2223894723423800',
      processDefinitionId: 'someKey',
      startDate: '2024-01-01T00:00:00.000Z',
      endDate: null,
      state: 'ACTIVE',
      incidentKey: null,
      elementId: 'someElement',
      elementName: null,
      type: 'SERVICE_TASK',
      tenantId: '<default>',
      hasIncident: true,
      rootProcessInstanceKey: null,
    });
    mockSearchIncidentsByElementInstance(':key').withSuccess(
      searchResult([], 2),
    );

    const path = `${Paths.processInstance(PROCESS_INSTANCE_ID)}?elementId=someElement&elementInstanceKey=${elementInstanceKey}`;
    render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(path),
    });

    expect(
      await screen.findByRole('link', {name: /^Incidents$/i}),
    ).toBeVisible();
    expect(await screen.findByText('2')).toBeVisible();
  });

  it('should display an incident count filtered to selected element when no single instance is selected', async () => {
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: true,
      }),
    );
    mockSearchElementInstances().withSuccess(
      searchResult(
        [
          {
            elementInstanceKey: '1001',
            processInstanceKey: PROCESS_INSTANCE_ID,
            processDefinitionKey: '2223894723423800',
            processDefinitionId: 'someKey',
            startDate: '2024-01-01T00:00:00.000Z',
            endDate: null,
            state: 'ACTIVE',
            incidentKey: null,
            elementId: 'multiElement',
            elementName: null,
            type: 'SERVICE_TASK',
            tenantId: '<default>',
            hasIncident: true,
            rootProcessInstanceKey: null,
          },
          {
            elementInstanceKey: '1002',
            processInstanceKey: PROCESS_INSTANCE_ID,
            processDefinitionKey: '2223894723423800',
            processDefinitionId: 'someKey',
            startDate: '2024-01-01T00:00:00.000Z',
            endDate: null,
            state: 'ACTIVE',
            incidentKey: null,
            elementId: 'multiElement',
            elementName: null,
            type: 'SERVICE_TASK',
            tenantId: '<default>',
            hasIncident: true,
            rootProcessInstanceKey: null,
          },
        ],
        2,
      ),
    );
    mockSearchIncidentsByProcessInstance(':key').withSuccess(
      searchResult([], 3),
    );

    const selectedPath = `${Paths.processInstance(PROCESS_INSTANCE_ID)}?elementId=multiElement`;
    render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(selectedPath),
    });

    expect(
      await screen.findByRole('link', {name: /^Incidents$/i}),
    ).toBeVisible();
    expect(await screen.findByText('3')).toBeVisible();
  });

  it('should redirect incidents tab when process instance has no incidents', async () => {
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    const path = Paths.processInstanceIncidents({
      processInstanceId: PROCESS_INSTANCE_ID,
    });
    render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(path),
    });

    expect(await screen.findByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceVariables({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );
  });

  it('should redirect incidents tab when selected element has no incidents', async () => {
    const elementInstanceKey = '456';
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: true,
      }),
    );
    mockFetchElementInstance(':key').withSuccess({
      elementInstanceKey,
      processInstanceKey: PROCESS_INSTANCE_ID,
      processDefinitionKey: '2223894723423800',
      processDefinitionId: 'someKey',
      startDate: '2024-01-01T00:00:00.000Z',
      endDate: null,
      state: 'ACTIVE',
      incidentKey: null,
      elementId: 'someElement',
      elementName: null,
      type: 'SERVICE_TASK',
      tenantId: '<default>',
      hasIncident: false,
      rootProcessInstanceKey: null,
    });
    mockSearchIncidentsByElementInstance(':key').withSuccess(
      searchResult([], 0),
    );

    const path = `${Paths.processInstanceIncidents({processInstanceId: PROCESS_INSTANCE_ID})}?elementId=someElement&elementInstanceKey=${elementInstanceKey}`;
    render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(path),
    });

    expect(await screen.findByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceVariables({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );
  });

  it('should redirect input mappings tab when no element is selected', async () => {
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    const inputMappingsPath = Paths.processInstanceInputMappings({
      processInstanceId: PROCESS_INSTANCE_ID,
    });
    render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(inputMappingsPath),
    });

    expect(await screen.findByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceVariables({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );
  });

  it('should redirect output mappings tab when no element is selected', async () => {
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    const outputMappingsPath = Paths.processInstanceOutputMappings({
      processInstanceId: PROCESS_INSTANCE_ID,
    });
    render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(outputMappingsPath),
    });

    expect(await screen.findByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceVariables({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );
  });

  it('should redirect details tab when no element is selected', async () => {
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
        hasIncident: false,
      }),
    );

    const path = Paths.processInstanceDetails({
      processInstanceId: PROCESS_INSTANCE_ID,
    });
    render(<BottomPanelTabs isHistoryTabVisible />, {
      wrapper: getWrapper(path),
    });

    expect(await screen.findByTestId('pathname')).toHaveTextContent(
      Paths.processInstanceVariables({
        processInstanceId: PROCESS_INSTANCE_ID,
      }),
    );
  });
});
