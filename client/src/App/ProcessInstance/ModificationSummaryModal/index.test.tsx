/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createAddVariableModification} from 'modules/mocks/modifications';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {createBatchOperation, createInstance} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {ModificationSummaryModal} from './index';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockModify} from 'modules/mocks/api/processInstances/modify';
import {open} from 'modules/mocks/diagrams';

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

describe('Modification Summary Modal', () => {
  beforeAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    processInstanceDetailsStore.setProcessInstance(createInstance({id: '1'}));
  });
  afterEach(() => {
    modificationsStore.reset();
    processInstanceDetailsStore.reset();
    processInstanceDetailsStatisticsStore.reset();
  });

  it('should render information message', async () => {
    render(<ModificationSummaryModal isVisible onClose={() => {}} />, {
      wrapper: ThemeProvider,
    });

    expect(
      screen.getByText(/Planned modifications for Process Instance/)
    ).toBeInTheDocument();
    expect(screen.getByText('"someProcessName - 1"')).toBeInTheDocument();
    expect(screen.getByText(/Click "Apply" to proceed/)).toBeInTheDocument();
  });

  it('should display no planned modification messages', async () => {
    render(<ModificationSummaryModal isVisible onClose={() => {}} />, {
      wrapper: ThemeProvider,
    });

    expect(
      screen.getByText('No planned flow node modifications')
    ).toBeInTheDocument();
    expect(
      screen.getByText('No planned variable modifications')
    ).toBeInTheDocument();
  });

  it('should render variable modifications', async () => {
    render(<ModificationSummaryModal isVisible onClose={() => {}} />, {
      wrapper: ThemeProvider,
    });

    createAddVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flow node 1',
      name: 'test',
      value: '123',
    });

    expect(
      await screen.findByRole('columnheader', {name: /operation/i})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('cell', {
        name: /add/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /scope/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: /flow node 1/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /name \/ value/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: /test: 123/i,
      })
    ).toBeInTheDocument();
  });

  it('should delete variable modifications', async () => {
    const {user} = render(
      <ModificationSummaryModal isVisible onClose={() => {}} />,
      {
        wrapper: ThemeProvider,
      }
    );

    createAddVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flow node 1',
      name: 'test',
      value: '123',
    });

    await user.click(
      await screen.findByRole('button', {name: 'Delete variable modification'})
    );

    expect(
      screen.getByText('No planned variable modifications')
    ).toBeInTheDocument();
    expect(modificationsStore.variableModifications).toEqual([]);
  });

  it('should render flow node modifications', async () => {
    render(<ModificationSummaryModal isVisible onClose={() => {}} />, {
      wrapper: ThemeProvider,
    });

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

    expect(
      await screen.findByRole('columnheader', {
        name: /operation/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('cell', {
        name: /move/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /flow node/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /instance key/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: /flow node 1 → flow node 2/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: /--/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /affected tokens/i,
      })
    ).toBeInTheDocument();

    modificationsStore.removeLastModification();

    modificationsStore.cancelToken('flow-node-1', 'some-instance-key-1');

    await waitForElementToBeRemoved(() =>
      screen.getByRole('cell', {
        name: /--/i,
      })
    );
    expect(
      screen.getByRole('cell', {
        name: /some-instance-key-1/i,
      })
    ).toBeInTheDocument();

    modificationsStore.removeLastModification();

    modificationsStore.addMoveModification({
      sourceFlowNodeId: 'flow-node-1',
      sourceFlowNodeInstanceKey: 'some-instance-key-2',
      targetFlowNodeId: 'flow-node-2',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      newScopeCount: 1,
    });

    await waitForElementToBeRemoved(() =>
      screen.getByRole('cell', {
        name: /some-instance-key-1/i,
      })
    );
    expect(
      screen.getByRole('cell', {
        name: /some-instance-key-2/i,
      })
    ).toBeInTheDocument();
  });

  it('should delete flow node modifications', async () => {
    const {user} = render(
      <ModificationSummaryModal isVisible onClose={() => {}} />,
      {
        wrapper: ThemeProvider,
      }
    );

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

    await waitFor(() =>
      expect(screen.getByRole('button', {name: 'Apply'})).toBeEnabled()
    );

    await user.click(
      screen.getByRole('button', {name: 'Delete flow node modification'})
    );

    expect(
      screen.getByText('No planned flow node modifications')
    ).toBeInTheDocument();
    expect(modificationsStore.flowNodeModifications).toEqual([]);
    expect(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
  });

  it('should delete cancel token modification applied on a single flow node instance key', async () => {
    const {user} = render(
      <ModificationSummaryModal isVisible onClose={() => {}} />,
      {
        wrapper: ThemeProvider,
      }
    );

    modificationsStore.cancelToken('flow-node-1', 'some-instance-key-1');
    modificationsStore.cancelToken('flow-node-1', 'some-instance-key-2');

    await waitFor(() =>
      expect(screen.getByRole('button', {name: 'Apply'})).toBeEnabled()
    );

    const [deleteFirstModification] = screen.getAllByRole('button', {
      name: 'Delete flow node modification',
    });

    await user.click(deleteFirstModification!);

    expect(screen.queryByText('some-instance-key-1')).not.toBeInTheDocument();
    expect(screen.getByText('some-instance-key-2')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: 'Delete flow node modification'})
    );
    expect(screen.queryByText('some-instance-key-2')).not.toBeInTheDocument();

    expect(
      screen.getByText('No planned flow node modifications')
    ).toBeInTheDocument();
  });

  it('should delete move token modification applied on a single flow node instance key', async () => {
    const {user} = render(
      <ModificationSummaryModal isVisible onClose={() => {}} />,
      {
        wrapper: ThemeProvider,
      }
    );

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

    await waitFor(() =>
      expect(screen.getByRole('button', {name: 'Apply'})).toBeEnabled()
    );

    const [deleteFirstModification] = screen.getAllByRole('button', {
      name: 'Delete flow node modification',
    });

    await user.click(deleteFirstModification!);

    expect(screen.queryByText('some-instance-key-1')).not.toBeInTheDocument();
    expect(screen.getByText('some-instance-key-2')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: 'Delete flow node modification'})
    );
    expect(screen.queryByText('some-instance-key-2')).not.toBeInTheDocument();

    expect(
      screen.getByText('No planned flow node modifications')
    ).toBeInTheDocument();
  });

  it('should handle modal close', async () => {
    const mockOnClose = jest.fn();

    const {user} = render(
      <ModificationSummaryModal isVisible onClose={mockOnClose} />,
      {
        wrapper: ThemeProvider,
      }
    );

    await user.click(screen.getByRole('button', {name: /cancel/i}));

    expect(mockOnClose).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', {name: 'Exit Modal'}));

    expect(mockOnClose).toHaveBeenCalledTimes(2);
  });

  it('should display variable content details on modal icon click', async () => {
    const {user} = render(
      <ModificationSummaryModal isVisible onClose={() => {}} />,
      {
        wrapper: ThemeProvider,
      }
    );

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

    const [jsonEditorModal, diffEditorModal] = await screen.findAllByRole(
      'button',
      {
        name: /open json editor modal/i,
      }
    );

    await user.click(jsonEditorModal!);

    expect(
      screen.getByRole('heading', {name: /variable "test"/i})
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue('123')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /close/i}));
    await waitForElementToBeRemoved(() =>
      screen.getByRole('heading', {name: /variable "test"/i})
    );

    await user.click(diffEditorModal!);

    expect(
      screen.getByRole('heading', {name: /variable "anotherVariable"/i})
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue('"someOldValue"')).toBeInTheDocument();
    expect(
      screen.getByDisplayValue('"someOldValue-edited"')
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /close/i}));
    await waitForElementToBeRemoved(() =>
      screen.getByRole('heading', {name: /variable "anotherVariable"/i})
    );
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
      'processInstanceId'
    );
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(1);

    render(<ModificationSummaryModal isVisible onClose={() => {}} />, {
      wrapper: ThemeProvider,
    });

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

    const [
      addModificationAffectedTokenCount,
      cancelModificationAfectedTokenCount,
    ] = await screen.findAllByTestId('affected-token-count');
    expect(addModificationAffectedTokenCount).toHaveTextContent('1');
    expect(cancelModificationAfectedTokenCount).toHaveTextContent('7');
  });

  it('should display success notification when modifications are applied with success', async () => {
    modificationsStore.enableModificationMode();
    const mockOnClose = jest.fn();

    mockModify().withSuccess(
      createBatchOperation({type: 'MODIFY_PROCESS_INSTANCE'})
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
      <ModificationSummaryModal isVisible onClose={mockOnClose} />,
      {
        wrapper: ThemeProvider,
      }
    );

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenCalledWith('success', {
        headline: 'Modifications applied',
      })
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
      <ModificationSummaryModal isVisible onClose={mockOnClose} />,
      {
        wrapper: ThemeProvider,
      }
    );

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
        headline: 'Modification failed',
        description: 'Unable to apply modifications, please try again.',
      })
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
      <ModificationSummaryModal isVisible onClose={mockOnClose} />,
      {
        wrapper: ThemeProvider,
      }
    );

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
        headline: 'Modification failed',
        description: 'You do not have permission',
      })
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

    render(<ModificationSummaryModal isVisible onClose={jest.fn()} />, {
      wrapper: ThemeProvider,
    });

    expect(
      screen.queryByText(
        'The planned modifications will cancel all remaining running flow node instances. Applying these modifications will cancel the entire process instance.'
      )
    ).not.toBeInTheDocument();

    modificationsStore.cancelAllTokens('taskA');

    expect(
      screen.queryByText(
        'The planned modifications will cancel all remaining running flow node instances. Applying these modifications will cancel the entire process instance.'
      )
    ).not.toBeInTheDocument();

    modificationsStore.cancelAllTokens('taskB');

    expect(
      await screen.findByText(
        'The planned modifications will cancel all remaining running flow node instances. Applying these modifications will cancel the entire process instance.'
      )
    ).toBeInTheDocument();

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

    await waitForElementToBeRemoved(() =>
      screen.getByText(
        'The planned modifications will cancel all remaining running flow node instances. Applying these modifications will cancel the entire process instance.'
      )
    );
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
      })
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

    render(<ModificationSummaryModal isVisible onClose={jest.fn()} />, {
      wrapper: ThemeProvider,
    });

    expect(
      screen.queryByText(
        /This set of planned modifications cannot be applied. This instance is a child instance of/i
      )
    ).not.toBeInTheDocument();

    expect(
      screen.queryByText(/This instance is the child instance of/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/some parent process - 2/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(
        /, and cannot be canceled entirely. To cancel this instance, the root instance/i
      )
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/some root process - 3/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/needs to be canceled./i)
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();

    modificationsStore.cancelAllTokens('taskA');

    expect(
      await screen.findByText(
        /This set of planned modifications cannot be applied. This instance is a child instance of/i
      )
    ).toBeInTheDocument();
    expect(screen.getByText(/some parent process - 2/i)).toBeInTheDocument();
    expect(
      screen.getByText(
        /, and cannot be canceled entirely. To cancel this instance, the root instance/i
      )
    ).toBeInTheDocument();
    expect(screen.getByText(/some root process - 3/i)).toBeInTheDocument();
    expect(screen.getByText(/needs to be canceled./i)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
  });
});
