/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'common/testing/testing-library';
import {assignedTaskWithForm, unassignedTaskWithForm} from 'v1/mocks/task';
import {FormJS} from './index';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {http, HttpResponse} from 'msw';
import noop from 'lodash/noop';
import * as formMocks from 'v1/mocks/form';
import * as variableMocks from 'v1/mocks/variables';
import * as userMocks from 'common/mocks/current-user';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';

const MOCK_FORM_ID = 'form-0';
const MOCK_PROCESS_DEFINITION_KEY = 'process';
const MOCK_TASK_ID = 'task-0';
const REQUESTED_VARIABLES = ['myVar', 'isCool'];
const DYNAMIC_FORM_REQUESTED_VARIABLES = ['radio_field', 'radio_field_options'];

type VariableSearchRequestBody = {
  includeVariables?: Array<{
    name: string;
    alwaysReturnFullValue: boolean;
  }>;
};

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>{children}</QueryClientProvider>
  );

  return Wrapper;
};

function arraysContainSameValues(
  firstArray: unknown[],
  secondArray: unknown[],
) {
  return (
    firstArray.length === secondArray.length &&
    firstArray
      .map((item) => secondArray.includes(item))
      .every((result) => result)
  );
}

function hasRequestedVariables(
  req: VariableSearchRequestBody,
  expectedVariableNames: string[],
) {
  const requestedVariables = req.includeVariables ?? [];
  const names = requestedVariables.map((variable) => variable.name);
  return arraysContainSameValues(expectedVariableNames, names);
}

describe('<FormJS />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v1/forms/:formId',
        () => {
          return HttpResponse.json(formMocks.form);
        },
        {once: true},
      ),
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
    );
  });

  it('should render form for unassigned task', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (
            hasRequestedVariables(await request.json(), REQUESTED_VARIABLES)
          ) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 404,
            },
          );
        },
        {once: true},
      ),
    );

    render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={unassignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitFor(() =>
      expect(screen.getByLabelText(/my variable/i)).toHaveValue('0001'),
    );

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
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (
            hasRequestedVariables(await request.json(), REQUESTED_VARIABLES)
          ) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 404,
            },
          );
        },
        {once: true},
      ),
    );

    render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={assignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
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
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (
            hasRequestedVariables(await request.json(), REQUESTED_VARIABLES)
          ) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 404,
            },
          );
        },
        {once: true},
      ),
    );

    render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={assignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
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
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (
            hasRequestedVariables(await request.json(), REQUESTED_VARIABLES)
          ) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 404,
            },
          );
        },
        {once: true},
      ),
    );

    const mockOnSubmit = vi.fn();
    const {user} = render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={assignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
        onSubmit={mockOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitFor(() =>
      expect(screen.getByLabelText(/my variable/i)).toHaveValue('0001'),
    );

    await user.tab();

    await user.click(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    );

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
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (
            hasRequestedVariables(await request.json(), REQUESTED_VARIABLES)
          ) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 404,
            },
          );
        },
        {once: true},
      ),
    );

    const mockOnSubmit = vi.fn();
    const {user} = render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={assignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
        onSubmit={mockOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
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

  // TODO: #3957
  it('should render a prefilled dynamic form', async () => {
    nodeMockServer.use(
      http.get('/v1/forms/:formId', () => {
        return HttpResponse.json(formMocks.dynamicForm);
      }),
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (
            hasRequestedVariables(
              await request.json(),
              DYNAMIC_FORM_REQUESTED_VARIABLES,
            )
          ) {
            return HttpResponse.json(variableMocks.dynamicFormVariables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 404,
            },
          );
        },
        {once: true},
      ),
    );

    render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={assignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
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

  it("should show an error message if variables can't be loaded", async () => {
    nodeMockServer.use(
      http.post('/v1/tasks/:taskId/variables/search', () => {
        return HttpResponse.json(null, {
          status: 404,
        });
      }),
    );
    render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={assignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(
      screen.getByText(
        'We could not fetch the task variables. Please try again or contact your Tasklist administrator.',
      ),
    ).toBeInTheDocument();
  });

  it('should enable completion button when form has no inputs', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/forms/:formId',
        () => {
          return HttpResponse.json(formMocks.noInputForm);
        },
        {
          once: true,
        },
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json([]);
        },
        {once: true},
      ),
    );

    render(
      <FormJS
        id={MOCK_FORM_ID}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
        task={assignedTaskWithForm(MOCK_TASK_ID)}
        user={userMocks.currentUser}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('foo')).toBeInTheDocument();

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /complete task/i,
        }),
      ).toBeEnabled(),
    );
  });
});
