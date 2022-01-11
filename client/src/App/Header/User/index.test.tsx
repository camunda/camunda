/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {User} from './index';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {authenticationStore} from 'modules/stores/authentication';
import Authentication from 'App/Authentication';
import {MemoryRouter} from 'react-router-dom';

const mockUser = {
  displayName: 'Franz Kafka',
  canLogout: true,
};

const mockSsoUser = {
  displayName: 'Michael Jordan',
  canLogout: false,
};

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <MemoryRouter>
        <Authentication>{children} </Authentication>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('User', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should render user display name', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json(mockUser))
      )
    );

    render(<User handleRedirect={() => {}} />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();
  });

  it('should handle a SSO user', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json(mockSsoUser))
      )
    );

    render(<User handleRedirect={() => {}} />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Michael Jordan')).toBeInTheDocument();

    userEvent.click(await screen.findByText('Michael Jordan'));

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
      wrapper: Wrapper,
    });

    userEvent.click(await screen.findByText('Franz Kafka'));
    userEvent.click(await screen.findByText('Logout'));

    await waitFor(() => expect(mockHandleRedirect).toHaveBeenCalled());
  });
});
