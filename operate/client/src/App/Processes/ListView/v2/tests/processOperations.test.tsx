/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {ListView} from '..';
import {getWrapper} from './mocks';
import {
  mockProcessDefinitions,
  mockProcessXML,
  createUser,
  mockProcessInstancesV2,
  searchResult,
  createProcessDefinition,
} from 'modules/testUtils';
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {mockApplyProcessDefinitionOperation} from 'modules/mocks/api/processes/operations';
import {notificationsStore} from 'modules/stores/notifications';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

describe('<ListView /> - operations', () => {
  beforeEach(() => {
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({
          processDefinitionId: 'demoProcess',
          processDefinitionKey: 'demoProcess1',
          name: 'New demo process',
          version: 1,
        }),
      ]),
    );
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockFetchProcessInstancesStatistics().withSuccess({
      items: [],
    });
    mockMe().withSuccess(createUser());
  });

  it('should show delete button when version is selected', async () => {
    mockSearchProcessInstances().withSuccess(mockProcessInstancesV2);
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const queryString =
      '?active=true&incidents=true&process=demoProcess&version=1';

    render(<ListView />, {
      wrapper: getWrapper(`/processes${queryString}`),
    });

    expect(
      await screen.findByRole('heading', {name: 'New demo process'}),
    ).toBeInTheDocument();

    expect(
      await screen.findByRole('heading', {
        name: 'Process Instances',
      }),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {
        name: /^delete process definition "new demo process - version 1"$/i,
      }),
    ).toBeInTheDocument();
  });

  it('should not show delete button when no process is selected', async () => {
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    render(<ListView />, {
      wrapper: getWrapper('/processes'),
    });

    expect(
      await screen.findByText('There is no Process selected'),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('heading', {
        name: 'Process Instances',
      }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when no version is selected', async () => {
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const queryString = '?active=true&incidents=true&process=demoProcess';

    render(<ListView />, {
      wrapper: getWrapper(`/processes${queryString}`),
    });

    expect(
      await screen.findByRole('heading', {name: 'New demo process'}),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('heading', {
        name: 'Process Instances',
      }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should show permission error notification when delete operation returns 403', async () => {
    mockSearchProcessInstances().withSuccess(mockProcessInstancesV2);
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockApplyProcessDefinitionOperation().withServerError(403);

    const {user} = render(<ListView />, {
      wrapper: getWrapper('/processes?process=demoProcess&version=1'),
    });

    expect(
      await screen.findByRole('button', {
        name: /delete process definition/i,
      }),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: /delete process definition/i}),
    );

    const confirmCheckbox = await screen.findByLabelText(
      /Yes, I confirm I want to delete this process definition/i,
    );

    await user.click(confirmCheckbox);

    await screen.findByRole('button', {name: 'Cancel'});

    const allButtons = screen.getAllByRole('button');
    const deleteButtons = allButtons.filter((button) =>
      button.textContent?.includes('Delete'),
    );
    const deleteButton = deleteButtons[deleteButtons.length - 1];

    await user.click(deleteButton);

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'warning',
      title: "You don't have permission to perform this operation",
      subtitle: 'Please contact the administrator if you need access.',
      isDismissable: true,
    });
  });
});
