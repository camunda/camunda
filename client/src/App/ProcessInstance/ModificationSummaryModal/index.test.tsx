/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {render, screen} from 'modules/testing-library';
import {createInstance} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {ModificationSummaryModal} from './index';

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

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flow node 1',
        id: '1',
        name: 'test',
        newValue: '123',
      },
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

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flow node 1',
        id: '1',
        name: 'test',
        newValue: '123',
      },
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

    await user.click(screen.getByTestId('apply-button'));

    expect(mockOnClose).toHaveBeenCalledTimes(2);
  });
});
