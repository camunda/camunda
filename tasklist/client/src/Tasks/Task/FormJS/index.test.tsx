/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {
  assignedTaskWithForm,
  unassignedTaskWithForm,
} from 'modules/mock-schema/mocks/task';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {FormJS} from './index';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import noop from 'lodash/noop';
import * as formMocks from 'modules/mock-schema/mocks/form';
import * as variableMocks from 'modules/mock-schema/mocks/variables';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';

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
    <QueryClientProvider client={mockClient}>
      <MockThemeProvider>{children}</MockThemeProvider>
    </QueryClientProvider>
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
        '/v1/internal/users/current',
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

  it('should enable completion buttton when form has no inputs', async () => {
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
