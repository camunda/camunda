/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import * as React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';
import {Dropdown} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {mockServer} from 'modules/mockServer';
import {graphql, rest} from 'msw';

const Wrapper: React.FC = ({children}) => (
  <ApolloProvider client={client}>
    <MockThemeProvider>{children}</MockThemeProvider>
  </ApolloProvider>
);

describe('<Dropdown />', () => {
  it('should render dropdown', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
    );

    render(<Dropdown />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.getByTestId('dropdown-icon')).toBeInTheDocument();
  });

  it('should show&hide dropdown menu behavior works correctly', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
    );

    render(
      <>
        <Dropdown />
        <div data-testid="some-other-element" />
      </>,
      {
        wrapper: Wrapper,
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

  it('should hide logout button', async () => {
    Object.defineProperty(window.clientConfig, 'canLogout', {
      value: false,
    });

    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
    );

    render(<Dropdown />, {
      wrapper: Wrapper,
    });

    fireEvent.click(await screen.findByText('Demo User'));
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByText('Logout')).not.toBeInTheDocument();

    Object.defineProperty(window.clientConfig, 'canLogout', {
      value: true,
    });
  });

  it('should hide the menu after the option is clicked', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      rest.post('/api/logout', (_, res, ctx) => {
        return res.once(ctx.status(200), ctx.body(''));
      }),
    );

    render(<Dropdown />, {
      wrapper: Wrapper,
    });

    fireEvent.click(await screen.findByText('Demo User'));
    fireEvent.click(screen.getByText('Logout'));

    expect(screen.queryByText('Logout')).not.toBeInTheDocument();
  });
});
