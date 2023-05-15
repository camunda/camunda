/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {
  assignedTaskWithForm,
  unassignedTaskWithForm,
} from 'modules/mock-schema/mocks/task';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {mockGetDynamicForm, mockGetForm} from 'modules/queries/get-form';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {FormJS} from './index';
import {
  mockGetDynamicFormsVariables,
  mockGetSelectedVariables,
} from 'modules/queries/get-selected-variables';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {graphql} from 'msw';
import noop from 'lodash/noop';
import {convertToGraphqlTask} from 'modules/utils/convertToGraphqlTask';

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => (
  <ApolloProvider client={client}>
    <MockThemeProvider>{children}</MockThemeProvider>
  </ApolloProvider>
);

function areArraysEqual(firstArray: unknown[], secondArray: unknown[]) {
  return (
    firstArray.length === secondArray.length &&
    firstArray
      .map((item) => secondArray.includes(item))
      .every((result) => result)
  );
}

const REQUESTED_VARIABLES = ['myVar', 'isCool'];

describe('<FormJS />', () => {
  beforeAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    nodeMockServer.use(
      graphql.query('GetForm', (_, res, ctx) => {
        return res.once(ctx.data(mockGetForm.result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
    );
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should render form for unassigned task', async () => {
    nodeMockServer.use(
      graphql.query('GetSelectedVariables', (req, res, ctx) => {
        if (areArraysEqual(REQUESTED_VARIABLES, req.variables?.variableNames)) {
          return res(ctx.data(mockGetSelectedVariables().result.data));
        }

        return res(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
    );

    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={convertToGraphqlTask(unassignedTaskWithForm())}
        user={mockGetCurrentUser.currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() =>
      expect(screen.getByLabelText(/my variable/i)).toHaveValue('0001'),
    );
    jest.runOnlyPendingTimers();
    expect(screen.getByLabelText(/is cool\?/i)).toBeInTheDocument();
    expect(screen.getAllByRole('textbox')).toHaveLength(2);
    expect(screen.getByLabelText(/my variable/i)).toBeDisabled();
    expect(screen.getByLabelText(/is cool\?/i)).toBeDisabled();
    expect(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    ).toBeDisabled();
  });

  it('should render form for assigned task', async () => {
    nodeMockServer.use(
      graphql.query('GetSelectedVariables', (req, res, ctx) => {
        if (areArraysEqual(REQUESTED_VARIABLES, req.variables?.variableNames)) {
          return res(ctx.data(mockGetSelectedVariables().result.data));
        }

        return res(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
    );

    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={convertToGraphqlTask(assignedTaskWithForm())}
        user={mockGetCurrentUser.currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() =>
      expect(screen.getByLabelText(/my variable/i)).toHaveValue('0001'),
    );
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
    nodeMockServer.use(
      graphql.query('GetSelectedVariables', (req, res, ctx) => {
        if (areArraysEqual(REQUESTED_VARIABLES, req.variables?.variableNames)) {
          return res(ctx.data(mockGetSelectedVariables().result.data));
        }

        return res(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
    );

    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={convertToGraphqlTask(assignedTaskWithForm())}
        user={mockGetCurrentUser.currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() =>
      expect(screen.getByLabelText(/my variable/i)).toHaveValue('0001'),
    );
    expect(screen.getByLabelText(/is cool\?/i)).toHaveValue('yes');
    expect(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    ).toBeInTheDocument();
  });

  it('should submit prefilled form', async () => {
    nodeMockServer.use(
      graphql.query('GetSelectedVariables', (req, res, ctx) => {
        if (areArraysEqual(REQUESTED_VARIABLES, req.variables?.variableNames)) {
          return res(ctx.data(mockGetSelectedVariables().result.data));
        }

        return res(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
    );

    const mockOnSubmit = jest.fn();
    const {user} = render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={convertToGraphqlTask(assignedTaskWithForm())}
        user={mockGetCurrentUser.currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() =>
      expect(screen.getByLabelText(/my variable/i)).toHaveValue('0001'),
    );

    await user.click(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    );
    expect(screen.getByText('Completing task...')).toBeInTheDocument();
    expect(await screen.findByText('Completed')).toBeInTheDocument();

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
    nodeMockServer.use(
      graphql.query('GetSelectedVariables', (req, res, ctx) => {
        if (areArraysEqual(REQUESTED_VARIABLES, req.variables?.variableNames)) {
          return res(ctx.data(mockGetSelectedVariables().result.data));
        }

        return res(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
    );

    const mockOnSubmit = jest.fn();
    const {user} = render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={convertToGraphqlTask(assignedTaskWithForm())}
        user={mockGetCurrentUser.currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() =>
      expect(screen.getByLabelText(/my variable/i)).toHaveValue('0001'),
    );

    await user.clear(screen.getByLabelText(/my variable/i));
    await user.type(screen.getByLabelText(/my variable/i), 'new value');
    await user.click(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    );

    expect(screen.getByText('Completing task...')).toBeInTheDocument();
    expect(await screen.findByText('Completed')).toBeInTheDocument();

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

  it('should render a prefilled form', async () => {
    nodeMockServer.use(
      graphql.query('GetForm', (_, res, ctx) => {
        return res(ctx.data(mockGetDynamicForm.result.data));
      }),
      graphql.query('GetSelectedVariables', async (req, res, ctx) => {
        const body = await req.json();

        if (
          areArraysEqual(
            mockGetDynamicFormsVariables().request.variables.variableNames,
            body?.variables?.variableNames,
          )
        ) {
          return res(ctx.data(mockGetDynamicFormsVariables().result.data));
        }

        return res(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
    );

    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={convertToGraphqlTask(assignedTaskWithForm())}
        user={mockGetCurrentUser.currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByLabelText(/radio label 1/i)).toBeChecked();
    expect(screen.getByLabelText(/radio label 2/i)).toBeInTheDocument();
    expect(screen.getByText(/radio field/i)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    ).toBeInTheDocument();
  });
});
