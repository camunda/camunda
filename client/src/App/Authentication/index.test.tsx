/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {MemoryRouter, Route} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import Authentication from './index';

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

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/instances/1`]}>
        <Route path="/login" render={() => <h1>{LOGIN_CONTENT}</h1>} />
        <Authentication>
          <Route>{children} </Route>
        </Authentication>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('Authentication', () => {
  it('should render component if user is logged in', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );

    render(<PrivateComponent />, {wrapper: Wrapper});
    expect(
      await screen.findByText(PRIVATE_COMPONENT_CONTENT)
    ).toBeInTheDocument();
  });

  it('should redirect to login page if user is not authenticated (401)', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.status(401), ctx.json({}))
      )
    );

    render(<PrivateComponent />, {wrapper: Wrapper});

    expect(await screen.findByText(LOGIN_CONTENT)).toBeInTheDocument();
  });

  it('should redirect to login page if user is authenticated (403)', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.status(403), ctx.json({}))
      )
    );

    render(<PrivateComponent />, {wrapper: Wrapper});

    expect(await screen.findByText(LOGIN_CONTENT)).toBeInTheDocument();
  });
});
