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
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {FormJS} from './index';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {rest} from 'msw';
import noop from 'lodash/noop';
import * as formMocks from 'modules/mock-schema/mocks/form';
import * as variableMocks from 'modules/mock-schema/mocks/variables';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {ReactQueryProvider} from 'modules/ReactQueryProvider';

const MOCK_FORM_ID = 'form-0';
const MOCK_PROCESS_DEFINITION_KEY = 'process';
const MOCK_TASK_ID = 'task-0';
const REQUESTED_VARIABLES = ['myVar', 'isCool'];
const DYNAMIC_FORM_REQUESTED_VARIABLES = ['radio_field', 'radio_field_options'];

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => (
  <ReactQueryProvider>
    <MockThemeProvider>{children}</MockThemeProvider>
  </ReactQueryProvider>
);

function areArraysEqual(firstArray: unknown[], secondArray: unknown[]) {
  return (
    firstArray.length === secondArray.length &&
    firstArray
      .map((item) => secondArray.includes(item))
      .every((result) => result)
  );
}

describe('<FormJS />', () => {
  beforeAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    nodeMockServer.use(
      rest.get('/v1/forms/:formId', (_, res, ctx) => {
        return res.once(ctx.json(formMocks.form));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
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
      rest.post('/v1/tasks/:taskId/variables/search', async (req, res, ctx) => {
        const body = await req.json();
        if (areArraysEqual(REQUESTED_VARIABLES, body.variableNames)) {
          return res.once(ctx.json(variableMocks.variables));
        }

        return res(
          ctx.json([
            {
              message: 'Invalid variables',
            },
          ]),
          ctx.status(404),
        );
      }),
    );

    render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={unassignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
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
    expect(screen.getByLabelText(/my variable/i)).toHaveAttribute('readonly');
    expect(screen.getByLabelText(/is cool\?/i)).toHaveAttribute('readonly');
    expect(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    ).toBeDisabled();
  });

  it('should render form for assigned task', async () => {
    nodeMockServer.use(
      rest.post('/v1/tasks/:taskId/variables/search', async (req, res, ctx) => {
        const body = await req.json();
        if (areArraysEqual(REQUESTED_VARIABLES, body.variableNames)) {
          return res.once(ctx.json(variableMocks.variables));
        }

        return res(
          ctx.json([
            {
              message: 'Invalid variables',
            },
          ]),
          ctx.status(404),
        );
      }),
    );

    render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={assignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
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
      rest.post('/v1/tasks/:taskId/variables/search', async (req, res, ctx) => {
        const body = await req.json();
        if (areArraysEqual(REQUESTED_VARIABLES, body.variableNames)) {
          return res.once(ctx.json(variableMocks.variables));
        }

        return res(
          ctx.json([
            {
              message: 'Invalid variables',
            },
          ]),
          ctx.status(404),
        );
      }),
    );

    render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={assignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
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
      rest.post('/v1/tasks/:taskId/variables/search', async (req, res, ctx) => {
        const body = await req.json();
        if (areArraysEqual(REQUESTED_VARIABLES, body.variableNames)) {
          return res.once(ctx.json(variableMocks.variables));
        }

        return res(
          ctx.json([
            {
              message: 'Invalid variables',
            },
          ]),
          ctx.status(404),
        );
      }),
    );

    const mockOnSubmit = jest.fn();
    const {user} = render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={assignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
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
      rest.post('/v1/tasks/:taskId/variables/search', async (req, res, ctx) => {
        const body = await req.json();
        if (areArraysEqual(REQUESTED_VARIABLES, body.variableNames)) {
          return res.once(ctx.json(variableMocks.variables));
        }

        return res(
          ctx.json([
            {
              message: 'Invalid variables',
            },
          ]),
          ctx.status(404),
        );
      }),
    );

    const mockOnSubmit = jest.fn();
    const {user} = render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={assignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
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

  it('should render a prefilled dynamic form', async () => {
    nodeMockServer.use(
      rest.get('/v1/forms/:formId', (_, res, ctx) => {
        return res(ctx.json(formMocks.dynamicForm));
      }),
      rest.post('/v1/tasks/:taskId/variables/search', async (req, res, ctx) => {
        const body = await req.json();

        if (
          areArraysEqual(DYNAMIC_FORM_REQUESTED_VARIABLES, body.variableNames)
        ) {
          return res.once(ctx.json(variableMocks.dynamicFormVariables));
        }

        return res(
          ctx.json([
            {
              message: 'Invalid variables',
            },
          ]),
          ctx.status(404),
        );
      }),
    );

    render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={assignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
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
