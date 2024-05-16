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

import {mockApplyProcessDefinitionOperation} from 'modules/mocks/api/processes/operations';
import {operationsStore} from 'modules/stores/operations';
import {panelStatesStore} from 'modules/stores/panelStates';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {useEffect} from 'react';
import {ProcessOperations} from '.';
import {notificationsStore} from 'modules/stores/notifications';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

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

  return <>{children}</>;
};

describe('<ProcessOperations />', () => {
  it('should open modal and show content', async () => {
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
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
      screen.getByText(
        /Deleting a process definition will permanently remove it and will impact the following:/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /All the deleted process definition’s finished process instances will be deleted from the application./i,
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
    mockApplyProcessDefinitionOperation().withSuccess(mockOperation);
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      await screen.findByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);

    await user.click(
      await screen.findByLabelText(
        /Yes, I confirm I want to delete this process definition./i,
      ),
    );

    await user.click(screen.getByRole('button', {name: /danger Delete/}));

    await waitFor(() =>
      expect(operationsStore.state.operations).toEqual([mockOperation]),
    );
    expect(panelStatesStore.state.isOperationsCollapsed).toBe(false);
  });

  it('should show notification on operation error', async () => {
    mockApplyProcessDefinitionOperation().withServerError(500);
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      await screen.findByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);

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
    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);
  });

  it('should show notification on operation auth error', async () => {
    mockApplyProcessDefinitionOperation().withServerError(403);
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      await screen.findByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);

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
        subtitle: 'You do not have permission',
        isDismissable: true,
      });
    });
    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);
  });

  it('should disable button and show spinner when delete operation is triggered', async () => {
    mockApplyProcessDefinitionOperation().withSuccess(mockOperation);
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
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
    expect(screen.getByTestId('delete-operation-spinner')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    ).toBeDisabled();
  });

  it('should enable button and remove spinner when delete operation failed', async () => {
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

    mockApplyProcessDefinitionOperation().withNetworkError();
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
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
    expect(screen.getByTestId('delete-operation-spinner')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    ).toBeDisabled();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('delete-operation-spinner'),
    );

    expect(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    ).toBeEnabled();

    consoleErrorMock.mockRestore();
  });

  it('should show warning when clicking apply without confirmation', async () => {
    mockApplyProcessDefinitionOperation().withSuccess(mockOperation);
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      await screen.findByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
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
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    );

    expect(
      screen.queryByText('Please tick this box if you want to proceed.'),
    ).not.toBeInTheDocument();
  });

  it('should initially disable the delete button', async () => {
    mockApplyProcessDefinitionOperation().withSuccess(mockOperation);
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
      />,
      {wrapper: Wrapper},
    );

    expect(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      }),
    ).toBeDisabled();

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /^delete process definition "myProcess - version 2"$/i,
        }),
      ).toBeEnabled(),
    );
  });

  it('should disable delete button when there are running instances', async () => {
    mockApplyProcessDefinitionOperation().withSuccess(mockOperation);
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 1,
    });

    render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
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
    mockApplyProcessDefinitionOperation().withSuccess(mockOperation);
    mockFetchProcessInstances().withServerError();

    render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
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

  it('should reset confirmation checkbox to unchecked when delete modal is closed and reopened', async () => {
    mockApplyProcessDefinitionOperation().withSuccess(mockOperation);
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    const {user} = render(
      <ProcessOperations
        processDefinitionId="2251799813687094"
        processName="myProcess"
        processVersion="2"
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

    await user.click(screen.getByLabelText(/^close$/i, {selector: 'button'}));

    await user.click(deleteButton);

    expect(checkbox).not.toBeChecked();
  });
});
