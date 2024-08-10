/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  within,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import Variables from '../index';
import {Wrapper, mockVariables} from './mocks';
import {createInstance} from 'modules/testUtils';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {act} from 'react';

const instanceMock = createInstance({id: '1'});

describe('Add variable', () => {
  it('should show/hide add variable inputs', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    expect(
      screen.queryByRole('textbox', {
        name: /name/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {
        name: /value/i,
      }),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /add variable/i}));

    expect(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: /exit edit mode/i}));

    expect(
      screen.queryByRole('textbox', {
        name: /name/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {
        name: /value/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should not allow empty value', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await user.click(screen.getByRole('button', {name: /add variable/i}));

    expect(screen.getByRole('button', {name: /save variable/i})).toBeDisabled();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'test',
    );
    expect(screen.getByRole('button', {name: /save variable/i})).toBeDisabled();

    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '    ',
    );

    expect(screen.getByRole('button', {name: /save variable/i})).toBeDisabled();
    expect(screen.queryByTitle('Value has to be JSON')).not.toBeInTheDocument();
    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();
  });

  it('should not allow empty characters in variable name', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await user.click(screen.getByRole('button', {name: /add variable/i}));

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();

    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '123',
    );

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();
    expect(screen.queryByText('Name has to be filled')).not.toBeInTheDocument();
    expect(
      await screen.findByText('Name has to be filled'),
    ).toBeInTheDocument();

    await user.dblClick(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      'test',
    );

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();

    expect(screen.getByText('Name has to be filled')).toBeInTheDocument();
    expect(screen.queryByText('Value has to be JSON')).not.toBeInTheDocument();
    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      '   ',
    );

    expect(screen.getByText('Value has to be JSON')).toBeInTheDocument();
    expect(await screen.findByText('Name is invalid')).toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      ' test',
    );

    expect(screen.getByText('Value has to be JSON')).toBeInTheDocument();
    expect(screen.getByText('Name is invalid')).toBeInTheDocument();

    await user.dblClick(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '"valid value"',
    );

    expect(screen.queryByText('Value has to be JSON')).not.toBeInTheDocument();
    expect(screen.getByText('Name is invalid')).toBeInTheDocument();
  });

  it('should not allow to add duplicate variables', async () => {
    jest.useFakeTimers();
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await user.click(screen.getByRole('button', {name: /add variable/i}));

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();
    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      mockVariables[0]!.name,
    );

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();
    expect(screen.queryByText('Name should be unique')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Value has to be filled'),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByText('Name should be unique'),
    ).toBeInTheDocument();
    expect(
      await screen.findByText('Value has to be filled'),
    ).toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '123',
    );

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();
    expect(
      screen.queryByText('Value has to be filled'),
    ).not.toBeInTheDocument();

    expect(screen.getByText('Name should be unique')).toBeInTheDocument();

    await user.dblClick(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'someOtherName',
    );

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );

    expect(screen.queryByText('Name should be unique')).not.toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should not allow to add variable with invalid name', async () => {
    jest.useFakeTimers();
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await user.click(screen.getByRole('button', {name: /add variable/i}));

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();
    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      '"invalid"',
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '123',
    );

    expect(await screen.findByText('Name is invalid')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();

    await user.clear(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'someOtherName',
    );

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );

    expect(screen.queryByText('Name is invalid')).not.toBeInTheDocument();
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('clicking edit variables while add mode is open, should not display a validation error', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables('1');

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await user.click(screen.getByRole('button', {name: /add variable/i}));

    const withinVariable = within(screen.getByTestId('variable-clientNo'));
    await user.click(
      withinVariable.getByRole('button', {name: /edit variable/i}),
    );
    expect(
      screen.queryByTitle('Name should be unique'),
    ).not.toBeInTheDocument();
  });

  it('should not exit add variable state when user presses Enter', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    expect(
      screen.queryByRole('textbox', {
        name: /name/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {
        name: /value/i,
      }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: /add variable/i}));

    expect(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
    ).toBeInTheDocument();

    await user.keyboard('{Enter}');

    expect(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
    ).toBeInTheDocument();
  });

  it('should exit Add Variable mode when process is canceled', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
    ).toBeInTheDocument();

    act(() =>
      processInstanceDetailsStore.setProcessInstance({
        ...instanceMock,
        state: 'CANCELED',
      }),
    );

    expect(
      screen.queryByRole('textbox', {
        name: /name/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {
        name: /value/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should have JSON editor when adding a new Variable', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    await user.click(
      screen.getByRole('button', {name: /open json editor modal/i}),
    );

    expect(
      within(screen.getByRole('dialog')).getByRole('button', {name: /cancel/i}),
    ).toBeEnabled();
    expect(
      within(screen.getByRole('dialog')).getByRole('button', {name: /apply/i}),
    ).toBeEnabled();
    expect(
      within(screen.getByRole('dialog')).getByTestId('monaco-editor'),
    ).toBeInTheDocument();
  });
});
