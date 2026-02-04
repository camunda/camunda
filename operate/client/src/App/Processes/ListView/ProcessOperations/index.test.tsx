/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockDeleteResource} from 'modules/mocks/api/v2/resource/deleteResource';
import {operationsStore} from 'modules/stores/operations';
import {panelStatesStore} from 'modules/stores/panelStates';
import {
  fireEvent,
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {useEffect} from 'react';
import {ProcessOperations} from './index';
import {notificationsStore} from 'modules/stores/notifications';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const mockQueryClient = getMockQueryClient();

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      panelStatesStore.reset();
      operationsStore.reset();
    };
  }, []);

  return (
    <QueryClientProvider client={mockQueryClient}>
      {children}
    </QueryClientProvider>
  );
};

describe('<ProcessOperations />', () => {
  afterEach(() => {
    mockQueryClient.clear();
  });

  it('should open modal and show content', async () => {
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionKey="2251799813687094"
        processName="myProcess"
        processVersion={2}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      await screen.findByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    );

    expect(
      screen.getByText(
        /You are about to delete the following process definition:/,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/myProcess - Version 2/)).toBeInTheDocument();

    expect(
      await screen.findByText(
        /Deleting a process definition will permanently remove it and will impact the following:/i,
      ),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(
        /All the deleted process definition's finished process instances will be deleted from the application./i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /All decision and process instances referenced by the deleted process instances will be deleted./i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /If a process definition contains user tasks, they will be deleted from Tasklist./i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /For a detailed overview, please view our guide on deleting a process definition/i,
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        /Yes, I confirm I want to delete this process definition./i,
      ),
    ).toBeInTheDocument();
  });

  it('should apply delete definition operation', async () => {
    mockDeleteResource().withSuccess({});
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionKey="2251799813687094"
        processName="myProcess"
        processVersion={2}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      await screen.findByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    );

    await user.click(
      await screen.findByLabelText(
        /Yes, I confirm I want to delete this process definition./i,
      ),
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'success',
        title: 'Operation created',
        isDismissable: true,
      }),
    );
  });

  it('should show notification on operation error', async () => {
    mockDeleteResource().withServerError(500);
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionKey="2251799813687094"
        processName="myProcess"
        processVersion={2}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      await screen.findByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    );

    await user.click(
      await screen.findByLabelText(
        /Yes, I confirm I want to delete this process definition./i,
      ),
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Operation could not be created',
        isDismissable: true,
      });
    });
  });

  it('should show notification on operation auth error', async () => {
    mockDeleteResource().withServerError(403);
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionKey="2251799813687094"
        processName="myProcess"
        processVersion={2}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      await screen.findByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    );

    await user.click(
      await screen.findByLabelText(
        /Yes, I confirm I want to delete this process definition./i,
      ),
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'warning',
        title: "You don't have permission to perform this operation",
        subtitle: 'Please contact the administrator if you need access.',
        isDismissable: true,
      });
    });
  });

  it('should disable button and show spinner when delete operation is triggered', async () => {
    mockDeleteResource().withSuccess({});
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionKey="2251799813687094"
        processName="myProcess"
        processVersion={2}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      await screen.findByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    );

    await user.click(
      await screen.findByLabelText(
        /Yes, I confirm I want to delete this process definition./i,
      ),
    );

    fireEvent.click(screen.getByRole('button', {name: /danger Delete/}));
    expect(screen.getByTestId('delete-operation-spinner')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    ).toBeDisabled();
  });

  it('should enable button and remove spinner when delete operation failed', async () => {
    mockDeleteResource().withNetworkError();
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionKey="2251799813687094"
        processName="myProcess"
        processVersion={2}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      await screen.findByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    );

    await user.click(
      await screen.findByLabelText(
        /Yes, I confirm I want to delete this process definition./i,
      ),
    );

    fireEvent.click(screen.getByRole('button', {name: /danger Delete/}));
    expect(screen.getByTestId('delete-operation-spinner')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    ).toBeDisabled();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('delete-operation-spinner'),
    );

    expect(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    ).toBeEnabled();
  });

  it('should show warning when clicking apply without confirmation', async () => {
    mockDeleteResource().withSuccess({});
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionKey="2251799813687094"
        processName="myProcess"
        processVersion={2}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      await screen.findByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    expect(
      await screen.findByText('Please tick this box if you want to proceed.'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /close/i}));

    await user.click(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    );

    expect(
      screen.queryByText('Please tick this box if you want to proceed.'),
    ).not.toBeInTheDocument();
  });

  it('should initially disable the delete button', async () => {
    mockDeleteResource().withSuccess({});
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    render(
      <ProcessOperations
        processDefinitionKey="2251799813687094"
        processName="myProcess"
        processVersion={2}
      />,
      {wrapper: Wrapper},
    );

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /^delete process definition "myProcess - version 2"$/i,
        }),
      ).toBeEnabled(),
    );
  });

  it('should disable delete button when there are running instances', async () => {
    mockDeleteResource().withSuccess({});
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 1},
    });

    render(
      <ProcessOperations
        processDefinitionKey="2251799813687094"
        processName="myProcess"
        processVersion={2}
      />,
      {wrapper: Wrapper},
    );

    expect(
      await screen.findByRole('button', {
        name: 'Only process definitions without running instances can be deleted.',
      }),
    ).toBeDisabled();
  });

  it('should enable delete button when process instances could not be fetched', async () => {
    mockDeleteResource().withSuccess({});
    mockSearchProcessInstances().withServerError();

    render(
      <ProcessOperations
        processDefinitionKey="2251799813687094"
        processName="myProcess"
        processVersion={2}
      />,
      {wrapper: Wrapper},
    );

    await waitFor(
      () =>
        expect(
          screen.getByRole('button', {
            name: /^delete process definition "myProcess - version 2"$/i,
          }),
        ).toBeEnabled(),
      {timeout: 10000},
    );
  });

  it('should reset confirmation checkbox to unchecked when delete modal is closed and reopened', async () => {
    mockDeleteResource().withSuccess({});
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionKey="2251799813687094"
        processName="myProcess"
        processVersion={2}
      />,
      {wrapper: Wrapper},
    );

    const deleteButton = await screen.findByRole('button', {
      name: /^delete process definition "myProcess - version 2"$/i,
    });

    await user.click(deleteButton);

    const checkbox = await screen.findByLabelText(
      /Yes, I confirm I want to delete this process definition./i,
    );

    expect(checkbox).not.toBeChecked();

    await user.click(checkbox);

    expect(checkbox).toBeChecked();

    await user.click(screen.getByRole('button', {name: /close/i}));

    await user.click(deleteButton);

    expect(checkbox).not.toBeChecked();
  });
});
