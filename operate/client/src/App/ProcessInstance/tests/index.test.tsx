/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {ProcessInstance} from '../index';
import {PAGE_TITLE} from 'modules/constants';
import {getWrapper, mockRequests} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {createVariable} from 'modules/testUtils';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';
import {getProcessDefinitionName} from 'modules/utils/instance';
import {storeStateLocally} from 'modules/utils/localStorage';

vi.mock('modules/utils/bpmn');

describe('ProcessInstance', () => {
  beforeEach(() => {
    storeStateLocally({hideProcessInstanceHelperModal: true});
    mockRequests();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should render and set the page title', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<ProcessInstance />, {wrapper: getWrapper()});
    expect(screen.queryByTestId('variables-skeleton')).not.toBeInTheDocument();
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-body')).toBeInTheDocument();
    expect(screen.getByText('Instance History')).toBeInTheDocument();
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('instance-header')).getByTestId(
        'INCIDENT-icon',
      ),
    ).toBeInTheDocument();

    expect(document.title).toBe(
      PAGE_TITLE.INSTANCE(
        mockProcessInstance.processInstanceKey,
        getProcessDefinitionName(mockProcessInstance),
      ),
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should display skeletons until instance is available', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    render(<ProcessInstance />, {wrapper: getWrapper()});

    vi.runOnlyPendingTimers();

    expect(screen.queryByTestId('diagram-spinner')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('instance-history-skeleton'),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('variables-skeleton')).not.toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should display forbidden content', async () => {
    mockFetchProcessInstance().withServerError(403);

    render(<ProcessInstance />, {wrapper: getWrapper()});

    expect(
      await screen.findByText(
        '403 - You do not have permission to view this information',
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText('Contact your administrator to get access.'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {name: 'Learn more about permissions'}),
    ).toHaveAttribute(
      'href',
      'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
    );
  });

  it('should display forbidden content after polling', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockFetchProcessInstance().withServerError(403);

    render(<ProcessInstance />, {wrapper: getWrapper()});

    vi.runOnlyPendingTimers();

    expect(
      await screen.findByText(
        '403 - You do not have permission to view this information',
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText('Contact your administrator to get access.'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {name: 'Learn more about permissions'}),
    ).toHaveAttribute(
      'href',
      'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should show helper modal on first load', async () => {
    localStorage.clear();
    render(<ProcessInstance />, {wrapper: getWrapper()});

    expect(
      screen.getByText("Here's what moved in Operate"),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /got it/i})).toBeInTheDocument();
  });

  it('should close helper modal when "Got it" is clicked', async () => {
    localStorage.clear();
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});

    expect(
      screen.getByText("Here's what moved in Operate"),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /got it/i}));

    expect(
      screen.queryByText("Here's what moved in Operate"),
    ).not.toBeInTheDocument();
  });

  it('should not show info modal when previously dismissed', async () => {
    storeStateLocally({hideProcessInstanceHelperModal: true});

    render(<ProcessInstance />, {wrapper: getWrapper()});

    expect(
      screen.queryByText("Here's what moved in Operate"),
    ).not.toBeInTheDocument();
  });
});
