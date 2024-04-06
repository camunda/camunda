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

import {
  fireEvent,
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {Component} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import * as formMocks from 'modules/mock-schema/mocks/form';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {Process, Variable} from 'modules/types';

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
      http.get('/v1/external/process/:bpmnProcessId/form', () =>
        HttpResponse.json(formMocks.form),
      ),
      http.patch<Pick<Process, 'bpmnProcessId'>, {variables: Variable[]}>(
        '/v1/external/process/:bpmnProcessId/start',
        async ({request, params}) => {
          const {bpmnProcessId} = params;
          const {variables} = await request.json();

          if (bpmnProcessId !== 'foo' || !Array.isArray(variables)) {
            return new HttpResponse(null, {status: 500});
          }

          return HttpResponse.json({id: 'foo-instance'});
        },
        {once: true},
      ),
    );

    const {user} = render(<Component />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('public-form-skeleton'),
    );

    await user.type(
      screen.getByRole('textbox', {name: /my variable/i}),
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
      http.get('/v1/external/process/:bpmnProcessId/form', () =>
        HttpResponse.json(formMocks.form),
      ),
    );

    render(<Component />, {
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
        screen.getByRole('textbox', {name: /my variable/i}),
      ).toHaveAccessibleDescription('Field is required.'),
    );
  });

  it('should handle a submit error', async () => {
    nodeMockServer.use(
      http.get('/v1/external/process/:bpmnProcessId/form', () =>
        HttpResponse.json(formMocks.form),
      ),
      http.patch(
        '/v1/external/process/:bpmnProcessId/start',
        () => new HttpResponse(null, {status: 500}),
        {once: true},
      ),
    );

    const {user} = render(<Component />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('public-form-skeleton'),
    );

    await user.type(
      screen.getByRole('textbox', {name: /my variable/i}),
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
      http.get(
        '/v1/external/process/:bpmnProcessId/form',
        () => new HttpResponse(null, {status: 500}),
      ),
    );

    render(<Component />, {
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
      http.get('/v1/external/process/:bpmnProcessId/form', () =>
        HttpResponse.json(formMocks.invalidForm),
      ),
    );

    render(<Component />, {
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
