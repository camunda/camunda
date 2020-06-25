/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';
import {Router} from 'react-router-dom';
import {createMemoryHistory, History} from 'history';

import {Filters} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {OPTIONS} from './constants';

const getWrapper = (history: History): React.FC => ({children}) => {
  return (
    <Router history={history}>
      <MockThemeProvider>{children}</MockThemeProvider>
    </Router>
  );
};

const FILTERS = OPTIONS.map(({value}) => value);

describe('<Filters />', () => {
  it('should write the filters to the search params', () => {
    const history = createMemoryHistory();
    render(<Filters />, {
      wrapper: getWrapper(history),
    });

    FILTERS.forEach((filter) => {
      fireEvent.change(screen.getByRole('combobox'), {
        target: {
          value: filter,
        },
      });

      expect(new URLSearchParams(history.location.search).get('filter')).toBe(
        filter,
      );
    });
  });

  it('should redirect to the initial page', () => {
    const history = createMemoryHistory({initialEntries: ['/foobar']});
    render(<Filters />, {
      wrapper: getWrapper(history),
    });

    fireEvent.change(screen.getByRole('combobox'), {
      target: {
        value: FILTERS[0],
      },
    });

    expect(history.location.pathname).toBe('/');
  });

  it('should preserve existing search params on the URL', () => {
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

    fireEvent.change(screen.getByRole('combobox'), {
      target: {
        value: FILTERS[0],
      },
    });

    const searchParams = new URLSearchParams(history.location.search);

    expect(searchParams.get('filter')).toBe(FILTERS[0]);
    expect(searchParams.get(mockSearchParam.id)).toBe(mockSearchParam.value);
  });

  it('should load a value from the URL', () => {
    const [, mockFilter] = OPTIONS;
    const history = createMemoryHistory({
      initialEntries: [`/?filter=${mockFilter.value}`],
    });
    render(<Filters />, {
      wrapper: getWrapper(history),
    });

    expect(screen.getByDisplayValue(mockFilter.label)).toBeInTheDocument();
  });

  it('should have the correct options', () => {
    render(<Filters />, {
      wrapper: getWrapper(createMemoryHistory()),
    });

    OPTIONS.forEach(({label, value}) => {
      const option = screen.getByRole('option', {name: label});
      expect(option).toBeInTheDocument();
      expect(option).toHaveValue(value);
    });
  });

  it('should assume the correct default value', () => {
    render(<Filters />, {
      wrapper: getWrapper(createMemoryHistory()),
    });

    expect(screen.getByDisplayValue('All open')).toBeInTheDocument();
  });
});
