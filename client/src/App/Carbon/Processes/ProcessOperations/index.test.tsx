/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mockApplyProcessDefinitionOperation} from 'modules/mocks/api/processes/operations';
import {operationsStore} from 'modules/stores/operations';
import {panelStatesStore} from 'modules/stores/panelStates';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {useEffect} from 'react';
import {ProcessOperations} from '.';

const mockDisplayNotification = jest.fn();

jest.mock('modules/notifications', () => {
  return {
    useNotifications: () => {
      return {displayNotification: mockDisplayNotification};
    },
  };
});

const mockOperation: OperationEntity = {
  id: '2251799813687094',
  name: 'myProcess - Version 2',
  type: 'DELETE_PROCESS_DEFINITION',
  startDate: '2023-02-16T14:23:45.306+0100',
  endDate: null,
  instancesCount: 10,
  operationsTotalCount: 10,
  operationsFinishedCount: 0,
};

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      panelStatesStore.reset();
      operationsStore.reset();
    };
  }, []);

  return <ThemeProvider>{children}</ThemeProvider>;
};

describe('<ProcessOperations />', () => {
  it('should open modal and show content', async () => {
    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
      />,
      {wrapper: Wrapper}
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    );

    expect(
      screen.getByText(
        /You are about to delete the following process definition:/
      )
    ).toBeInTheDocument();
    expect(screen.getByText(/myProcess - Version 2/)).toBeInTheDocument();

    expect(
      screen.getByText(
        /Deleting a process definition will permanently remove it and will impact the following:/i
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /All the deleted process definition’s running process instances will be immediately canceled and deleted./i
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /All the deleted process definition’s finished process instances will be deleted from the application./i
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /All decision and process instances referenced by the deleted process instances will be deleted./i
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /If a process definition contains user tasks, they will be canceled and deleted from Tasklist./i
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /For a detailed overview, please view our guide on deleting a process definition/i
      )
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        /Yes, I confirm I want to delete this process definition./i
      )
    ).toBeInTheDocument();
  });

  it('should apply delete definition operation', async () => {
    mockApplyProcessDefinitionOperation().withSuccess(mockOperation);

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
      />,
      {wrapper: Wrapper}
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);

    await user.click(
      await screen.findByLabelText(
        /Yes, I confirm I want to delete this process definition./i
      )
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperation])
    );
    expect(panelStatesStore.state.isOperationsCollapsed).toBe(false);
  });

  it('should show notification on operation error', async () => {
    mockApplyProcessDefinitionOperation().withServerError(500);

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
      />,
      {wrapper: Wrapper}
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);

    await user.click(
      await screen.findByLabelText(
        /Yes, I confirm I want to delete this process definition./i
      )
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    await waitFor(() => {
      expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
        headline: 'Operation could not be created',
      });
    });
    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);
  });

  it('should show notification on operation auth error', async () => {
    mockApplyProcessDefinitionOperation().withServerError(403);

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
      />,
      {wrapper: Wrapper}
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);

    await user.click(
      await screen.findByLabelText(
        /Yes, I confirm I want to delete this process definition./i
      )
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    await waitFor(() => {
      expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
        headline: 'Operation could not be created',
        description: 'You do not have permission',
      });
    });
    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);
  });

  it('should disable button and show spinner when delete operation is triggered', async () => {
    mockApplyProcessDefinitionOperation().withSuccess(mockOperation);

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
      />,
      {wrapper: Wrapper}
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    );

    await user.click(
      await screen.findByLabelText(
        /Yes, I confirm I want to delete this process definition./i
      )
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));
    expect(
      await screen.findByTestId('delete-operation-spinner')
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    ).toBeDisabled();
  });

  it('should enable button and remove spinner when delete operation failed', async () => {
    mockApplyProcessDefinitionOperation().withNetworkError();

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
      />,
      {wrapper: Wrapper}
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    );

    await user.click(
      await screen.findByLabelText(
        /Yes, I confirm I want to delete this process definition./i
      )
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));
    expect(
      await screen.findByTestId('delete-operation-spinner')
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    ).toBeDisabled();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('delete-operation-spinner')
    );

    expect(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    ).toBeEnabled();
  });

  it('should show warning when clicking apply without confirmation', async () => {
    mockApplyProcessDefinitionOperation().withSuccess(mockOperation);

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
      />,
      {wrapper: Wrapper}
    );

    await user.click(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    expect(
      await screen.findByText('Please tick this box if you want to proceed.')
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /close/}));

    await user.click(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    );

    expect(
      screen.queryByText('Please tick this box if you want to proceed.')
    ).not.toBeInTheDocument();
  });
});
