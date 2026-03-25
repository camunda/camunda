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
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockGetVariable} from 'modules/mocks/api/v2/variables/getVariable';
import {createVariable} from 'modules/testUtils';
import {ViewFullVariableButton} from './index';
import {Form} from 'react-final-form';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

const createWrapper = () => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
          <Routes>
            <Route
              path={Paths.processInstance()}
              element={<Form onSubmit={() => {}}>{() => children}</Form>}
            />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );
  return Wrapper;
};

describe('<ViewFullVariableButton />', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  it('should load full value for mode show', async () => {
    const mockVariableName = 'foo-variable';
    const mockVariableKey = 'variable-key-123';
    const mockVariableValue = '{"foo": "bar", "test": 123}';

    mockGetVariable().withSuccess(
      createVariable({
        variableKey: mockVariableKey,
        name: mockVariableName,
        value: mockVariableValue,
      }),
    );

    const {user} = render(
      <ViewFullVariableButton
        mode="show"
        variableName={mockVariableName}
        variableKey={mockVariableKey}
        variableValue={mockVariableValue}
      />,
      {wrapper: createWrapper()},
    );

    await user.click(
      screen.getByRole('button', {
        name: `View full value of ${mockVariableName}`,
      }),
    );

    expect(
      screen.getByTestId('variable-operation-spinner'),
    ).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('variable-operation-spinner'),
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: `Full value of ${mockVariableName}`,
      }),
    ).toBeInTheDocument();
    expect(await screen.findByText(/"foo": "bar"/)).toBeInTheDocument();
  });

  it('should open JSON editor modal for mode edit', async () => {
    const mockVariableName = 'foo-variable';
    const mockVariableKey = 'variable-key-123';
    const mockVariableValue = '{"foo": "bar", "test": 123}';

    mockGetVariable().withSuccess(
      createVariable({
        variableKey: mockVariableKey,
        name: mockVariableName,
        value: mockVariableValue,
      }),
    );

    const {user} = render(
      <ViewFullVariableButton
        mode="edit"
        variableName={mockVariableName}
        variableKey={mockVariableKey}
        variableValue={mockVariableValue}
      />,
      {wrapper: createWrapper()},
    );

    await user.click(
      screen.getByRole('button', {
        name: 'Open JSON editor',
      }),
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: `Edit Variable "${mockVariableName}"`,
      }),
    ).toBeInTheDocument();
  });

  it('should open JSON editor modal for mode add', async () => {
    const mockVariableName = 'foo-variable';

    const {user} = render(
      <ViewFullVariableButton
        mode="add"
        variableName={mockVariableName}
        scopeId={null}
      />,
      {wrapper: createWrapper()},
    );

    await user.click(
      screen.getByRole('button', {
        name: 'Open JSON editor',
      }),
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: 'Edit a new Variable',
      }),
    ).toBeInTheDocument();
  });
});
