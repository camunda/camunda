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
  beforeAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    modificationsStore.reset();

    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
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
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });
    expect(
      await screen.findByText(/Last added modification/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Add "flowNode1"/)).toBeInTheDocument();

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: '2', name: 'flowNode2'},
        affectedTokenCount: 2,
        visibleAffectedTokenCount: 2,
      },
    });

    expect(await screen.findByText(/Cancel "flowNode2"/)).toBeInTheDocument();

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: '3', name: 'flowNode3'},
        targetFlowNode: {id: '4', name: 'flowNode4'},
        affectedTokenCount: 2,
        visibleAffectedTokenCount: 2,
        scopeIds: [generateUniqueID(), generateUniqueID()],
        parentScopeIds: {},
      },
    });

    expect(
      await screen.findByText(/Move "flowNode3" to "flowNode4"/)
    ).toBeInTheDocument();

    createAddVariableModification({
      id: '1',
      scopeId: '5',
      flowNodeName: 'flowNode5',
      name: 'variableName1',
      value: 'variableValue1',
    });

    expect(
      await screen.findByText(/Add new variable "variableName1"/)
    ).toBeInTheDocument();

    createEditVariableModification({
      name: 'variableName2',
      oldValue: 'variableValue2',
      newValue: 'editedVariableValue2',
      scopeId: '5',
      flowNodeName: 'flowNode6',
    });

    expect(
      await screen.findByText(/Edit variable "variableName2"/)
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
