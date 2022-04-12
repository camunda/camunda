/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {Filters} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {OPTIONS} from './constants';
import {
  mockGetAllOpenTasks,
  mockGetEmptyTasks,
  mockGetClaimedByMe,
  mockGetUnclaimed,
  mockGetCompleted,
} from 'modules/queries/get-tasks';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {mockServer} from 'modules/mockServer';
import {graphql} from 'msw';
import {LocationLog} from 'modules/utils/LocationLog';

const getWrapper =
  (
    initialEntries: React.ComponentProps<
      typeof MemoryRouter
    >['initialEntries'] = ['/'],
  ): React.FC<{children?: React.ReactNode}> =>
  ({children}) => {
    return (
      <ApolloProvider client={client}>
        <MockThemeProvider>
          <MemoryRouter initialEntries={initialEntries}>
            {children}
            <LocationLog />
          </MemoryRouter>
        </MockThemeProvider>
      </ApolloProvider>
    );
  };

const FILTERS = OPTIONS.map(({value}) => value);

describe('<Filters />', () => {
  it('should write the filters to the search params', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks().result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetEmptyTasks.result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetClaimedByMe.result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetUnclaimed.result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCompleted.result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
    );

    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByRole('combobox', {name: /filter/i})).toBeDisabled();

    fireEvent.change(screen.getByRole('combobox', {name: /filter/i}), {
      target: {
        value: 'all-open',
      },
    });

    expect(screen.getByRole('combobox', {name: /filter/i})).toBeDisabled();
    expect(screen.getByTestId('search')).toHaveTextContent('filter=all-open');
    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: /filter/i})).toBeEnabled(),
    );

    fireEvent.change(screen.getByRole('combobox', {name: /filter/i}), {
      target: {
        value: 'claimed-by-me',
      },
    });

    expect(screen.getByRole('combobox', {name: /filter/i})).toBeDisabled();
    expect(screen.getByTestId('search')).toHaveTextContent(
      'filter=claimed-by-me',
    );
    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: /filter/i})).toBeEnabled(),
    );

    fireEvent.change(screen.getByRole('combobox', {name: /filter/i}), {
      target: {
        value: 'unclaimed',
      },
    });

    expect(screen.getByRole('combobox', {name: /filter/i})).toBeDisabled();
    expect(screen.getByTestId('search')).toHaveTextContent('filter=unclaimed');
    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: /filter/i})).toBeEnabled(),
    );

    fireEvent.change(screen.getByRole('combobox', {name: /filter/i}), {
      target: {
        value: 'completed',
      },
    });

    expect(screen.getByRole('combobox', {name: /filter/i})).toBeDisabled();
    expect(screen.getByTestId('search')).toHaveTextContent('filter=completed');
    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: /filter/i})).toBeEnabled(),
    );
  });

  it('should redirect to the initial page', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks().result.data));
      }),
    );

    render(<Filters />, {
      wrapper: getWrapper(['/foobar']),
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: /filter/i})).toBeDisabled(),
    );

    fireEvent.change(screen.getByRole('combobox', {name: /filter/i}), {
      target: {
        value: FILTERS[0],
      },
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent('/');
  });

  it('should preserve existing search params on the URL', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks().result.data));
      }),
    );
    const mockSearchParam = {
      id: 'foo',
      value: 'bar',
    } as const;

    render(<Filters />, {
      wrapper: getWrapper([`/?${mockSearchParam.id}=${mockSearchParam.value}`]),
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: /filter/i})).toBeDisabled(),
    );

    fireEvent.change(screen.getByRole('combobox', {name: /filter/i}), {
      target: {
        value: FILTERS[0],
      },
    });

    const searchParams = new URLSearchParams(
      screen.getByTestId('search')?.textContent ?? '',
    );

    expect(searchParams.get('filter')).toBe(FILTERS[0]);
    expect(searchParams.get(mockSearchParam.id)).toBe(mockSearchParam.value);
  });

  it('should load a value from the URL', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetClaimedByMe.result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
    );

    const [, mockFilter] = OPTIONS;

    render(<Filters />, {
      wrapper: getWrapper([`/?filter=${mockFilter.value}`]),
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: /filter/i})).toBeDisabled(),
    );

    expect(screen.getByDisplayValue(mockFilter.label)).toBeInTheDocument();
  });

  it('should have the correct options', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks().result.data));
      }),
    );

    render(<Filters />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: /filter/i})).toBeDisabled(),
    );

    OPTIONS.forEach(({label, value}) => {
      const option = screen.getByRole('option', {name: label});
      expect(option).toBeInTheDocument();
      expect(option).toHaveValue(value);
    });
  });

  it('should should disable the filter while loading', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks().result.data));
      }),
    );

    render(<Filters />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(screen.getByDisplayValue('All open')).toBeDisabled(),
    );

    await waitFor(() =>
      expect(screen.getByDisplayValue('All open')).toBeEnabled(),
    );
  });
});
