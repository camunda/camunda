/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {render, screen, waitFor} from 'modules/testing-library';
import {mockVariables} from './index.setup';
import {getWrapper, mockProcessInstance} from './mocks';
import {notificationsStore} from 'modules/stores/notifications';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockUpdateElementInstanceVariables} from 'modules/mocks/api/v2/elementInstances/updateElementInstanceVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

describe('VariablePanel notifications', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
  });

  it('should display error notification if add variable operation could not be created', async () => {
    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /add variable/i,
        }),
      ).toBeEnabled(),
    );

    await user.click(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    );
    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'foo',
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '"bar"',
    );

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );

    mockUpdateElementInstanceVariables(':1').withDelayedServerError();
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    await user.click(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    );

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'error',
        subtitle: 'Internal Server Error',
        title: 'Variable could not be saved',
      }),
    );

    expect(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();
  });

  it('should display warning notification if add variable operation could not be created because of authorization error', async () => {
    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /add variable/i,
        }),
      ).toBeEnabled(),
    );

    await user.click(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    );
    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'foo',
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '"bar"',
    );

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );

    mockUpdateElementInstanceVariables(':1').withDelayedServerError(403);
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    await user.click(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    );

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'warning',
        title: "You don't have permission to perform this operation",
        subtitle: 'Please contact the administrator if you need access.',
      }),
    );

    expect(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();
  });
});
