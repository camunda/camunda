/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {MockedResponse} from '@apollo/react-testing';
import {render, screen, fireEvent} from '@testing-library/react';
import {Route, MemoryRouter} from 'react-router-dom';
import {Form} from 'react-final-form';

import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {
  mockTaskWithVariables,
  mockTaskWithoutVariables,
} from 'modules/queries/get-task-variables';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Variables} from './';

const getWrapper = ({
  id,
  mocks,
}: {
  id: string;
  mocks: Array<MockedResponse>;
}) => {
  const Wrapper: React.FC = ({children}) => (
    <MemoryRouter initialEntries={[`/${id}`]}>
      <Route path="/:id">
        <MockedApolloProvider mocks={[mockGetCurrentUser, ...mocks]}>
          <MockThemeProvider>
            <Form onSubmit={() => {}}>{() => children}</Form>
          </MockThemeProvider>
        </MockedApolloProvider>
      </Route>
    </MemoryRouter>
  );

  return Wrapper;
};

describe('<Variables />', () => {
  it('should render with variables (readonly)', async () => {
    render(<Variables />, {
      wrapper: getWrapper({id: '0', mocks: [mockTaskWithVariables]}),
    });

    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(screen.getByText('myVar')).toBeInTheDocument();
    expect(screen.getByText('0001')).toBeInTheDocument();
    expect(screen.getByText('isCool')).toBeInTheDocument();
    expect(screen.getByText('yes')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('should render with empty message', async () => {
    render(<Variables />, {
      wrapper: getWrapper({id: '0', mocks: [mockTaskWithoutVariables]}),
    });

    expect(
      await screen.findByText('Task has no variables.'),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('variables-table')).not.toBeInTheDocument();
  });

  it('should edit variable', async () => {
    render(<Variables canEdit />, {
      wrapper: getWrapper({id: '0', mocks: [mockTaskWithVariables]}),
    });
    const newVariableValue = 'changedValue';

    expect(await screen.findByDisplayValue('0001')).toBeInTheDocument();

    fireEvent.change(await screen.findByDisplayValue('0001'), {
      target: {value: newVariableValue},
    });

    expect(
      await screen.findByDisplayValue(newVariableValue),
    ).toBeInTheDocument();
  });
});
