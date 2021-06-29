/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {MockedResponse} from '@apollo/client/testing';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {
  claimedTaskWithForm,
  unclaimedTaskWithForm,
} from 'modules/mock-schema/mocks/task';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {mockGetForm} from 'modules/queries/get-form';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {FormJS} from './index';
import {
  mockGetSelectedVariables,
  mockGetSelectedVariablesEmptyVariables,
} from 'modules/queries/get-selected-variables';

function createWrapper(mocks: MockedResponse[] = []) {
  const Wrapper: React.FC = ({children}) => (
    <MockedApolloProvider mocks={[mockGetCurrentUser, mockGetForm, ...mocks]}>
      <MockThemeProvider>{children}</MockThemeProvider>
    </MockedApolloProvider>
  );

  return Wrapper;
}

describe('<FormJS />', () => {
  it('should render form for unclaimed task', async () => {
    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={unclaimedTaskWithForm()}
        onSubmit={() => Promise.resolve()}
      />,
      {
        wrapper: createWrapper([mockGetSelectedVariables()]),
      },
    );

    expect(await screen.findByLabelText(/my variable/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/is cool\?/i)).toBeInTheDocument();
    expect(screen.getAllByRole('textbox')).toHaveLength(2);
    expect(screen.getByLabelText(/my variable/i)).toBeDisabled();
    expect(screen.getByLabelText(/is cool\?/i)).toBeDisabled();
    expect(
      screen.queryByRole('button', {
        name: /complete task/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should render form for claimed task', async () => {
    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={claimedTaskWithForm()}
        onSubmit={() => Promise.resolve()}
      />,
      {
        wrapper: createWrapper([mockGetSelectedVariables()]),
      },
    );

    expect(await screen.findByLabelText(/my variable/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/is cool\?/i)).toBeInTheDocument();
    expect(screen.getAllByRole('textbox')).toHaveLength(2);
    expect(screen.getByLabelText(/my variable/i)).toBeEnabled();
    expect(screen.getByLabelText(/is cool\?/i)).toBeEnabled();
    expect(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    ).toBeInTheDocument();
  });

  it('should render a prefilled form', async () => {
    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={claimedTaskWithForm()}
        onSubmit={() => Promise.resolve()}
      />,
      {
        wrapper: createWrapper([mockGetSelectedVariables()]),
      },
    );

    expect(await screen.findByLabelText(/my variable/i)).toHaveValue('0001');
    expect(screen.getByLabelText(/is cool\?/i)).toHaveValue('yes');
    expect(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    ).toBeInTheDocument();
  });

  it('should disable form submission', async () => {
    const {rerender} = render(
      <FormJS
        key="0"
        id="form-0"
        processDefinitionId="process"
        task={claimedTaskWithForm()}
        onSubmit={() => Promise.resolve()}
      />,
      {
        wrapper: createWrapper([
          mockGetSelectedVariablesEmptyVariables(),
          mockGetSelectedVariables('1'),
        ]),
      },
    );

    expect(await screen.findByText('Field is required.')).toBeInTheDocument();

    rerender(
      <FormJS
        key="1"
        id="form-0"
        processDefinitionId="process"
        task={claimedTaskWithForm('1')}
        onSubmit={() => Promise.resolve()}
      />,
    );

    userEvent.clear(await screen.findByDisplayValue('0001'));

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /complete task/i,
        }),
      ).toBeDisabled(),
    );
  });

  it('should submit prefilled form', async () => {
    const mockOnSubmit = jest.fn();
    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={claimedTaskWithForm()}
        onSubmit={mockOnSubmit}
      />,
      {
        wrapper: createWrapper([mockGetSelectedVariables()]),
      },
    );

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /complete task/i,
        }),
      ).toBeEnabled(),
    );

    userEvent.click(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    );

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith([
        {
          name: 'myVar',
          value: '"0001"',
        },
        {
          name: 'isCool',
          value: '"yes"',
        },
      ]),
    );
  });

  it('should submit edited form', async () => {
    const mockOnSubmit = jest.fn();
    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={claimedTaskWithForm()}
        onSubmit={mockOnSubmit}
      />,
      {
        wrapper: createWrapper([mockGetSelectedVariables()]),
      },
    );

    userEvent.clear(await screen.findByLabelText(/my variable/i));
    userEvent.type(screen.getByLabelText(/my variable/i), 'new value');
    userEvent.click(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    );

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith(
        expect.arrayContaining([
          {
            name: 'isCool',
            value: '"yes"',
          },
          {
            name: 'myVar',
            value: '"new value"',
          },
        ]),
      ),
    );
  });
});
