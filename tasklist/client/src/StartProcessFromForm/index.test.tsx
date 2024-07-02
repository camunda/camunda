/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route path="/new/:bpmnProcessId" element={children} />
        </Routes>
      </MemoryRouter>
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

    const {user} = render(<Component />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('public-form-skeleton'),
    );

    await user.type(screen.getByRole('textbox', {name: /is cool/i}), 'var1');

    await user.click(
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
    vi.useFakeTimers({
      shouldAdvanceTime: true,
    });
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
        name: 'Something went wrong',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Please try again later and reload the page.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Reload'})).toBeInTheDocument();
    vi.useRealTimers();
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
