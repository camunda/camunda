/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import {GettingStartedExperience} from './index';

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
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
    render(<GettingStartedExperience />, {
      wrapper: createWrapper('/?gseUrl=https://www.testUrl.com'),
    });

    expect(mockDisplayNotification).toHaveBeenCalledWith('info', {
      headline: 'To continue getting started, head back to Console',
      isDismissable: false,
      navigation: expect.objectContaining({
        label: 'Open Console',
      }),
      showCreationTime: false,
    });
  });

  it('should not display gse notification', async () => {
    render(<GettingStartedExperience />, {
      wrapper: createWrapper(),
    });

    expect(mockDisplayNotification).not.toHaveBeenCalledWith('info', {
      headline: 'To continue getting started, head back to Console',
      isDismissable: false,
      navigation: expect.objectContaining({
        label: 'Open Console',
      }),
    });
  });
});
