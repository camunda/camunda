/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {Router, Route, Switch} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import Authentication from './index';
import {createMemoryHistory, History} from 'history';

const LOGIN_CONTENT = 'Login content';
const PRIVATE_COMPONENT_CONTENT = 'Private component content';
const PrivateComponent: React.FC = () => <div>{PRIVATE_COMPONENT_CONTENT}</div>;

jest.mock('modules/notifications', () => {
  return {
    useNotifications: () => {
      return {
        displayNotification: () => {
          return new Promise((resolve) => {
            resolve({});
          });
        },
      };
    },
  };
});

function createWrapper(history: History) {
  const Wrapper: React.FC = ({children}) => (
    <ThemeProvider>
      <Router history={history}>
        <Switch>
          <Route path="/login">{<h1>{LOGIN_CONTENT}</h1>}</Route>
          <Authentication>
            <Route path="/instances/:instanceId">{children}</Route>
          </Authentication>
        </Switch>
      </Router>
    </ThemeProvider>
  );

  return Wrapper;
}

describe('Authentication', () => {
  it('should render component if user is logged in', async () => {
    const mockPathname = '/instances/1';
    const historyMock = createMemoryHistory({
      initialEntries: [mockPathname],
    });

    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );

    render(<PrivateComponent />, {
      wrapper: createWrapper(historyMock),
    });

    expect(
      await screen.findByText(PRIVATE_COMPONENT_CONTENT)
    ).toBeInTheDocument();
    expect(historyMock.location.pathname).toBe(mockPathname);
  });

  it('should redirect to login page if user is not authenticated (401)', async () => {
    const historyMock = createMemoryHistory({
      initialEntries: ['/instances/1'],
    });

    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.status(401), ctx.json({}))
      )
    );
    render(<PrivateComponent />, {
      wrapper: createWrapper(historyMock),
    });

    expect(await screen.findByText(LOGIN_CONTENT)).toBeInTheDocument();
    expect(historyMock.location.pathname).toBe('/login');
  });

  it('should not erase persistent params on session redirect (401)', async () => {
    const historyMock = createMemoryHistory({
      initialEntries: ['/instances/1?gseUrl=https%3A%2F%2Fwww.testUrl.com'],
    });

    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.status(401), ctx.json({}))
      )
    );

    render(<PrivateComponent />, {
      wrapper: createWrapper(historyMock),
    });

    expect(await screen.findByText(LOGIN_CONTENT)).toBeInTheDocument();
    expect(historyMock.location.pathname).toBe('/login');
    expect(historyMock.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );
  });

  it('should redirect to login page if user is authenticated (403)', async () => {
    const historyMock = createMemoryHistory({
      initialEntries: ['/instances/1'],
    });

    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.status(403), ctx.json({}))
      )
    );

    render(<PrivateComponent />, {
      wrapper: createWrapper(historyMock),
    });

    expect(await screen.findByText(LOGIN_CONTENT)).toBeInTheDocument();
    expect(historyMock.location.pathname).toBe('/login');
  });

  it('should not erase persistent params on session redirect (403)', async () => {
    const historyMock = createMemoryHistory({
      initialEntries: ['/instances/1?gseUrl=https%3A%2F%2Fwww.testUrl.com'],
    });

    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.status(403), ctx.json({}))
      )
    );

    render(<PrivateComponent />, {
      wrapper: createWrapper(historyMock),
    });

    expect(await screen.findByText(LOGIN_CONTENT)).toBeInTheDocument();
    expect(historyMock.location.pathname).toBe('/login');
    expect(historyMock.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );
  });
});
