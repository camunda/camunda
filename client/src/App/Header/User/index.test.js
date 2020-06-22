/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, fireEvent, screen} from '@testing-library/react';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {fetchUser, logout} from 'modules/api/header';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';

import User from './index';

const mockUser = {
  firstname: 'Franz',
  lastname: 'Kafka',
};
const mockSsoUser = {
  firstname: '',
  lastname: 'Michael Jordan',
};

jest.mock('modules/api/header');

describe('User', () => {
  beforeEach(() => {
    jest.resetAllMocks();
  });

  afterEach(() => {
    clearStateLocally();
  });

  it('renders with locally stored User data', async () => {
    fetchUser.mockResolvedValue({});
    storeStateLocally({firstname: 'Sponge', lastname: 'Bob'});

    render(<User />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByText('Sponge Bob')).toBeInTheDocument();
  });

  it('renders with User data', async () => {
    fetchUser.mockResolvedValue(mockUser);

    render(<User />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();
  });

  it('renders without User data', async () => {
    fetchUser.mockRejectedValue({});

    render(<User />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByTestId('username-skeleton')).toBeInTheDocument();
  });

  it('renders with SSO User data (firstname field is empty)', async () => {
    fetchUser.mockResolvedValue(mockSsoUser);

    render(<User />, {
      wrapper: ThemeProvider,
    });

    expect(await screen.findByText('Michael Jordan')).toBeInTheDocument();
  });

  it('should handle logout', async () => {
    fetchUser.mockResolvedValue(mockUser);

    render(<User handleRedirect={() => {}} />, {
      wrapper: ThemeProvider,
    });

    fireEvent.click(await screen.findByText('Franz Kafka'));
    fireEvent.click(await screen.findByText('Logout'));

    expect(logout).toHaveBeenCalled();
  });
});
