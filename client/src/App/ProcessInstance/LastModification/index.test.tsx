/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {LastModification} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MemoryRouter} from 'react-router-dom';
import {modificationsStore} from 'modules/stores/modifications';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {
  createAddVariableModification,
  createEditVariableModification,
} from 'modules/mocks/modifications';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </ThemeProvider>
  );
};

describe('LastModification', () => {
  afterAll(() => {
    modificationsStore.reset();
  });

  it('should not display last modification if no modifications applied', () => {
    render(<LastModification />, {wrapper: Wrapper});

    expect(
      screen.queryByText(/Last added modification/)
    ).not.toBeInTheDocument();
  });

  it('should display/remove last added modification', async () => {
    const {user} = render(<LastModification />, {wrapper: Wrapper});

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: generateUniqueID(),
        flowNode: {id: '1', name: 'flowNode1'},
        affectedTokenCount: 1,
        parentScopeIds: {},
      },
    });
    expect(screen.getByText(/Last added modification/)).toBeInTheDocument();
    expect(screen.getByText(/Add "flowNode1"/)).toBeInTheDocument();

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: '2', name: 'flowNode2'},
        affectedTokenCount: 2,
      },
    });

    expect(screen.getByText(/Cancel "flowNode2"/)).toBeInTheDocument();

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: '3', name: 'flowNode3'},
        targetFlowNode: {id: '4', name: 'flowNode4'},
        affectedTokenCount: 2,
        scopeIds: [generateUniqueID(), generateUniqueID()],
        parentScopeIds: {},
      },
    });

    expect(
      screen.getByText(/Move "flowNode3" to "flowNode4"/)
    ).toBeInTheDocument();

    createAddVariableModification({
      id: '1',
      scopeId: '5',
      flowNodeName: 'flowNode5',
      name: 'variableName1',
      value: 'variableValue1',
    });

    expect(
      screen.getByText(/Add new variable "variableName1"/)
    ).toBeInTheDocument();

    createEditVariableModification({
      name: 'variableName2',
      oldValue: 'variableValue2',
      newValue: 'editedVariableValue2',
      scopeId: '5',
      flowNodeName: 'flowNode6',
    });

    expect(
      screen.getByText(/Edit variable "variableName2"/)
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(
      screen.getByText(/Add new variable "variableName1"/)
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(
      screen.getByText(/Move "flowNode3" to "flowNode4"/)
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(screen.getByText(/Cancel "flowNode2"/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(screen.getByText(/Add "flowNode1"/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(
      screen.queryByText(/Last added modification/)
    ).not.toBeInTheDocument();
  });
});
