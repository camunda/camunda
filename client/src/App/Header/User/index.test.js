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

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';

import User from './index';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

const mockUser = {
  firstname: 'Franz',
  lastname: 'Kafka',
};
const mockSsoUser = {
  firstname: '',
  lastname: 'Michael Jordan',
};

describe('User', () => {
  afterEach(() => {
    clearStateLocally();
  });

  it('renders with locally stored User data', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );
    storeStateLocally({firstname: 'Sponge', lastname: 'Bob'});

    render(<User />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByText('Sponge Bob')).toBeInTheDocument();
  });

  it('renders with User data', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json(mockUser))
      )
    );

    render(<User />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();
  });

  it('renders without User data', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json(mockUser))
      )
    );

    render(<User />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByTestId('username-skeleton')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('username-skeleton'));
  });

  it('renders with SSO User data (firstname field is empty)', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json(mockSsoUser))
      )
    );

    render(<User />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByText('Michael Jordan')).toBeInTheDocument();
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
