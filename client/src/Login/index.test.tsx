/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {Router} from 'react-router-dom';
import {createMemoryHistory} from 'history';

import {Login} from './index';
import {login} from 'modules/stores/login';
import {MockThemeProvider} from 'modules/theme/MockProvider';

const fetchMock = jest.spyOn(window, 'fetch');
const getFullYearMock = jest.spyOn(Date.prototype, 'getFullYear');
const historyMock = createMemoryHistory();
const Wrapper: React.FC = ({children}) => (
  <Router history={historyMock}>
    <MockThemeProvider>{children}</MockThemeProvider>
  </Router>
);

describe('<Login />', () => {
  afterEach(() => {
    fetchMock.mockClear();
    getFullYearMock.mockClear();
    login.reset();
  });

  afterAll(() => {
    fetchMock.mockRestore();
    getFullYearMock.mockRestore();
  });

  it('should redirect to the initial on success', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 204}));
    render(<Login />, {
      wrapper: Wrapper,
    });

    await userEvent.type(screen.getByPlaceholderText('Username'), 'demo');
    await userEvent.type(screen.getByPlaceholderText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(historyMock.location.pathname).toBe('/');
  });

  it('should show an error on failure', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 404}));
    render(<Login />, {
      wrapper: Wrapper,
    });

    await userEvent.type(screen.getByPlaceholderText('Username'), 'demo');
    await userEvent.type(screen.getByPlaceholderText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(
      screen.getByText('Username and Password do not match.'),
    ).toBeInTheDocument();
  });

  it.skip('should show a loading overlay while the login form is submitting', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 204}));

    render(<Login />, {
      wrapper: Wrapper,
    });

    await userEvent.type(screen.getByPlaceholderText('Username'), 'demo');
    await userEvent.type(screen.getByPlaceholderText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      await screen.findByTestId('login-loading-overlay'),
    ).toBeInTheDocument();
  });

  it('should have the correct copyright notice', () => {
    const mockYear = 1984;
    getFullYearMock.mockReturnValue(mockYear);
    render(<Login />, {
      wrapper: Wrapper,
    });

    expect(
      screen.getByText(
        `© Camunda Services GmbH ${mockYear}. All rights reserved.`,
      ),
    ).toBeInTheDocument();
  });

  // Currently there is an issue with jsdom which is making forms not respect required fields https://github.com/jsdom/jsdom/issues/2898
  it.skip('should not submit with empty fields', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 204}));

    render(<Login />, {
      wrapper: Wrapper,
    });

    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() => expect(fetchMock).not.toHaveBeenCalled());
    expect(historyMock.location.pathname).toBe('/');

    await userEvent.type(screen.getByPlaceholderText('Username'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() => expect(fetchMock).not.toHaveBeenCalled());
    expect(historyMock.location.pathname).toBe('/');

    await userEvent.type(screen.getByPlaceholderText('Username'), '');
    await userEvent.type(screen.getByPlaceholderText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() => expect(fetchMock).not.toHaveBeenCalled());
    expect(historyMock.location.pathname).toBe('/');
  });
});
