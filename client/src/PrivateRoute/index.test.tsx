/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen} from '@testing-library/react';
import {MemoryRouter, Switch, Route} from 'react-router-dom';

import {PrivateRoute} from './index';
import {login} from '../login.store';

const LOGIN_CONTENT = 'Login content';
const fetchMock = jest.spyOn(window, 'fetch');
const LOGIN_PATH = '/login';

const Wrapper: React.FC = ({children}) => {
  return (
    <MemoryRouter>
      <Switch>{children}</Switch>
      <Route path={LOGIN_PATH} render={() => <h1>{LOGIN_CONTENT}</h1>} />
    </MemoryRouter>
  );
};

describe('<PrivateRoute />', () => {
  afterEach(() => {
    fetchMock.mockClear();
    login.reset();
  });

  afterAll(() => {
    fetchMock.mockRestore();
  });

  it('should show the provided content', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 204}));

    const CONTENT = 'Secret route';
    render(
      <PrivateRoute
        path="/"
        redirectPath={LOGIN_PATH}
        render={() => {
          return <h1>{CONTENT}</h1>;
        }}
      />,
      {wrapper: Wrapper},
    );

    await login.handleLogin('demo', 'demo');

    expect(screen.getByText(CONTENT)).toBeInTheDocument();
  });

  it('should redirect when not authenticated', () => {
    render(
      <PrivateRoute
        path="/"
        redirectPath={LOGIN_PATH}
        render={() => {
          return <h1>Secret route</h1>;
        }}
      />,
      {wrapper: Wrapper},
    );

    expect(screen.getByText(LOGIN_CONTENT)).toBeInTheDocument();
  });
});
