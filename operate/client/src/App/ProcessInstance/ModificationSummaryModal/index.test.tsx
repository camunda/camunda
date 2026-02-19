/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createAddVariableModification} from 'modules/mocks/modifications';
import {modificationsStore} from 'modules/stores/modifications';
import {render, screen, waitFor} from 'modules/testing-library';
import {ModificationSummaryModal} from './index';
import {open} from 'modules/mocks/diagrams';
import {useEffect, act} from 'react';
import {notificationsStore} from 'modules/stores/notifications';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {cancelAllTokens} from 'modules/utils/modifications';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';
import {mockModifyProcessInstance} from 'modules/mocks/api/v2/processInstances/modifyProcessInstance';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const mockProcessInstance: ProcessInstance = {
  processInstanceKey: '1',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionKey: '2',
  processDefinitionVersion: 1,
  processDefinitionId: 'someKey',
  tenantId: '<default>',
  processDefinitionName: 'someProcessName',
  hasIncident: true,
};

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        modificationsStore.reset();
      };
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };
  return Wrapper;
};

describe('Modification Summary Modal', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  it('should render information message', async () => {
    render(<ModificationSummaryModal open setOpen={() => {}} />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByText(/Planned modifications for Process Instance/),
    ).toBeInTheDocument();
    expect(screen.getByText(/"someProcessName - 1"/)).toBeInTheDocument();
    expect(screen.getByText(/Click "Apply" to proceed/)).toBeInTheDocument();
  });

  it('should display no planned modification messages', async () => {
    render(<ModificationSummaryModal open setOpen={() => {}} />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByText('No planned flow node modifications'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('No planned variable modifications'),
    ).toBeInTheDocument();
  });

  it('should render variable modifications', async () => {
    render(<ModificationSummaryModal open setOpen={() => {}} />, {
      wrapper: getWrapper(),
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
        wrapper: getWrapper(),
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
      wrapper: getWrapper(),
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
      modificationsStore.cancelToken('flow-node-1', 'some-instance-key-1', {});
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
        businessObjects: {},
        bpmnProcessId: '123',
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
        wrapper: getWrapper(),
      },
    );

    await waitFor(() =>
      expect(screen.getByRole('button', {name: 'Apply'})).toBeEnabled(),
    );

    await user.click(
      screen.getByRole('button', {name: 'Delete flow node modification'}),
    );

    expect(
      screen.getByText('No planned flow node modifications'),
    ).toBeInTheDocument();
    expect(modificationsStore.flowNodeModifications).toEqual([]);
    expect(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
  });

  it('should delete cancel token modification applied on a single flow node instance key', async () => {
    const {user} = render(
      <ModificationSummaryModal open setOpen={() => {}} />,
      {
        wrapper: getWrapper(),
      },
    );

    act(() => {
      modificationsStore.cancelToken('flow-node-1', 'some-instance-key-1', {});
      modificationsStore.cancelToken('flow-node-1', 'some-instance-key-2', {});
    });

    await waitFor(() =>
      expect(screen.getByRole('button', {name: 'Apply'})).toBeEnabled(),
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
      businessObjects: {},
      bpmnProcessId: '123',
    });

    modificationsStore.addMoveModification({
      sourceFlowNodeId: 'flow-node-1',
      sourceFlowNodeInstanceKey: 'some-instance-key-2',
      targetFlowNodeId: 'flow-node-2',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      newScopeCount: 1,
      businessObjects: {},
      bpmnProcessId: '123',
    });

    const {user} = render(
      <ModificationSummaryModal open setOpen={() => {}} />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitFor(() =>
      expect(screen.getByRole('button', {name: 'Apply'})).toBeEnabled(),
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
    const mockOnClose = vi.fn();

    const {user} = render(
      <ModificationSummaryModal open setOpen={mockOnClose} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByRole('button', {name: /close/i}),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: /cancel/i}));

    expect(mockOnClose).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', {name: /close/i}));

    expect(mockOnClose).toHaveBeenCalledTimes(2);
  });

  it.skip('should display variable content details on modal icon click', async () => {
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
        wrapper: getWrapper(),
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
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'multi-instance-subprocess',
          active: 6,
          incidents: 1,
          completed: 0,
          canceled: 0,
        },
        {
          elementId: 'subprocess-service-task',
          active: 4,
          incidents: 2,
          completed: 0,
          canceled: 0,
        },
      ],
    });

    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

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

      modificationsStore.addCancelModification({
        flowNodeId: 'multi-instance-subprocess',
        affectedTokenCount: 7,
        visibleAffectedTokenCount: 7,
        businessObjects: {},
      });
    });

    render(<ModificationSummaryModal open setOpen={() => {}} />, {
      wrapper: getWrapper(),
    });

    const [
      addModificationAffectedTokenCount,
      cancelModificationAfectedTokenCount,
    ] = await screen.findAllByTestId('affected-token-count');
    expect(addModificationAffectedTokenCount).toHaveTextContent('1');
    await waitFor(() =>
      expect(cancelModificationAfectedTokenCount).toHaveTextContent('7'),
    );
  });

  it('should display success notification when modifications are applied with success', async () => {
    modificationsStore.enableModificationMode();
    const mockOnClose = vi.fn();

    mockModifyProcessInstance().withSuccess(null);

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
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByRole('button', {name: 'Apply'}),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Apply'}));

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
    const mockOnClose = vi.fn();

    mockModifyProcessInstance().withServerError();

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
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByRole('button', {name: 'Apply'}),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Apply'}));

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
    const mockOnClose = vi.fn();

    mockModifyProcessInstance().withServerError(403);

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
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
            <Routes>
              <Route
                path={Paths.processInstance()}
                element={
                  <ModificationSummaryModal open setOpen={mockOnClose} />
                }
              />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>,
    );

    expect(
      await screen.findByRole('button', {name: 'Apply'}),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Apply'}));
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
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'service-task-2',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
        {
          elementId: 'service-task-3',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    });
    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

    render(<ModificationSummaryModal open setOpen={vi.fn()} />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.queryByText(
        'The planned modifications will cancel all remaining running flow node instances. Applying these modifications will cancel the entire process instance.',
      ),
    ).not.toBeInTheDocument();

    act(() => {
      modificationsStore.addCancelModification({
        flowNodeId: 'service-task-2',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        businessObjects: {},
      });
    });

    expect(
      screen.queryByText(
        'The planned modifications will cancel all remaining running flow node instances. Applying these modifications will cancel the entire process instance.',
      ),
    ).not.toBeInTheDocument();

    act(() => {
      modificationsStore.addCancelModification({
        flowNodeId: 'service-task-3',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        businessObjects: {},
      });
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
          flowNode: {id: 'service-task-3', name: 'service-task-3'},
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
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      parentProcessInstanceKey: '2',
    });
    mockFetchCallHierarchy().withSuccess([
      {
        processInstanceKey: '3',
        processDefinitionName: 'some root process',
        processDefinitionKey: 'process-key',
      },
      {
        processInstanceKey: '2',
        processDefinitionName: 'some parent process',
        processDefinitionKey: 'process-key',
      },
    ]);

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'taskA',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
      ],
    });
    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

    render(<ModificationSummaryModal open setOpen={vi.fn()} />, {
      wrapper: getWrapper(),
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

    expect(await screen.findByRole('button', {name: 'Apply'})).toBeDisabled();

    act(() => {
      cancelAllTokens('taskA', 0, 0, {});
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
    expect(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
  });

  it('should disable Apply and show an error when orphaned variable modifications exist', async () => {
    render(<ModificationSummaryModal open setOpen={() => {}} />, {
      wrapper: getWrapper(),
    });

    act(() => {
      createAddVariableModification({
        scopeId: 'orphaned-scope',
        flowNodeName: 'some flow node',
        name: 'myVar',
        value: '"hello"',
      });
    });

    await waitFor(() =>
      expect(screen.getByRole('button', {name: 'Apply'})).toBeDisabled(),
    );

    expect(
      screen.getByText(
        /Some planned variable modifications cannot be applied./i,
      ),
    ).toBeInTheDocument();

    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          scopeId: 'orphaned-scope',
          flowNode: {id: 'some-flow-node', name: 'some flow node'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds: {},
        },
      });
    });

    await waitFor(() =>
      expect(screen.getByRole('button', {name: 'Apply'})).toBeEnabled(),
    );

    expect(
      screen.queryByText(
        /Some planned variable modifications cannot be applied./i,
      ),
    ).not.toBeInTheDocument();
  });
});
