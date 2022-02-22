/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {User} from './index';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {authenticationStore} from 'modules/stores/authentication';

const mockUser = {
  displayName: 'Franz Kafka',
  canLogout: true,
};

const mockSsoUser = {
  displayName: 'Michael Jordan',
  canLogout: false,
};

const Wrapper: React.FC = ({children}) => {
  return <ThemeProvider>{children}</ThemeProvider>;
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

    render(<User />, {
      wrapper: Wrapper,
    });

    authenticationStore.authenticate();

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();
  });

  it('should handle a SSO user', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.json(mockSsoUser))
      )
    );

    render(<User />, {
      wrapper: Wrapper,
    });

    authenticationStore.authenticate();

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

    render(<User />, {
      wrapper: Wrapper,
    });

    authenticationStore.authenticate();

    userEvent.click(await screen.findByText('Franz Kafka'));
    userEvent.click(await screen.findByText('Logout'));

    expect(await screen.findByTestId('username-skeleton')).toBeInTheDocument();
  });
});
