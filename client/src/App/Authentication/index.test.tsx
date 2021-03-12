/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {Router, Route} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import Authentication from './index';
import {createMemoryHistory, History} from 'history';

const LOGIN_CONTENT = 'Login content';
const PRIVATE_COMPONENT_CONTENT = 'Private component content';
const PrivateComponent = () => <div>{PRIVATE_COMPONENT_CONTENT}</div>;

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

type Props = {
  children?: React.ReactNode;
};

const createWrapper = (history: History) => ({children}: Props) => (
  <ThemeProvider>
    <Router history={history}>
      <Route path="/login" render={() => <h1>{LOGIN_CONTENT}</h1>} />
      <Authentication>
        <Route>{children} </Route>
      </Authentication>
    </Router>
  </ThemeProvider>
);

describe('Authentication', () => {
  it('should render component if user is logged in', async () => {
    const historyMock = createMemoryHistory({initialEntries: ['/instances/1']});

    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );

    render(<PrivateComponent />, {wrapper: createWrapper(historyMock)});
    expect(
      await screen.findByText(PRIVATE_COMPONENT_CONTENT)
    ).toBeInTheDocument();

    expect(historyMock.location.search).toBe('');
  });

  it('should redirect to login page if user is not authenticated (401)', async () => {
    const historyMock = createMemoryHistory({initialEntries: ['/instances/1']});

    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.status(401), ctx.json({}))
      )
    );

    render(<PrivateComponent />, {wrapper: createWrapper(historyMock)});

    expect(await screen.findByText(LOGIN_CONTENT)).toBeInTheDocument();
    expect(historyMock.location.search).toBe('');
  });

  it('should redirect to login page with gse url if user is not authenticated (401)', async () => {
    const historyMock = createMemoryHistory({
      initialEntries: ['/instances/1?gseUrl=https://www.testUrl.com'],
    });

    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.status(401), ctx.json({}))
      )
    );

    render(<PrivateComponent />, {wrapper: createWrapper(historyMock)});

    expect(await screen.findByText(LOGIN_CONTENT)).toBeInTheDocument();
    expect(historyMock.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );
  });

  it('should redirect to login page if user is authenticated (403)', async () => {
    const historyMock = createMemoryHistory({initialEntries: ['/instances/1']});

    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.status(403), ctx.json({}))
      )
    );

    render(<PrivateComponent />, {wrapper: createWrapper(historyMock)});

    expect(await screen.findByText(LOGIN_CONTENT)).toBeInTheDocument();
    expect(historyMock.location.search).toBe('');
  });

  it('should redirect to login page  with gse url if user is authenticated (403)', async () => {
    const historyMock = createMemoryHistory({
      initialEntries: ['/instances/1?gseUrl=https://www.testUrl.com'],
    });

    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.status(403), ctx.json({}))
      )
    );

    render(<PrivateComponent />, {wrapper: createWrapper(historyMock)});

    expect(await screen.findByText(LOGIN_CONTENT)).toBeInTheDocument();
    expect(historyMock.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );
  });
});
