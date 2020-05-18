/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen, fireEvent, waitFor} from '@testing-library/react';
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
    const MOCK_HISTORY = createMemoryHistory();
    render(
      <Router history={MOCK_HISTORY}>
        <Login />
      </Router>,
    );

    fireEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(MOCK_HISTORY.location.pathname).toBe('/');
  });

  it('should show an error on failure', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 404}));
    render(
      <Router history={createMemoryHistory()}>
        <Login />
      </Router>,
    );

    fireEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(screen.getByText('error')).toBeInTheDocument();
  });
});
