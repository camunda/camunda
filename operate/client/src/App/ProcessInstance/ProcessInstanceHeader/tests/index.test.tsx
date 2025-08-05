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
import {
  mockInstance,
  mockInstanceWithParentInstance,
  mockOperationCreated,
  Wrapper,
  mockInstanceDeprecated,
} from './index.setup';

import {
  createUser,
  mockCallActivityProcessXML,
  mockProcessXML,
} from 'modules/testUtils';
import {panelStatesStore} from 'modules/stores/panelStates';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {notificationsStore} from 'modules/stores/notifications';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';
import {mockCancelProcessInstance} from 'modules/mocks/api/v2/processInstances/cancelProcessInstance';
import {mockMe} from 'modules/mocks/api/v2/me';

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
    // TODO: remove mockFetchProcessInstance once useHasActiveOperations is refactored https://github.com/camunda/camunda/issues/33512
    mockFetchProcessInstance().withSuccess(mockInstanceDeprecated);
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
    expect(screen.getByText(mockInstance.endDate ?? '--')).toBeInTheDocument();
    expect(screen.getByText('--')).toBeInTheDocument();
    expect(
      screen.getByTestId(`${mockInstance.state}-icon`),
    ).toBeInTheDocument();
    expect(screen.getByText('Process Name')).toBeInTheDocument();
    expect(screen.getByText('Process Instance Key')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();
    expect(screen.getByText('Start Date')).toBeInTheDocument();
    expect(screen.getByText('End Date')).toBeInTheDocument();
    expect(screen.getByText('Parent Process Instance Key')).toBeInTheDocument();
    expect(screen.getByText('Called Process Instances')).toBeInTheDocument();
    expect(screen.getAllByText('None').length).toBe(2);
    expect(
      screen.queryByRole('link', {name: /view all/i}),
    ).not.toBeInTheDocument();
  });

  it('should render "View All" link for call activity process', async () => {
    // TODO: remove mockFetchProcessInstance once useHasActiveOperations is refactored https://github.com/camunda/camunda/issues/33512
    mockFetchProcessInstance().withSuccess(mockInstanceDeprecated);
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

    // TODO: remove mockFetchProcessInstance once useHasActiveOperations is refactored https://github.com/camunda/camunda/issues/33512
    mockFetchProcessInstance().withSuccess(mockInstanceDeprecated);
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

  it('should render parent Process Instance Key', async () => {
    // TODO: remove mockFetchProcessInstance once useHasActiveOperations is refactored https://github.com/camunda/camunda/issues/33512
    mockFetchProcessInstance().withSuccess(mockInstanceDeprecated);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(
      <ProcessInstanceHeader
        processInstance={{
          ...mockInstance,
          parentProcessInstanceKey: '8724390842390124',
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(
      screen.getByRole('link', {
        description: `View parent instance ${mockInstanceWithParentInstance.parentProcessInstanceKey}`,
      }),
    ).toBeInTheDocument();
  });

  it('should show spinner when instance has active operations', async () => {
    // TODO: remove mockFetchProcessInstance once useHasActiveOperations is refactored https://github.com/camunda/camunda/issues/33512
    mockFetchProcessInstance().withSuccess(mockInstanceDeprecated);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(<ProcessInstanceHeader processInstance={mockInstance} />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should not show spinner when instance has no active operations', async () => {
    // TODO: remove mockFetchProcessInstance once useHasActiveOperations is refactored https://github.com/camunda/camunda/issues/33512
    mockFetchProcessInstance().withSuccess({
      ...mockInstanceDeprecated,
      operations: [],
    });
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(<ProcessInstanceHeader processInstance={mockInstance} />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
  });

  it('should show operation buttons for running process instance when user has permission', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceDeprecated);
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
  });

  it('should show operation buttons for finished process instance when user has permission', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceDeprecated);
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
    mockFetchProcessInstance().withSuccess(mockInstanceDeprecated);
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

    mockFetchProcessInstance().withSuccess(mockInstanceDeprecated);
    await user.click(screen.getByRole('button', {name: /Delete Instance/}));

    mockApplyOperation().withSuccess(mockOperationCreated);

    mockFetchProcessInstance().withSuccess(mockInstanceDeprecated);
    await user.click(screen.getByRole('button', {name: /danger delete/i}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'success',
        title: 'Instance deleted',
        isDismissable: true,
      }),
    );

    await waitFor(() => {
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    });
  });

  it('should show spinner on process instance cancellation', async () => {
    // TODO: remove mockFetchProcessInstance once useHasActiveOperations is refactored https://github.com/camunda/camunda/issues/33512
    mockFetchProcessInstance().withSuccess({
      ...mockInstanceDeprecated,
      operations: [],
    });
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockCancelProcessInstance().withSuccess({});

    const {user, rerender} = render(
      <ProcessInstanceHeader processInstance={mockInstance} />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    await user.click(screen.getByRole('button', {name: /cancel instance/i}));
    await user.click(screen.getByRole('button', {name: /apply/i}));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /cancel instance/i}),
    ).toBeDisabled();

    rerender(
      <ProcessInstanceHeader
        processInstance={{...mockInstance, state: 'TERMINATED'}}
      />,
    );

    await waitFor(() =>
      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument(),
    );
    expect(
      screen.queryByRole('button', {name: /cancel instance/i}),
    ).not.toBeInTheDocument();
  });
});
