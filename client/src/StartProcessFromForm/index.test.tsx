/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  fireEvent,
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {StartProcessFromForm} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {rest} from 'msw';
import * as formMocks from 'modules/mock-schema/mocks/form';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';

const getWrapper = ({
  initialEntries,
}: Pick<React.ComponentProps<typeof MemoryRouter>, 'initialEntries'>) => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <MockThemeProvider>
        <MemoryRouter initialEntries={initialEntries}>
          <Routes>
            <Route path="/new/:bpmnProcessId" element={children} />
          </Routes>
        </MemoryRouter>
      </MockThemeProvider>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('<StartProcessFromForm />', () => {
  it('should submit form', async () => {
    vi.useFakeTimers({
      shouldAdvanceTime: true,
    });
    nodeMockServer.use(
      rest.get('/v1/external/process/:bpmnProcessId/form', (_, res, ctx) =>
        res(ctx.json(formMocks.form)),
      ),
      rest.patch(
        '/v1/external/process/:bpmnProcessId/start',
        async (req, res, ctx) => {
          const {bpmnProcessId} = req.params;
          const {variables} = await req.json();

          if (bpmnProcessId !== 'foo' || !Array.isArray(variables)) {
            return res.once(ctx.status(500));
          }

          return res.once(ctx.json({id: 'foo-instance'}));
        },
      ),
    );

    const {user} = render(<StartProcessFromForm />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('public-form-skeleton'),
    );

    await user.type(
      screen.getByRole('textbox', {name: /my variable \*/i}),
      'var1',
    );
    vi.runOnlyPendingTimers();
    await user.type(
      screen.getByRole('textbox', {
        name: /is cool\?/i,
      }),
      'Yes',
    );
    vi.runOnlyPendingTimers();
    fireEvent.click(
      screen.getByRole('button', {
        name: 'Submit',
      }),
    );

    expect(
      await screen.findByRole('heading', {
        name: 'Success!',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Your form has been successfully submitted.You can close this window now.',
      ),
    ).toBeInTheDocument();
    vi.useRealTimers();
  });

  it('should show validation error', async () => {
    nodeMockServer.use(
      rest.get('/v1/external/process/:bpmnProcessId/form', (_, res, ctx) =>
        res(ctx.json(formMocks.form)),
      ),
    );

    render(<StartProcessFromForm />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('public-form-skeleton'),
    );

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Submit',
      }),
    );

    await waitFor(() =>
      expect(
        screen.getByRole('textbox', {name: /my variable \*/i}),
      ).toHaveAccessibleDescription('Field is required.'),
    );
  });

  it('should handle a submit error', async () => {
    nodeMockServer.use(
      rest.get('/v1/external/process/:bpmnProcessId/form', (_, res, ctx) =>
        res(ctx.json(formMocks.form)),
      ),
      rest.patch('/v1/external/process/:bpmnProcessId/start', (_, res, ctx) =>
        res.once(ctx.status(500)),
      ),
    );

    const {user} = render(<StartProcessFromForm />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('public-form-skeleton'),
    );

    await user.type(
      screen.getByRole('textbox', {name: /my variable \*/i}),
      'var1',
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /is cool\?/i,
      }),
      'Yes',
    );
    fireEvent.click(
      screen.getByRole('button', {
        name: 'Submit',
      }),
    );

    expect(
      await screen.findByRole('heading', {
        name: 'Something went wrong',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Please try again later and reload the page.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Reload'})).toBeInTheDocument();
  });

  it('should show a request error message', async () => {
    nodeMockServer.use(
      rest.get('/v1/external/process/:bpmnProcessId/form', (_, res, ctx) =>
        res(ctx.status(500)),
      ),
    );

    render(<StartProcessFromForm />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    expect(
      await screen.findByRole('heading', {
        name: '404 - Page not found',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "We're sorry! The requested URL you're looking for could not be found.",
      ),
    ).toBeInTheDocument();
  });

  it('should show a bad form schema error message', async () => {
    nodeMockServer.use(
      rest.get('/v1/external/process/:bpmnProcessId/form', (_, res, ctx) =>
        res(ctx.json(formMocks.invalidForm)),
      ),
    );

    render(<StartProcessFromForm />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Invalid form',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Something went wrong and the form could not be displayed. Please contact your provider.',
      ),
    ).toBeInTheDocument();
  });
});
