/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  fireEvent,
  screen,
  waitForElementToBeRemoved,
  waitFor,
} from '@testing-library/react';

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';

import {User} from './index';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

const mockUser = {
  firstname: 'Franz',
  lastname: 'Kafka',
  username: 'franzkafka',
  canLogout: true,
};
const mockUserWithOnlyUsername = {
  firstname: null,
  lastname: null,
  username: 'franzkafka',
  canLogout: true,
};
const mockSsoUser = {
  firstname: '',
  lastname: 'Michael Jordan',
  username: 'michaeljordan',
  canLogout: false,
};
const previouslyLoggedInUser = {
  firstname: 'Sponge',
  lastname: 'Bob',
  username: 'bob',
  canLogout: true,
};

describe('User', () => {
  afterEach(() => {
    clearStateLocally();
  });

  it('should handle a previously logged in user', async () => {
    storeStateLocally(previouslyLoggedInUser);

    render(<User handleRedirect={() => {}} />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByText('Sponge Bob')).toBeInTheDocument();
  });

  it('should render a the user name and last name', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json(mockUser))
      )
    );

    render(<User handleRedirect={() => {}} />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();
  });

  it('should handle render the username', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json(mockUserWithOnlyUsername))
      )
    );

    render(<User handleRedirect={() => {}} />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByText('franzkafka')).toBeInTheDocument();
  });

  it('should render a skeleton', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json(mockUser))
      )
    );

    render(<User handleRedirect={() => {}} />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByTestId('username-skeleton')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('username-skeleton'));
  });

  it('should handle a SSO user', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json(mockSsoUser))
      )
    );

    render(<User handleRedirect={() => {}} />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByText('Michael Jordan')).toBeInTheDocument();

    fireEvent.click(await screen.findByText('Michael Jordan'));

    expect(screen.queryByText('Logout')).not.toBeInTheDocument();
  });

  it('should handle logout', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json(mockUser))
      ),
      rest.post('/api/logout', (_, res, ctx) => res.once(ctx.json('')))
    );

    const mockHandleRedirect = jest.fn();

    render(<User handleRedirect={mockHandleRedirect} />, {
      wrapper: ThemeProvider,
    });

    fireEvent.click(await screen.findByText('Franz Kafka'));
    fireEvent.click(await screen.findByText('Logout'));

    await waitFor(() => expect(mockHandleRedirect).toHaveBeenCalled());
  });
});
