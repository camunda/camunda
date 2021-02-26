/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  fireEvent,
  waitForElementToBeRemoved,
  waitFor,
} from '@testing-library/react';
import {Router} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import {GettingStartedExperience} from './index';

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

function createWrapper(history = createMemoryHistory()) {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <Router history={history}>{children}</Router>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('<GettingStartedExperience />', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should display gse notification', async () => {
    const mockHistory = createMemoryHistory({
      initialEntries: ['/?gseUrl=https://www.testUrl.com'],
    });
    render(<GettingStartedExperience />, {
      wrapper: createWrapper(mockHistory),
    });

    expect(mockDisplayNotification).toHaveBeenCalledWith('info', {
      headline: 'To continue to getting started, go back to',
      isDismissable: false,
      navigation: expect.objectContaining({
        label: 'Cloud',
      }),
    });
  });

  it('should not display gse notification', async () => {
    const mockHistory = createMemoryHistory({
      initialEntries: ['/'],
    });
    render(<GettingStartedExperience />, {
      wrapper: createWrapper(mockHistory),
    });

    expect(mockDisplayNotification).not.toHaveBeenCalledWith('info', {
      headline: 'To continue to getting started, go back to',
      isDismissable: false,
      navigation: expect.objectContaining({
        label: 'Cloud',
      }),
    });
  });
});
