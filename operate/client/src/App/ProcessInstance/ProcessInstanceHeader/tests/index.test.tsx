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
  waitFor,
} from 'modules/testing-library';
import {getProcessDefinitionName} from 'modules/utils/instance';
import {ProcessInstanceHeader} from '../index';
import {mockInstance, Wrapper} from './index.setup';

import {
  createIncident,
  createUser,
  mockCallActivityProcessXML,
  mockProcessXML,
  searchResult,
} from 'modules/testUtils';
import {panelStatesStore} from 'modules/stores/panelStates';
import {notificationsStore} from 'modules/stores/notifications';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockDeleteProcessInstance} from 'modules/mocks/api/v2/processInstances/deleteProcessInstance';
import {mockSearchIncidentsByProcessInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByProcessInstance';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

describe('InstanceHeader', () => {
  beforeEach(() => {
    mockFetchCallHierarchy().withSuccess([]);
    mockMe().withSuccess(createUser());
  });

  it('should render process instance data', async () => {
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(<ProcessInstanceHeader processInstance={mockInstance} />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    const processDefinitionName = getProcessDefinitionName(mockInstance);

    expect(screen.getByText(processDefinitionName)).toBeInTheDocument();
    expect(
      screen.getByText(mockInstance.processInstanceKey),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        description: `View process "${processDefinitionName} version ${
          mockInstance.processDefinitionVersion
        }" instances`,
      }),
    ).toHaveTextContent(mockInstance.processDefinitionVersion.toString());
    expect(
      screen.getByTestId(`${mockInstance.state}-icon`),
    ).toBeInTheDocument();
    expect(screen.getByText('Process Instance Key')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();
    expect(screen.getByText('Start Date')).toBeInTheDocument();
    expect(screen.queryByText('End Date')).not.toBeInTheDocument();
    expect(screen.getByText('Called Instances')).toBeInTheDocument();
    expect(screen.getAllByText('None').length).toBe(1);
    expect(
      screen.queryByRole('link', {name: /view all/i}),
    ).not.toBeInTheDocument();
  });

  it('should render an end date for finished process instances', async () => {
    const finishedInstance = {
      ...mockInstance,
      state: 'COMPLETED',
      endDate: '2020-06-21 00:00:00',
    } satisfies typeof mockInstance;
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(<ProcessInstanceHeader processInstance={finishedInstance} />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(screen.getByText('End Date')).toBeInTheDocument();
    expect(screen.getByText(finishedInstance.endDate)).toBeInTheDocument();
  });

  it('should render an incidents count for process instances with incidents', async () => {
    const failedInstance = {
      ...mockInstance,
      state: 'ACTIVE',
      hasIncident: true,
    } satisfies typeof mockInstance;
    mockSearchIncidentsByProcessInstance(
      failedInstance.processInstanceKey,
    ).withSuccess(searchResult([createIncident(), createIncident()]));
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(<ProcessInstanceHeader processInstance={failedInstance} />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('2 incidents')).toBeInTheDocument();
    expect(screen.getByTestId(`INCIDENT-icon`)).toBeInTheDocument();
    expect(
      screen.getByText(failedInstance.processDefinitionName),
    ).toBeInTheDocument();
  });

  it('should not render an incidents count after incidents are resolved', async () => {
    const failedInstance = {
      ...mockInstance,
      state: 'ACTIVE',
      hasIncident: true,
    } satisfies typeof mockInstance;
    mockSearchIncidentsByProcessInstance(
      failedInstance.processInstanceKey,
    ).withSuccess(searchResult([createIncident(), createIncident()]));
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {rerender} = render(
      <ProcessInstanceHeader processInstance={failedInstance} />,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByText('2 incidents')).toBeInTheDocument();
    expect(screen.getByTestId(`INCIDENT-icon`)).toBeInTheDocument();

    const resolvedInstance = {
      ...mockInstance,
      state: 'ACTIVE',
      hasIncident: false,
    } satisfies typeof mockInstance;

    rerender(<ProcessInstanceHeader processInstance={resolvedInstance} />);

    expect(screen.queryByText('2 incidents')).not.toBeInTheDocument();
    expect(screen.queryByTestId(`INCIDENT-icon`)).not.toBeInTheDocument();
    expect(screen.getByTestId(`ACTIVE-icon`)).toBeInTheDocument();
  });

  it('should render "View All" link for call activity process', async () => {
    mockFetchProcessDefinitionXml().withSuccess(mockCallActivityProcessXML);

    render(<ProcessInstanceHeader processInstance={mockInstance} />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(
      await screen.findByRole('link', {name: /view all/i}),
    ).toBeInTheDocument();
  });

  it('should navigate to Instances Page and expand Filters Panel on "View All" click', async () => {
    panelStatesStore.toggleFiltersPanel();

    mockFetchProcessDefinitionXml().withSuccess(mockCallActivityProcessXML);

    const {user} = render(
      <ProcessInstanceHeader processInstance={mockInstance} />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/processes\/1$/,
    );
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    await user.click(await screen.findByRole('link', {name: /view all/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should show operation buttons for running process instances', async () => {
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(<ProcessInstanceHeader processInstance={mockInstance} />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Modify Instance/}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Migrate Instance/}),
    ).toBeInTheDocument();
  });

  it('should show operation buttons for finished process instances', async () => {
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(
      <ProcessInstanceHeader
        processInstance={{...mockInstance, state: 'TERMINATED'}}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(
      screen.getByRole('button', {name: /Delete Instance/}),
    ).toBeInTheDocument();
  });

  it('should redirect and show notification when "Delete Instance" is clicked', async () => {
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(
      <ProcessInstanceHeader
        processInstance={{...mockInstance, state: 'TERMINATED'}}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    await user.click(screen.getByRole('button', {name: /Delete Instance/}));

    mockDeleteProcessInstance().withSuccess(null, {expectPolling: false});

    await user.click(screen.getByRole('button', {name: /danger delete/i}));

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'info',
      title: 'Instance is scheduled for deletion',
      isDismissable: true,
    });
    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/),
    );
  });

  it('should configure the migration store and redirect when "Migrate Instance" is clicked', async () => {
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(
      <ProcessInstanceHeader processInstance={mockInstance} />,
      {
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    await user.click(screen.getByRole('button', {name: /Migrate Instance/}));
    await user.click(screen.getByRole('button', {name: 'Continue'}));

    expect(processInstanceMigrationStore.isEnabled).toBe(true);
    expect(processInstanceMigrationStore.state.selectedInstancesCount).toBe(1);
    expect(processInstanceMigrationStore.state.batchOperationQuery).toEqual({
      ids: [mockInstance.processInstanceKey],
    });
    expect(processInstanceMigrationStore.state.sourceProcessDefinition).toEqual(
      {
        processDefinitionKey: mockInstance.processDefinitionKey,
        processDefinitionId: mockInstance.processDefinitionId,
        version: mockInstance.processDefinitionVersion,
        versionTag: mockInstance.processDefinitionVersionTag,
        name: mockInstance.processDefinitionName,
        tenantId: mockInstance.tenantId,
        resourceName: null,
        hasStartForm: false,
      },
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);

    const search = new URLSearchParams(
      screen.getByTestId('search').textContent ?? '',
    );

    expect(search.get('active')).toBe('true');
    expect(search.get('incidents')).toBe('true');
    expect(search.get('processDefinitionId')).toBe(
      mockInstance.processDefinitionId,
    );
    expect(search.get('processDefinitionVersion')).toBe(
      mockInstance.processDefinitionVersion.toString(),
    );
    expect(search.get('tenantId')).toBe(mockInstance.tenantId);
  });

  it('should enable modification mode when "Modify Instance" is clicked', async () => {
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(
      <ProcessInstanceHeader processInstance={mockInstance} />,
      {
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    await user.click(screen.getByRole('button', {name: /Modify Instance/}));
    await user.click(screen.getByRole('button', {name: 'Continue'}));

    expect(modificationsStore.isModificationModeEnabled).toBe(true);
    await waitFor(() =>
      expect(
        screen.queryByRole('button', {name: /Modify Instance/}),
      ).not.toBeInTheDocument(),
    );
  });
});
