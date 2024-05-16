/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {mockApplyDeleteDefinitionOperation} from 'modules/mocks/api/decisions/operations';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {
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

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
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
        decisionVersion="2"
        decisionDefinitionId="2251799813687094"
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
        decisionVersion="2"
        decisionDefinitionId="2251799813687094"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);

    await user.click(
      await screen.findByLabelText(
        'Yes, I confirm I want to delete this DRD and all related instances.',
      ),
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperation]),
    );
    expect(panelStatesStore.state.isOperationsCollapsed).toBe(false);
  });

  it('should show notification on operation error', async () => {
    mockApplyDeleteDefinitionOperation().withServerError(500);

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion="2"
        decisionDefinitionId="2251799813687094"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);

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
    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);
  });

  it('should show notification on operation auth error', async () => {
    mockApplyDeleteDefinitionOperation().withServerError(403);

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion="2"
        decisionDefinitionId="2251799813687094"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);

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
        subtitle: 'You do not have permission',
        isDismissable: true,
      });
    });
    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);
  });

  it('should disable button and show spinner when delete operation is triggered', async () => {
    mockApplyDeleteDefinitionOperation().withSuccess(mockOperation);

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion="2"
        decisionDefinitionId="2251799813687094"
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
    expect(screen.getByTestId('delete-operation-spinner')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    ).toBeDisabled();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('delete-operation-spinner'),
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
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

    mockApplyDeleteDefinitionOperation().withNetworkError();

    const {user} = render(
      <DecisionOperations
        decisionName="myDecision"
        decisionVersion="2"
        decisionDefinitionId="2251799813687094"
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
    expect(screen.getByTestId('delete-operation-spinner')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    ).toBeDisabled();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('delete-operation-spinner'),
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
        decisionVersion="2"
        decisionDefinitionId="2251799813687094"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete decision definition "myDecision - version 2"$/i,
      }),
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    expect(
      await screen.findByText('Please tick this box if you want to proceed.'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /^close$/i}));

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
