/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createAddVariableModification} from 'modules/mocks/modifications';
import {mockServer} from 'modules/mock-server/node';
import {mockProcessForModifications} from 'modules/mocks/mockProcessForModifications';
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
import {createInstance} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {rest} from 'msw';
import {ModificationSummaryModal} from './index';

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

describe('Modification Summary Modal', () => {
  beforeEach(() => {
    processInstanceDetailsStore.setProcessInstance(createInstance({id: '1'}));
  });
  afterEach(() => {
    modificationsStore.reset();
    processInstanceDetailsStore.reset();
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
      screen.getByRole('columnheader', {
        name: /operation/i,
      })
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
      screen.getByRole('button', {name: 'Delete variable modification'})
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
      screen.getByRole('columnheader', {
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
      screen.getByRole('cell', {
        name: /flow node 1 → flow node 2/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /affected tokens/i,
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

    await user.click(
      screen.getByRole('button', {name: 'Delete flow node modification'})
    );

    expect(
      screen.getByText('No planned flow node modifications')
    ).toBeInTheDocument();
    expect(modificationsStore.flowNodeModifications).toEqual([]);
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

    const [jsonEditorModal, diffEditorModal] = screen.getAllByRole('button', {
      name: /open json editor modal/i,
    });

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
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessForModifications))
      ),

      rest.get(
        '/api/process-instances/:processInstanceId/statistics',
        (_, res, ctx) =>
          res.once(
            ctx.json([
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
            ])
          )
      )
    );
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
        operation: 'CANCEL_TOKEN',
        flowNode: {
          id: 'multi-instance-subprocess',
          name: 'multi instance subprocess',
        },
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
      },
    });

    expect(screen.getByTestId('affected-token-count')).toHaveTextContent('7');
  });

  it('should display success notification when modifications are applied with success', async () => {
    modificationsStore.enableModificationMode();
    const mockOnClose = jest.fn();

    mockServer.use(
      rest.post(
        '/api/process-instances/:processInstanceId/modify',
        (_, res, ctx) => res.once(ctx.json({}))
      )
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

    mockServer.use(
      rest.post(
        '/api/process-instances/:processInstanceId/modify',
        (_, res, ctx) => res.once(ctx.status(500), ctx.json({}))
      )
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
      expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
        headline: 'Modification failed',
        description: 'Unable to apply modifications, please try again.',
      })
    );
    expect(mockOnClose).toHaveBeenCalled();
    expect(modificationsStore.isModificationModeEnabled).toBe(false);
  });
});
