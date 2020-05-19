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
import {login} from '../login.store';

const fetchMock = jest.spyOn(window, 'fetch');

describe('<Login />', () => {
  afterEach(() => {
    fetchMock.mockClear();
    login.reset();
  });

  afterAll(() => {
    fetchMock.mockRestore();
  });

  it('should redirect to the initial on success', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 204}));
    const historyMock = createMemoryHistory();
    render(
      <Router history={historyMock}>
        <Login />
      </Router>,
    );

    await userEvent.type(screen.getByLabelText('Username'), 'demo');
    await userEvent.type(screen.getByLabelText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(historyMock.location.pathname).toBe('/');
  });

  it('should show an error on failure', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 404}));
    render(
      <Router history={createMemoryHistory()}>
        <Login />
      </Router>,
    );

    await userEvent.type(screen.getByLabelText('Username'), 'demo');
    await userEvent.type(screen.getByLabelText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(screen.getByText('error')).toBeInTheDocument();
  });

  it('should not submit with empty fields', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 204}));
    const historyMock = createMemoryHistory();
    render(
      <Router history={historyMock}>
        <Login />
      </Router>,
    );

    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() => expect(fetchMock).not.toHaveBeenCalled());
    expect(historyMock.location.pathname).toBe('/');

    // Currently there is an issue with jsdom which is making forms not respect required fields https://github.com/jsdom/jsdom/issues/2898
    // await userEvent.type(screen.getByLabelText('Username'), 'demo');
    // userEvent.click(screen.getByRole('button', {name: 'Login'}));

    // await waitFor(() => expect(fetchMock).not.toHaveBeenCalled());
    // expect(historyMock.location.pathname).toBe('/');

    // await userEvent.type(screen.getByLabelText('Username'), '');
    // await userEvent.type(screen.getByLabelText('Password'), 'demo');
    // userEvent.click(screen.getByRole('button', {name: 'Login'}));

    // await waitFor(() => expect(fetchMock).not.toHaveBeenCalled());
    // expect(historyMock.location.pathname).toBe('/');
  });
});
