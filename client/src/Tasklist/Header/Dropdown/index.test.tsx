/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';
import {MockedResponse} from '@apollo/client/testing';

import {Dropdown} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';

const fetchMock = jest.spyOn(window, 'fetch');

const getWrapper = (mocks: MockedResponse[]) => {
  const Wrapper: React.FC = ({children}) => {
    return (
      <MockedApolloProvider mocks={mocks}>
        <MockThemeProvider>{children}</MockThemeProvider>
      </MockedApolloProvider>
    );
  };

  return Wrapper;
};

describe('<Dropdown />', () => {
  afterAll(() => {
    fetchMock.mockRestore();
  });

  it('should render dropdown', async () => {
    render(<Dropdown />, {
      wrapper: getWrapper([mockGetCurrentUser]),
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.getByTestId('dropdown-icon')).toBeInTheDocument();
  });

  it('should show&hide dropdown menu behavior works correctly', async () => {
    render(
      <>
        <Dropdown />
        <div data-testid="some-other-element" />
      </>,
      {
        wrapper: getWrapper([mockGetCurrentUser]),
      },
    );

    // menu should not be displayed on first load
    expect(screen.queryByText('Logout')).not.toBeInTheDocument();

    // menu should be displayed on dropdown click
    fireEvent.click(await screen.findByText('Demo User'));
    expect(screen.getByText('Logout')).toBeInTheDocument();

    // menu should not be displayed after clicking dropdown again
    fireEvent.click(screen.getByText('Demo User'));
    expect(screen.queryByText('Logout')).not.toBeInTheDocument();

    // menu should be displayed on dropdown click
    fireEvent.click(screen.getByText('Demo User'));
    expect(screen.getByText('Logout')).toBeInTheDocument();

    // menu should not be displayed if another element is clicked
    fireEvent.click(screen.getByTestId('some-other-element'));
    expect(screen.queryByText('Logout')).not.toBeInTheDocument();

    // menu should be displayed on dropdown click
    fireEvent.click(screen.getByText('Demo User'));
    expect(screen.getByText('Logout')).toBeInTheDocument();

    // menu should not be displayed if esc key is pressed
    fireEvent.keyDown(screen.getByText('Demo User'), {
      key: 'Escape',
      code: 27,
    });
    expect(screen.queryByText('Logout')).not.toBeInTheDocument();

    // menu should be displayed on dropdown click
    fireEvent.click(screen.getByText('Demo User'));
    expect(screen.getByText('Logout')).toBeInTheDocument();
  });

  it('should hide the menu after the option is clicked', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 200}));
    render(<Dropdown />, {
      wrapper: getWrapper([mockGetCurrentUser]),
    });

    fireEvent.click(await screen.findByText('Demo User'));
    fireEvent.click(screen.getByText('Logout'));

    expect(screen.queryByText('Logout')).not.toBeInTheDocument();
  });
});
