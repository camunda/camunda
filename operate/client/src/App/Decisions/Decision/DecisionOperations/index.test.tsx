/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {
  fireEvent,
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {useEffect} from 'react';
import {DecisionOperations} from '.';
import {panelStatesStore} from 'modules/stores/panelStates';
import {operationsStore} from 'modules/stores/operations';
import {notificationsStore} from 'modules/stores/notifications';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter} from 'react-router-dom';
import {mockDeleteResource} from 'modules/mocks/api/v2/resource/deleteResource';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const mockQueryClient = getMockQueryClient();

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    decisionDefinitionStore.setDefinition({
      name: 'My Definition',
      id: 'myDefinition',
    });

    return () => {
      decisionDefinitionStore.reset();
      panelStatesStore.reset();
      operationsStore.reset();
    };
  }, []);

  return (
    <QueryClientProvider client={mockQueryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
};

describe('<DecisionOperations />', () => {
  afterEach(() => {
    mockQueryClient.clear();
  });

  it('should open modal and show content', async () => {
    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionRequirementsKey="2251799813687000"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    );

    expect(
      screen.getByText(/You are about to delete the following DRD:/),
    ).toBeInTheDocument();
    expect(screen.getByText(/My Definition/)).toBeInTheDocument();
    expect(
      screen.getByText(
        /Deleting a decision definition will delete the DRD and will impact the following/,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /^Yes, I confirm I want to delete this DRD and all related instances.$/,
      ),
    ).toBeInTheDocument();
  });

  it('should apply delete definition operation', async () => {
    mockDeleteResource().withSuccess({});

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionRequirementsKey="2251799813687000"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    );

    await user.click(
      await screen.findByLabelText(
        'Yes, I confirm I want to delete this DRD and all related instances.',
      ),
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'success',
        title: 'Operation created',
        isDismissable: true,
      });
    });
  });

  it('should show notification on operation error', async () => {
    mockDeleteResource().withServerError(500);

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionRequirementsKey="2251799813687000"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    );

    await user.click(
      await screen.findByLabelText(
        'Yes, I confirm I want to delete this DRD and all related instances.',
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

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionRequirementsKey="2251799813687000"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    );

    await user.click(
      await screen.findByLabelText(
        'Yes, I confirm I want to delete this DRD and all related instances.',
      ),
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'warning',
      title: "You don't have permission to perform this operation",
      subtitle: 'Please contact the administrator if you need access.',
      isDismissable: true,
    });
  });

  it('should disable button and show spinner when delete operation is triggered', async () => {
    mockDeleteResource().withSuccess({});

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionRequirementsKey="2251799813687000"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    );

    await user.click(
      await screen.findByLabelText(
        'Yes, I confirm I want to delete this DRD and all related instances.',
      ),
    );

    fireEvent.click(screen.getByRole('button', {name: /danger Delete/}));
    expect(screen.getByTestId('delete-operation-spinner')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    ).toBeDisabled();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('delete-operation-spinner'),
    );

    expect(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    ).toBeEnabled();
    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'success',
      title: 'Operation created',
      isDismissable: true,
    });
  });

  it('should enable button and remove spinner when delete operation failed', async () => {
    const consoleErrorMock = vi
      .spyOn(global.console, 'error')
      .mockImplementation(() => {});

    mockDeleteResource().withNetworkError();

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionRequirementsKey="2251799813687000"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    );

    await user.click(
      await screen.findByLabelText(
        'Yes, I confirm I want to delete this DRD and all related instances.',
      ),
    );

    fireEvent.click(screen.getByRole('button', {name: /danger Delete/}));
    expect(screen.getByTestId('delete-operation-spinner')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    ).toBeDisabled();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('delete-operation-spinner'),
    );

    expect(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    ).toBeEnabled();

    consoleErrorMock.mockRestore();
  });

  it('should show warning when clicking apply without confirmation', async () => {
    mockDeleteResource().withServerError(500);

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionRequirementsKey="2251799813687000"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    expect(
      await screen.findByText('Please tick this box if you want to proceed.'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /close/i}));

    await user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    );

    expect(
      screen.queryByText('Please tick this box if you want to proceed.'),
    ).not.toBeInTheDocument();
  });
});
