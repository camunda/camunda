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

import {createAddVariableModification} from 'modules/mocks/modifications';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {render, screen, waitFor} from 'modules/testing-library';
import {createBatchOperation, createInstance} from 'modules/testUtils';
import {ModificationSummaryModal} from './index';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockModify} from 'modules/mocks/api/processInstances/modify';
import {open} from 'modules/mocks/diagrams';
import {useEffect} from 'react';
import {act} from 'react-dom/test-utils';
import {notificationsStore} from 'modules/stores/notifications';

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  useEffect(() => {
    return () => {
      modificationsStore.reset();
      processInstanceDetailsStore.reset();
      processInstanceDetailsStatisticsStore.reset();
    };
  }, []);

  return <>{children}</>;
};

describe('Modification Summary Modal', () => {
  beforeEach(() => {
    processInstanceDetailsStore.setProcessInstance(createInstance({id: '1'}));
  });

  it('should render information message', async () => {
    render(<ModificationSummaryModal open setOpen={() => {}} />, {
      wrapper: Wrapper,
    });

    expect(
      screen.getByText(/Planned modifications for Process Instance/),
    ).toBeInTheDocument();
    expect(screen.getByText(/"someProcessName - 1"/)).toBeInTheDocument();
    expect(screen.getByText(/Click "Apply" to proceed/)).toBeInTheDocument();
  });

  it('should display no planned modification messages', async () => {
    render(<ModificationSummaryModal open setOpen={() => {}} />, {
      wrapper: Wrapper,
    });

    expect(
      screen.getByText('No planned flow node modifications'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('No planned variable modifications'),
    ).toBeInTheDocument();
  });

  it('should render variable modifications', async () => {
    render(<ModificationSummaryModal open setOpen={() => {}} />, {
      wrapper: Wrapper,
    });

    act(() => {
      createAddVariableModification({
        scopeId: 'flow-node-1',
        flowNodeName: 'flow node 1',
        name: 'test',
        value: '123',
      });
    });

    expect(
      await screen.findByRole('columnheader', {name: /operation/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('cell', {
        name: /add/i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /scope/i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: /flow node 1/i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /name \/ value/i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: /test: 123/i,
      }),
    ).toBeInTheDocument();
  });

  it('should delete variable modifications', async () => {
    const {user} = render(
      <ModificationSummaryModal open setOpen={() => {}} />,
      {
        wrapper: Wrapper,
      },
    );

    act(() => {
      createAddVariableModification({
        scopeId: 'flow-node-1',
        flowNodeName: 'flow node 1',
        name: 'test',
        value: '123',
      });
    });

    await user.click(
      await screen.findByRole('button', {name: 'Delete variable modification'}),
    );

    expect(
      screen.getByText('No planned variable modifications'),
    ).toBeInTheDocument();
    expect(modificationsStore.variableModifications).toEqual([]);
  });

  it('should render flow node modifications', async () => {
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'flow-node-1', name: 'flow node 1'},
        targetFlowNode: {id: 'flow-node-2', name: 'flow node 2'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
        scopeIds: ['1'],
        parentScopeIds: {},
      },
    });

    render(<ModificationSummaryModal open setOpen={() => {}} />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByRole('columnheader', {
        name: /operation/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('cell', {
        name: /move/i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /flow node/i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /instance key/i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: /flow node 1 → flow node 2/i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: /--/i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /affected tokens/i,
      }),
    ).toBeInTheDocument();

    act(() => {
      modificationsStore.removeLastModification();
      modificationsStore.cancelToken('flow-node-1', 'some-instance-key-1');
    });

    expect(
      screen.queryByRole('cell', {
        name: /--/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('cell', {
        name: /some-instance-key-1/i,
      }),
    ).toBeInTheDocument();

    act(() => {
      modificationsStore.removeLastModification();
      modificationsStore.addMoveModification({
        sourceFlowNodeId: 'flow-node-1',
        sourceFlowNodeInstanceKey: 'some-instance-key-2',
        targetFlowNodeId: 'flow-node-2',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        newScopeCount: 1,
      });
    });

    expect(
      screen.queryByRole('cell', {
        name: /some-instance-key-1/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('cell', {
        name: /some-instance-key-2/i,
      }),
    ).toBeInTheDocument();
  });

  it('should delete flow node modifications', async () => {
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'flow-node-1', name: 'flow node 1'},
        targetFlowNode: {id: 'flow-node-2', name: 'flow node 2'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
        scopeIds: ['1'],
        parentScopeIds: {},
      },
    });

    const {user} = render(
      <ModificationSummaryModal open setOpen={() => {}} />,
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() =>
      expect(screen.getByRole('button', {name: /^apply$/i})).toBeEnabled(),
    );

    await user.click(
      screen.getByRole('button', {name: 'Delete flow node modification'}),
    );

    expect(
      screen.getByText('No planned flow node modifications'),
    ).toBeInTheDocument();
    expect(modificationsStore.flowNodeModifications).toEqual([]);
    expect(screen.getByRole('button', {name: /^apply$/i})).toBeDisabled();
  });

  it('should delete cancel token modification applied on a single flow node instance key', async () => {
    const {user} = render(
      <ModificationSummaryModal open setOpen={() => {}} />,
      {
        wrapper: Wrapper,
      },
    );

    act(() => {
      modificationsStore.cancelToken('flow-node-1', 'some-instance-key-1');
      modificationsStore.cancelToken('flow-node-1', 'some-instance-key-2');
    });

    await waitFor(() =>
      expect(screen.getByRole('button', {name: /^apply$/i})).toBeEnabled(),
    );

    const [deleteFirstModification] = screen.getAllByRole('button', {
      name: 'Delete flow node modification',
    });

    await user.click(deleteFirstModification!);

    expect(screen.queryByText('some-instance-key-1')).not.toBeInTheDocument();
    expect(screen.getByText('some-instance-key-2')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: 'Delete flow node modification'}),
    );
    expect(screen.queryByText('some-instance-key-2')).not.toBeInTheDocument();

    expect(
      screen.getByText('No planned flow node modifications'),
    ).toBeInTheDocument();
  });

  it('should delete move token modification applied on a single flow node instance key', async () => {
    modificationsStore.addMoveModification({
      sourceFlowNodeId: 'flow-node-1',
      sourceFlowNodeInstanceKey: 'some-instance-key-1',
      targetFlowNodeId: 'flow-node-2',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      newScopeCount: 1,
    });

    modificationsStore.addMoveModification({
      sourceFlowNodeId: 'flow-node-1',
      sourceFlowNodeInstanceKey: 'some-instance-key-2',
      targetFlowNodeId: 'flow-node-2',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      newScopeCount: 1,
    });

    const {user} = render(
      <ModificationSummaryModal open setOpen={() => {}} />,
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() =>
      expect(screen.getByRole('button', {name: /^apply$/i})).toBeEnabled(),
    );

    const [deleteFirstModification] = screen.getAllByRole('button', {
      name: 'Delete flow node modification',
    });

    await user.click(deleteFirstModification!);

    expect(screen.queryByText('some-instance-key-1')).not.toBeInTheDocument();
    expect(screen.getByText('some-instance-key-2')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: 'Delete flow node modification'}),
    );
    expect(screen.queryByText('some-instance-key-2')).not.toBeInTheDocument();

    expect(
      screen.getByText('No planned flow node modifications'),
    ).toBeInTheDocument();
  });

  it('should handle modal close', async () => {
    const mockOnClose = jest.fn();

    const {user} = render(
      <ModificationSummaryModal open setOpen={mockOnClose} />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(screen.getByRole('button', {name: /cancel/i}));

    expect(mockOnClose).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', {name: /^close$/i}));

    expect(mockOnClose).toHaveBeenCalledTimes(2);
  });

  it('should display variable content details on modal icon click', async () => {
    createAddVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flow node 1',
      name: 'test',
      value: '123',
    });

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'EDIT_VARIABLE',
        scopeId: 'flow-node-2',
        flowNodeName: 'flow node 2',
        id: '1',
        name: 'anotherVariable',
        oldValue: '"someOldValue"',
        newValue: '"someOldValue-edited"',
      },
    });

    const {user} = render(
      <ModificationSummaryModal open setOpen={() => {}} />,
      {
        wrapper: Wrapper,
      },
    );

    const [jsonEditor, diffEditor] = await screen.findAllByRole('button', {
      name: /expand current row/i,
    });

    await user.click(jsonEditor!);

    expect(await screen.findByDisplayValue('123')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: /collapse current row/i}),
    );

    expect(
      screen.getAllByRole('button', {
        name: /expand current row/i,
      }),
    ).toHaveLength(2);

    await user.click(diffEditor!);

    expect(screen.getByDisplayValue('"someOldValue"')).toBeInTheDocument();
    expect(
      screen.getByDisplayValue('"someOldValue-edited"'),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: /collapse current row/i}),
    );

    expect(
      screen.getAllByRole('button', {
        name: /expand current row/i,
      }),
    ).toHaveLength(2);
  });

  it('should display total affected token count if a subprocess is canceled', async () => {
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'multi-instance-subprocess',
        active: 6,
        incidents: 1,
        completed: 0,
        canceled: 0,
      },
      {
        activityId: 'subprocess-service-task',
        active: 4,
        incidents: 2,
        completed: 0,
        canceled: 0,
      },
    ]);

    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));

    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId',
    );
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(1);

    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {
            id: 'multi-instance-subprocess',
            name: 'multi instance subprocess',
          },
          scopeId: 'multi-instance-subprocess-scope',
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds: {},
        },
      });

      modificationsStore.cancelAllTokens('multi-instance-subprocess');
    });

    render(<ModificationSummaryModal open setOpen={() => {}} />, {
      wrapper: Wrapper,
    });

    const [
      addModificationAffectedTokenCount,
      cancelModificationAfectedTokenCount,
    ] = await screen.findAllByTestId('affected-token-count');
    expect(addModificationAffectedTokenCount).toHaveTextContent('1');
    expect(cancelModificationAfectedTokenCount).toHaveTextContent('13'); // 7 from subprocess + 6 from service task
  });

  it('should display success notification when modifications are applied with success', async () => {
    modificationsStore.enableModificationMode();
    const mockOnClose = jest.fn();

    mockModify().withSuccess(
      createBatchOperation({type: 'MODIFY_PROCESS_INSTANCE'}),
    );

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: '123',
        flowNode: {id: 'flow-node-1', name: 'flow node 1'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
        parentScopeIds: {},
      },
    });

    const {user} = render(
      <ModificationSummaryModal open setOpen={mockOnClose} />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(screen.getByRole('button', {name: /^apply$/i}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'success',
        title: 'Modifications applied',
      }),
    );
    expect(mockOnClose).toHaveBeenCalled();
    expect(modificationsStore.isModificationModeEnabled).toBe(false);
  });

  it('should display error notification when modifications are applied with failure', async () => {
    modificationsStore.enableModificationMode();
    const mockOnClose = jest.fn();

    mockModify().withServerError();

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: '123',
        flowNode: {id: 'flow-node-1', name: 'flow node 1'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
        parentScopeIds: {},
      },
    });

    const {user} = render(
      <ModificationSummaryModal open setOpen={mockOnClose} />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(screen.getByRole('button', {name: /^apply$/i}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'error',
        title: 'Modification failed',
        subtitle: 'Unable to apply modifications, please try again.',
      }),
    );
    expect(mockOnClose).toHaveBeenCalled();
    expect(modificationsStore.isModificationModeEnabled).toBe(false);
  });

  it('should display error notification when modifications are applied with failure because of auth error', async () => {
    modificationsStore.enableModificationMode();
    const mockOnClose = jest.fn();

    mockModify().withServerError(403);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: '123',
        flowNode: {id: 'flow-node-1', name: 'flow node 1'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
        parentScopeIds: {},
      },
    });

    const {user} = render(
      <ModificationSummaryModal open setOpen={mockOnClose} />,
    );

    await user.click(screen.getByRole('button', {name: /^apply$/i}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'error',
        title: 'Modification failed',
        subtitle: 'You do not have permission',
      }),
    );
    expect(mockOnClose).toHaveBeenCalled();
    expect(modificationsStore.isModificationModeEnabled).toBe(false);
  });

  it('should display/hide warning message if all modifications are about to be canceled', async () => {
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'taskA',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
      {
        activityId: 'taskB',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);

    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics('id');

    render(<ModificationSummaryModal open setOpen={jest.fn()} />, {
      wrapper: Wrapper,
    });

    expect(
      screen.queryByText(
        'The planned modifications will cancel all remaining running flow node instances. Applying these modifications will cancel the entire process instance.',
      ),
    ).not.toBeInTheDocument();

    act(() => {
      modificationsStore.cancelAllTokens('taskA');
    });

    expect(
      screen.queryByText(
        'The planned modifications will cancel all remaining running flow node instances. Applying these modifications will cancel the entire process instance.',
      ),
    ).not.toBeInTheDocument();

    act(() => {
      modificationsStore.cancelAllTokens('taskB');
    });

    expect(
      await screen.findByText(
        'The planned modifications will cancel all remaining running flow node instances. Applying these modifications will cancel the entire process instance.',
      ),
    ).toBeInTheDocument();

    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {id: 'taskB', name: 'task b'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'some-scope-id',
          parentScopeIds: {},
        },
      });
    });

    expect(
      screen.queryByText(
        'The planned modifications will cancel all remaining running flow node instances. Applying these modifications will cancel the entire process instance.',
      ),
    ).not.toBeInTheDocument();
  });

  it('should display error message and diable apply button if all modifications are about to be canceled and process has a parent', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '1',
        parentInstanceId: '2',
        callHierarchy: [
          {instanceId: '3', processDefinitionName: 'some root process'},
          {instanceId: '2', processDefinitionName: 'some parent process'},
        ],
      }),
    );

    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'taskA',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
    ]);

    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics('id');

    render(<ModificationSummaryModal open setOpen={jest.fn()} />, {
      wrapper: Wrapper,
    });

    expect(
      screen.queryByText(
        /This set of planned modifications cannot be applied. This instance is a child instance of/i,
      ),
    ).not.toBeInTheDocument();

    expect(
      screen.queryByText(/This instance is the child instance of/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/some parent process - 2/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(
        /, and cannot be canceled entirely. To cancel this instance, the root instance/i,
      ),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/some root process - 3/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/needs to be canceled./i),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: /^apply$/i})).toBeDisabled();

    act(() => {
      modificationsStore.cancelAllTokens('taskA');
    });

    expect(
      await screen.findByText(
        /This set of planned modifications cannot be applied. This instance is a child instance of/i,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/some parent process - 2/i)).toBeInTheDocument();
    expect(
      screen.getByText(
        /, and cannot be canceled entirely. To cancel this instance, the root instance/i,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/some root process - 3/i)).toBeInTheDocument();
    expect(screen.getByText(/needs to be canceled./i)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /^apply$/i})).toBeDisabled();
  });
});
