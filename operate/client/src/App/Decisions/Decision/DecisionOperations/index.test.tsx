/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockApplyDeleteDefinitionOperation} from 'modules/mocks/api/decisions/operations';
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
import type {OperationEntity} from 'modules/types/operate';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const mockOperation: OperationEntity = {
  id: '2251799813687094',
  name: 'Delete MyDecisionDefinition - Version 1',
  type: 'DELETE_DECISION_DEFINITION',
  startDate: '2023-02-16T14:23:45.306+0100',
  endDate: null,
  instancesCount: 10,
  operationsTotalCount: 10,
  operationsFinishedCount: 0,
};

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
  return <>{children}</>;
};

describe('<DecisionOperations />', () => {
  it('should open modal and show content', async () => {
    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionDefinitionKey="2251799813687094"
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
    mockApplyDeleteDefinitionOperation().withSuccess(mockOperation);

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionDefinitionKey="2251799813687094"
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

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperation]),
    );
  });

  it('should show notification on operation error', async () => {
    mockApplyDeleteDefinitionOperation().withServerError(500);

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionDefinitionKey="2251799813687094"
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
    mockApplyDeleteDefinitionOperation().withServerError(403);

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionDefinitionKey="2251799813687094"
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
    mockApplyDeleteDefinitionOperation().withSuccess(mockOperation);

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionDefinitionKey="2251799813687094"
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

    mockApplyDeleteDefinitionOperation().withNetworkError();

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionDefinitionKey="2251799813687094"
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
    mockApplyDeleteDefinitionOperation().withServerError(500);

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion={2}
        decisionDefinitionKey="2251799813687094"
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
