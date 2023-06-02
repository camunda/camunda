/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {StartProcessFromForm} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {ReactQueryProvider} from 'modules/ReactQueryProvider';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {rest} from 'msw';
import * as formMocks from 'modules/mock-schema/mocks/form';

const getWrapper = ({
  initialEntries,
}: Pick<React.ComponentProps<typeof MemoryRouter>, 'initialEntries'>) => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <ReactQueryProvider>
      <MockThemeProvider>
        <MemoryRouter initialEntries={initialEntries}>
          <Routes>
            <Route path="/new/:bpmnProcessId" element={children} />
          </Routes>
        </MemoryRouter>
      </MockThemeProvider>
    </ReactQueryProvider>
  );

  return Wrapper;
};

describe('<StartProcessFromForm />', () => {
  it('should submit form', async () => {
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

    await waitForElementToBeRemoved(screen.getByTestId('public-form-skeleton'));

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
    await user.click(
      screen.getByRole('button', {
        name: 'Save',
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

    await waitForElementToBeRemoved(screen.getByTestId('public-form-skeleton'));

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
    await user.click(
      screen.getByRole('button', {
        name: 'Save',
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
