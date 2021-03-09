/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {MockedResponse} from '@apollo/client/testing';
import {render, screen, fireEvent} from '@testing-library/react';
import {Route, MemoryRouter} from 'react-router-dom';
import {Form} from 'react-final-form';
import arrayMutators from 'final-form-arrays';

import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {
  mockGetTaskClaimed,
  mockGetTaskClaimedWithVariables,
} from 'modules/queries/get-task';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Variables} from './';
import {Link} from 'react-router-dom';

const getWrapper = ({mocks}: {mocks: Array<MockedResponse>}) => {
  const Wrapper: React.FC = ({children}) => (
    <MemoryRouter initialEntries={['/0']}>
      <MockedApolloProvider mocks={[mockGetCurrentUser, ...mocks]}>
        <MockThemeProvider>
          <Route
            path="/:id"
            component={() => (
              <Form mutators={{...arrayMutators}} onSubmit={() => {}}>
                {() => children}
              </Form>
            )}
          ></Route>
        </MockThemeProvider>
      </MockedApolloProvider>
    </MemoryRouter>
  );

  return Wrapper;
};

describe('<Variables />', () => {
  it('should render with variables (readonly)', async () => {
    render(<Variables />, {
      wrapper: getWrapper({mocks: [mockGetTaskClaimedWithVariables]}),
    });

    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(screen.getByText('myVar')).toBeInTheDocument();
    expect(screen.getByText('"0001"')).toBeInTheDocument();
    expect(screen.getByText('isCool')).toBeInTheDocument();
    expect(screen.getByText('"yes"')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('should render with empty message', async () => {
    render(<Variables />, {
      wrapper: getWrapper({mocks: [mockGetTaskClaimed]}),
    });

    expect(
      await screen.findByText('Task has no Variables'),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('variables-table')).not.toBeInTheDocument();
  });

  it('should edit variable', async () => {
    render(<Variables canEdit />, {
      wrapper: getWrapper({mocks: [mockGetTaskClaimedWithVariables]}),
    });
    const newVariableValue = '"changedValue"';

    expect(await screen.findByDisplayValue('"0001"')).toBeInTheDocument();

    fireEvent.change(await screen.findByDisplayValue('"0001"'), {
      target: {value: newVariableValue},
    });

    expect(
      await screen.findByDisplayValue(newVariableValue),
    ).toBeInTheDocument();
  });

  it('should add two variables and remove one', async () => {
    render(<Variables canEdit />, {
      wrapper: getWrapper({mocks: [mockGetTaskClaimedWithVariables]}),
    });

    fireEvent.click(await screen.findByRole('button', {name: /Add Variable/}));
    fireEvent.click(await screen.findByRole('button', {name: /Add Variable/}));

    expect(
      await screen.findAllByRole('textbox', {name: /new-variables/}),
    ).toHaveLength(4);

    expect(
      screen.getByRole('textbox', {name: 'new-variables[0].name'}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('textbox', {name: 'new-variables[0].value'}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('textbox', {name: 'new-variables[1].name'}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('textbox', {name: 'new-variables[1].value'}),
    ).toBeInTheDocument();

    fireEvent.click(
      await screen.findByRole('button', {name: 'Remove new variable 1'}),
    );

    expect(
      await screen.findAllByRole('textbox', {name: /new-variables/}),
    ).toHaveLength(2);

    expect(
      screen.getByRole('textbox', {name: 'new-variables[0].name'}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('textbox', {name: 'new-variables[0].value'}),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('textbox', {name: 'new-variables[1].name'}),
    ).not.toBeInTheDocument();

    expect(
      screen.queryByRole('textbox', {name: 'new-variables[1].value'}),
    ).not.toBeInTheDocument();
  });

  it('should add variable on task without variables', async () => {
    render(<Variables canEdit />, {
      wrapper: getWrapper({mocks: [mockGetTaskClaimed]}),
    });

    fireEvent.click(await screen.findByRole('button', {name: /Add Variable/}));

    expect(
      screen.getByRole('textbox', {name: 'new-variables[0].name'}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('textbox', {name: 'new-variables[0].value'}),
    ).toBeInTheDocument();
  });

  it('should add validation error on empty variable name', async () => {
    render(<Variables canEdit />, {
      wrapper: getWrapper({mocks: [mockGetTaskClaimed]}),
    });

    fireEvent.click(await screen.findByRole('button', {name: /Add Variable/}));

    fireEvent.change(
      await screen.findByRole('textbox', {name: 'new-variables[0].value'}),
      {target: {value: '"valid_value"'}},
    );

    expect(
      await screen.findByTitle('Variable has to be filled'),
    ).toBeInTheDocument();
  });

  it('should add validation error on empty variable value', async () => {
    render(<Variables canEdit />, {
      wrapper: getWrapper({mocks: [mockGetTaskClaimed]}),
    });

    fireEvent.click(await screen.findByRole('button', {name: /Add Variable/}));

    fireEvent.change(
      await screen.findByRole('textbox', {name: 'new-variables[0].name'}),
      {target: {value: 'valid_name'}},
    );

    expect(
      await screen.findByTitle('Value has to be JSON'),
    ).toBeInTheDocument();
  });

  it('should add validation error on invalid variable name', async () => {
    render(<Variables canEdit />, {
      wrapper: getWrapper({mocks: [mockGetTaskClaimed]}),
    });

    fireEvent.click(await screen.findByRole('button', {name: /Add Variable/}));

    fireEvent.change(
      await screen.findByRole('textbox', {name: 'new-variables[0].value'}),
      {target: {value: 'invalid_value}}}'}},
    );

    expect(
      await screen.findByTitle(
        'Variable has to be filled and Value has to be JSON',
      ),
    ).toBeInTheDocument();
  });

  it('should show no validation error on valid name/value', async () => {
    render(<Variables canEdit />, {
      wrapper: getWrapper({mocks: [mockGetTaskClaimed]}),
    });

    fireEvent.click(await screen.findByRole('button', {name: /Add Variable/}));

    fireEvent.change(
      await screen.findByRole('textbox', {name: 'new-variables[0].name'}),
      {target: {value: 'valid_name'}},
    );

    fireEvent.change(
      await screen.findByRole('textbox', {name: 'new-variables[0].value'}),
      {target: {value: '"valid_value"'}},
    );

    expect(
      screen.queryByTitle('Variable has to be filled and Value has to be JSON'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle('Variable has to be filled'),
    ).not.toBeInTheDocument();
    expect(screen.queryByTitle('Value has to be JSON')).not.toBeInTheDocument();
  });

  it('should reset variables on task id change', async () => {
    render(
      <>
        <Link
          to={(location: Location) => ({
            ...location,
            pathname: '/1',
          })}
        >
          Change route
        </Link>
        <Variables canEdit />
      </>,
      {
        wrapper: getWrapper({
          mocks: [mockGetTaskClaimed, mockGetCurrentUser, mockGetTaskClaimed],
        }),
      },
    );

    fireEvent.click(await screen.findByRole('button', {name: /Add Variable/}));

    fireEvent.change(
      await screen.findByRole('textbox', {name: 'new-variables[0].name'}),
      {target: {value: 'valid_name'}},
    );

    fireEvent.change(
      await screen.findByRole('textbox', {name: 'new-variables[0].value'}),
      {target: {value: '"valid_value"'}},
    );

    expect(
      screen.getByRole('textbox', {name: 'new-variables[0].name'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('textbox', {name: 'new-variables[0].value'}),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', {name: 'Change route'}));

    expect(
      await screen.findByText(/Task has no Variables/),
    ).toBeInTheDocument();
  });
});
