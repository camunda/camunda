/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act, render, screen, waitFor} from 'common/testing/testing-library';
import {Variables} from './index';
import * as taskMocks from 'v2/mocks/task';
import * as variableMocks from 'v2/mocks/variables';
import * as userMocks from 'common/mocks/current-user';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {http, HttpResponse} from 'msw';
import noop from 'lodash/noop';
import {currentUser} from 'common/mocks/current-user';
import type {Variable} from '@vzeta/camunda-api-zod-schemas/8.8';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';
import {DEFAULT_TENANT_ID} from 'common/multitenancy/constants';

const {getQueryVariablesResponseMock} = variableMocks;

type VariableSearchRequestBody = {
  page?: {limit?: number};
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

function isRequestingAllVariables(req: VariableSearchRequestBody) {
  return req.page?.limit === 1000;
}

describe('<Variables />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
    );

    vi.useFakeTimers({
      shouldAdvanceTime: true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("should show an error message if variables can't be loaded", async () => {
    nodeMockServer.use(
      http.post('/v2/user-tasks/:userTaskKey/variables/search', () => {
        return HttpResponse.json(null, {status: 404});
      }),
    );

    render(
      <Variables
        task={taskMocks.unassignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
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

  it('should show existing variables for unassigned tasks', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    render(
      <Variables
        task={taskMocks.unassignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('myVar')).toBeInTheDocument();
    expect(screen.getByText('"0001"')).toBeInTheDocument();
    expect(screen.getByText('isCool')).toBeInTheDocument();
    expect(screen.getByText('"yes"')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('should show a message when the tasks has no variables', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(getQueryVariablesResponseMock([]));
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {status: 400},
          );
        },
      ),
    );

    render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByText('Task has no variables'),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('variables-form-table'),
    ).not.toBeInTheDocument();
  });

  it('should edit variable', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );
    const newVariableValue = '"changedValue"';

    expect(await screen.findByDisplayValue('"0001"')).toBeInTheDocument();

    await user.clear(screen.getByDisplayValue('"0001"'));
    await user.type(screen.getByLabelText('myVar'), newVariableValue);

    expect(screen.getByDisplayValue(newVariableValue)).toBeInTheDocument();

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );
  });

  it('should add two variables and remove one', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.click(screen.getByText(/add variable/i));

    expect(screen.getAllByPlaceholderText(/^name$/i)).toHaveLength(2);
    expect(screen.getAllByPlaceholderText(/^value$/i)).toHaveLength(2);
    expect(screen.getByLabelText(/1st variable name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/1st variable value/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/2nd variable name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/2nd variable value/i)).toBeInTheDocument();

    expect(await screen.findByText(/complete task/i)).toBeDisabled();

    await user.click(screen.getByLabelText(/remove 2nd new variable/i));

    expect(screen.getAllByPlaceholderText(/^name$/i)).toHaveLength(1);
    expect(screen.getAllByPlaceholderText(/^value$/i)).toHaveLength(1);
    expect(screen.getByLabelText(/1st variable name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/1st variable value/i)).toBeInTheDocument();

    expect(
      screen.queryByLabelText(/2nd variable name/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/2nd variable value/i),
    ).not.toBeInTheDocument();
  });

  it('should add variable on task without variables', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));

    expect(screen.getByLabelText(/1st variable name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/1st variable value/i)).toBeInTheDocument();

    expect(await screen.findByText(/complete task/i)).toBeDisabled();
  });

  it('should validate an empty variable name', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '"valid_value"',
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).toHaveAccessibleDescription(/name has to be filled/i),
    );
  });

  it('should validate an invalid variable name', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.type(screen.getByLabelText(/1st variable name/i), '"');
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '"valid_value"',
    );

    act(() => {
      vi.runOnlyPendingTimers();
    });

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).toHaveAccessibleDescription(/name is invalid/i),
    );

    await user.clear(screen.getByLabelText(/1st variable name/i));

    act(() => {
      vi.runOnlyPendingTimers();
    });

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).not.toHaveAccessibleDescription(/name is invalid/i),
    );

    await user.type(screen.getByLabelText(/1st variable name/i), 'test ');

    act(() => {
      vi.runOnlyPendingTimers();
    });

    expect(await screen.findByText(/name is invalid/i)).toBeInTheDocument();
  });

  it('should validate an empty variable value', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.type(screen.getByLabelText(/1st variable name/i), 'valid_name');

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable value/i),
      ).toHaveAccessibleDescription(/value has to be json or a literal/i),
    );
  });

  it('should validate an invalid variable value', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));

    await user.type(
      screen.getByLabelText(/1st variable value/i),
      'invalid_value}}}',
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).toHaveAccessibleDescription(/name has to be filled/i),
    );
    expect(
      screen.getByLabelText(/1st variable value/i),
    ).toHaveAccessibleDescription(/value has to be json or a literal/i);
  });

  it('should not validate valid variables', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.type(screen.getByLabelText(/1st variable name/i), 'valid_name');
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '"valid_value"',
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    expect(
      screen.queryByTitle(
        /name has to filled and value has to be json or a literal/i,
      ),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(/name has to be filled/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(/value has to be json or a literal/i),
    ).not.toBeInTheDocument();
  });

  it('should handle submission', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const mockOnSubmit = vi.fn();
    const {rerender, user} = render(
      <Variables
        key="id_0"
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    act(() => {
      vi.runOnlyPendingTimers();
    });
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    await user.click(await screen.findByText(/complete task/i));

    expect(await screen.findByText('Completed')).toBeInTheDocument();
    expect(mockOnSubmit).toHaveBeenCalledTimes(1);
    expect(mockOnSubmit).toHaveBeenNthCalledWith(1, {});

    await user.click(await screen.findByText(/add variable/i));
    await user.type(screen.getByLabelText(/1st variable name/i), 'var');
    await user.type(screen.getByLabelText(/1st variable value/i), '1');

    act(() => {
      vi.runOnlyPendingTimers();
    });
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    await user.click(screen.getByText(/complete task/i));

    expect(await screen.findByText('Completed')).toBeInTheDocument();
    expect(mockOnSubmit).toHaveBeenCalledTimes(2);
    expect(mockOnSubmit).toHaveBeenNthCalledWith(2, {
      var: 1,
    });

    rerender(
      <Variables
        key="id_1"
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
    );

    expect(await screen.findByLabelText('myVar')).toBeInTheDocument();

    await user.click(await screen.findByText(/add variable/i));
    await user.type(screen.getByLabelText(/1st variable name/i), 'name');
    await user.type(screen.getByLabelText(/1st variable value/i), '"Jon"');

    act(() => {
      vi.runOnlyPendingTimers();
    });
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    await user.click(screen.getByText(/complete task/i));

    expect(await screen.findByText('Completed')).toBeInTheDocument();
    expect(mockOnSubmit).toHaveBeenCalledTimes(3);
    expect(mockOnSubmit).toHaveBeenNthCalledWith(3, {
      name: 'Jon',
    });
  });

  it('should change variable and complete task', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const mockOnSubmit = vi.fn();

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.clear(await screen.findByLabelText('myVar'));
    await user.type(screen.getByLabelText('myVar'), '"newValue"');
    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    await user.click(screen.getByText(/complete task/i));

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith({
        myVar: 'newValue',
      }),
    );
  });

  it('should add new variable and complete task', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const mockOnSubmit = vi.fn();

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.type(
      screen.getByLabelText(/1st variable name/i),
      'newVariableName',
    );
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '"newVariableValue"',
    );

    act(() => {
      vi.runOnlyPendingTimers();
    });
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );
    await user.click(screen.getByText(/complete task/i));

    act(() => {
      vi.runOnlyPendingTimers();
    });
    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith({
        newVariableName: 'newVariableValue',
      }),
    );
  });

  it('should hide add variable button on completed tasks', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    render(
      <Variables
        task={taskMocks.completedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('Variables')).toBeInTheDocument();

    expect(screen.queryByText(/add variable/i)).not.toBeInTheDocument();
  });

  it('should disable submit button on form errors for existing variables', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.type(await screen.findByLabelText('myVar'), '{{ invalid value');

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByLabelText('myVar')).toHaveAccessibleDescription(
        /value has to be json or a literal/i,
      ),
    );

    expect(screen.getByText(/complete task/i)).toBeDisabled();
  });

  it('should disable submit button on form errors for new variables', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '{{ invalid value',
    );

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable value/i),
      ).toHaveAccessibleDescription(/value has to be json or a literal/i),
    );

    expect(screen.getByText(/complete task/i)).toBeDisabled();
  });

  it('should disable completion button', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));

    expect(await screen.findByText(/complete task/i)).toBeDisabled();
  });

  it('should hide completion button on completed tasks', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    render(
      <Variables
        task={taskMocks.completedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('Variables')).toBeInTheDocument();

    // jest-dom is not parsing the visibility properly so need to check the class
    expect(screen.queryByText(/complete task/i)).toHaveClass('hide');
  });

  it('should complete a task with a truncated variable', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.truncatedVariables),
            );
          }
          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {status: 400},
          );
        },
      ),
      http.get(
        '/v2/variables/:variableKey',
        () => {
          return HttpResponse.json(variableMocks.fullVariable());
        },
        {once: true},
      ),
    );
    const mockOnSubmit = vi.fn();
    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByDisplayValue('"000')).toBeInTheDocument();

    user.click(screen.getByDisplayValue('"000'));

    expect(await screen.findByDisplayValue('"0001"')).toBeInTheDocument();

    await user.clear(screen.getByDisplayValue('"0001"'));
    await user.type(screen.getByLabelText('myVar'), '"newVariableValue"');
    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );
    await user.click(screen.getByText(/complete task/i));

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith({
        myVar: 'newVariableValue',
      }),
    );
  });

  it('should preserve full value', async () => {
    const mockVariable: Variable = {
      variableKey: '1-myVar',
      value: '"1112"',
      name: 'myVar1',
      isTruncated: false,
      tenantId: DEFAULT_TENANT_ID,
      scopeKey: '1-myVar',
      processInstanceKey: '1-myVar',
    };
    const mockNewValue = '"new-value"';
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.truncatedVariables),
            );
          }
          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {status: 400},
          );
        },
      ),
      http.get<{variableKey: string}>(
        '/v2/variables/:variableKey',
        ({params}) => {
          switch (params.variableKey) {
            case '0-myVar':
              return HttpResponse.json(variableMocks.fullVariable());
            case '1-myVar':
              return HttpResponse.json(
                variableMocks.fullVariable(mockVariable),
              );

            default:
              return HttpResponse.error();
          }
        },
      ),
    );
    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByDisplayValue('"000')).toBeInTheDocument();
    expect(screen.getByDisplayValue('"111')).toBeInTheDocument();

    await user.click(screen.getByDisplayValue('"000'));

    const firstVariableValueTextarea =
      await screen.findByDisplayValue('"0001"');
    expect(firstVariableValueTextarea).toBeInTheDocument();
    expect(screen.getByDisplayValue('"111')).toBeInTheDocument();

    await user.clear(firstVariableValueTextarea);
    await user.type(firstVariableValueTextarea, mockNewValue);
    await user.click(screen.getByDisplayValue('"111'));

    expect(
      await screen.findByDisplayValue(mockVariable.value),
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue(mockNewValue)).toBeInTheDocument();
  });

  it('should show the preview value of a truncated variable', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.truncatedVariables),
            );
          }
          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {status: 400},
          );
        },
      ),
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.truncatedVariables),
            );
          }
          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {status: 400},
          );
        },
      ),
    );
    const mockOnSubmit = vi.fn();
    const {rerender} = render(
      <Variables
        task={taskMocks.unassignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('"000...')).toBeInTheDocument();

    rerender(
      <Variables
        task={taskMocks.completedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
    );

    expect(await screen.findByText('"000...')).toBeInTheDocument();
  });

  describe('Duplicate variable validations', () => {
    it('should display error if name is the same with one of the existing variables', async () => {
      nodeMockServer.use(
        http.post<never, VariableSearchRequestBody>(
          '/v2/user-tasks/:userTaskKey/variables/search',
          async ({request}) => {
            if (isRequestingAllVariables(await request.json())) {
              return HttpResponse.json(
                getQueryVariablesResponseMock(variableMocks.variables),
              );
            }
            return HttpResponse.json(
              [
                {
                  message: 'Invalid variables',
                },
              ],
              {status: 400},
            );
          },
        ),
      );

      const {user} = render(
        <Variables
          task={taskMocks.assignedTask()}
          user={currentUser}
          onSubmit={() => Promise.resolve()}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />,
        {
          wrapper: getWrapper(),
        },
      );

      await user.click(await screen.findByText(/add variable/i));
      await user.type(screen.getByLabelText(/1st variable name/i), 'myVar');

      vi.runOnlyPendingTimers();
      await waitFor(() =>
        expect(
          screen.getByLabelText(/1st variable value/i),
        ).toHaveAccessibleDescription(/value has to be json or a literal/i),
      );
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).toHaveAccessibleDescription(/name must be unique/i);
    });

    it('should display duplicate name error on last edited variable', async () => {
      nodeMockServer.use(
        http.post(
          '/v2/user-tasks/:userTaskKey/variables/search',
          () => {
            return HttpResponse.json(
              getQueryVariablesResponseMock(variableMocks.variables),
            );
          },
          {once: true},
        ),
      );

      const {user} = render(
        <Variables
          task={taskMocks.assignedTask()}
          user={currentUser}
          onSubmit={() => Promise.resolve()}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />,
        {
          wrapper: getWrapper(),
        },
      );

      await user.click(await screen.findByText(/add variable/i));
      await user.type(screen.getByLabelText(/1st variable name/i), 'myVar2');

      expect(
        screen.getByLabelText(/1st variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);

      await user.click(screen.getByText(/add variable/i));
      await user.type(screen.getByLabelText(/2nd variable name/i), 'myVar2');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(
          screen.getByLabelText(/2nd variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );

      await user.type(screen.getByLabelText(/2nd variable name/i), 'foo');

      await waitFor(() =>
        expect(
          screen.getByLabelText(/2nd variable name/i),
        ).not.toHaveAccessibleDescription(/name must be unique/i),
      );

      await user.type(screen.getByLabelText(/1st variable name/i), 'foo');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(
          screen.getByLabelText(/1st variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );
      expect(
        screen.getByLabelText(/2nd variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);
    });

    it('should display error if duplicate name is used and immediately started typing on to the value field', async () => {
      nodeMockServer.use(
        http.post<never, VariableSearchRequestBody>(
          '/v2/user-tasks/:userTaskKey/variables/search',
          async ({request}) => {
            if (isRequestingAllVariables(await request.json())) {
              return HttpResponse.json(
                getQueryVariablesResponseMock(variableMocks.variables),
              );
            }
            return HttpResponse.json(
              [
                {
                  message: 'Invalid variables',
                },
              ],
              {status: 400},
            );
          },
        ),
      );

      const {user} = render(
        <Variables
          task={taskMocks.assignedTask()}
          user={currentUser}
          onSubmit={() => Promise.resolve()}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />,
        {
          wrapper: getWrapper(),
        },
      );

      await user.click(await screen.findByText(/add variable/i));

      await user.type(screen.getByLabelText(/1st variable name/i), 'myVar2');
      await user.type(screen.getByLabelText(/1st variable value/i), '1');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(screen.getByText(/complete task/i)).toBeEnabled(),
      );

      await user.click(await screen.findByText(/add variable/i));

      await user.type(screen.getByLabelText(/2nd variable name/i), 'myVar2');
      await user.type(screen.getByLabelText(/2nd variable value/i), '2');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(
          screen.getByLabelText(/2nd variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );

      expect(
        screen.getByLabelText(/1st variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);
    });

    it('should continue to display existing duplicate name error', async () => {
      nodeMockServer.use(
        http.post<never, VariableSearchRequestBody>(
          '/v2/user-tasks/:userTaskKey/variables/search',
          async ({request}) => {
            if (isRequestingAllVariables(await request.json())) {
              return HttpResponse.json(
                getQueryVariablesResponseMock(variableMocks.variables),
              );
            }
            return HttpResponse.json(
              [
                {
                  message: 'Invalid variables',
                },
              ],
              {status: 400},
            );
          },
        ),
      );

      const {user} = render(
        <Variables
          task={taskMocks.assignedTask()}
          user={currentUser}
          onSubmit={() => Promise.resolve()}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />,
        {
          wrapper: getWrapper(),
        },
      );

      await user.click(await screen.findByText(/add variable/i));

      await user.type(screen.getByLabelText(/1st variable name/i), 'myVar2');
      await user.type(screen.getByLabelText(/1st variable value/i), '1');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(screen.getByText(/complete task/i)).toBeEnabled(),
      );

      await user.click(screen.getByText(/add variable/i));

      await user.type(screen.getByLabelText(/2nd variable name/i), 'myVar2');
      await user.type(screen.getByLabelText(/2nd variable value/i), '2');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(
          screen.getByLabelText(/2nd variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );

      await user.click(screen.getByText(/add variable/i));

      await user.type(screen.getByLabelText(/3rd variable name/i), 'myVar2');
      await user.type(screen.getByLabelText(/3rd variable value/i), '3');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(
          screen.getByLabelText(/3rd variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );

      expect(
        screen.getByLabelText(/2nd variable name/i),
      ).toHaveAccessibleDescription(/name must be unique/i);
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);
    });
  });

  it('should handle variables with dots in names correctly', async () => {
    const variableWithDot = {
      variableKey: 'var.with.dots',
      name: 'var.with.dots',
      value: '"dotted-value"',
      isTruncated: false,
      tenantId: DEFAULT_TENANT_ID,
      scopeKey: 'var.with.dots',
      processInstanceKey: 'var.with.dots',
    } satisfies Variable;

    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v2/user-tasks/:userTaskKey/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(
              getQueryVariablesResponseMock([variableWithDot]),
            );
          }
          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {status: 400},
          );
        },
      ),
    );

    const mockOnSubmit = vi.fn();
    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('var.with.dots')).toBeInTheDocument();
    expect(screen.getByDisplayValue('"dotted-value"')).toBeInTheDocument();

    await user.clear(await screen.findByLabelText('var.with.dots'));
    await user.type(screen.getByLabelText('var.with.dots'), '"updated-value"');

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    await user.click(screen.getByText(/complete task/i));

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith({
        'var.with.dots': 'updated-value',
      }),
    );
  });
});
