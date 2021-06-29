/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {Router} from 'react-router-dom';
import {createMemoryHistory} from 'history';

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

const getWrapper =
  (history = createMemoryHistory()): React.FC =>
  ({children}) => {
    return (
      <ApolloProvider client={client}>
        <Router history={history}>
          <MockThemeProvider>{children}</MockThemeProvider>
        </Router>
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

    const history = createMemoryHistory();
    render(<Filters />, {
      wrapper: getWrapper(history),
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Filter'})).toBeDisabled(),
    );

    FILTERS.forEach((filter) => {
      fireEvent.change(screen.getByRole('combobox', {name: /filter/i}), {
        target: {
          value: filter,
        },
      });

      expect(new URLSearchParams(history.location.search).get('filter')).toBe(
        filter,
      );
    });
  });

  it('should redirect to the initial page', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks().result.data));
      }),
    );

    const history = createMemoryHistory({initialEntries: ['/foobar']});

    render(<Filters />, {
      wrapper: getWrapper(history),
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Filter'})).toBeDisabled(),
    );

    fireEvent.change(screen.getByRole('combobox', {name: /filter/i}), {
      target: {
        value: FILTERS[0],
      },
    });

    expect(history.location.pathname).toBe('/');
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
    const history = createMemoryHistory({
      initialEntries: [`/?${mockSearchParam.id}=${mockSearchParam.value}`],
    });

    render(<Filters />, {
      wrapper: getWrapper(history),
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Filter'})).toBeDisabled(),
    );

    fireEvent.change(screen.getByRole('combobox', {name: /filter/i}), {
      target: {
        value: FILTERS[0],
      },
    });

    const searchParams = new URLSearchParams(history.location.search);

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
    const history = createMemoryHistory({
      initialEntries: [`/?filter=${mockFilter.value}`],
    });

    render(<Filters />, {
      wrapper: getWrapper(history),
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Filter'})).toBeDisabled(),
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
      wrapper: getWrapper(createMemoryHistory()),
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Filter'})).toBeDisabled(),
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
      wrapper: getWrapper(createMemoryHistory()),
    });

    await waitFor(() =>
      expect(screen.getByDisplayValue('All open')).toBeDisabled(),
    );

    await waitFor(() =>
      expect(screen.getByDisplayValue('All open')).toBeEnabled(),
    );
  });
});
