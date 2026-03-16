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
import {ViewFullVariableButton} from './index';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockGetVariable} from 'modules/mocks/api/v2/variables/getVariable';
import {createVariable} from 'modules/testUtils';

const createWrapper = () => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
    </QueryClientProvider>
  );
  return Wrapper;
};

describe('<ViewFullVariableButton />', () => {
  it('should load full value', async () => {
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
        variableName={mockVariableName}
        variableKey={mockVariableKey}
      />,
      {wrapper: createWrapper()},
    );

    await user.click(
      screen.getByRole('button', {
        name: /view full value of foo-variable/i,
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
      screen.getByRole('heading', {name: `Full value of ${mockVariableName}`}),
    ).toBeInTheDocument();
    expect(await screen.findByText(/"foo": "bar"/)).toBeInTheDocument();
  });
});
