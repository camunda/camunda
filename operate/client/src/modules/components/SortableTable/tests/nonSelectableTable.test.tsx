/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {SortableTable} from '../';
import {mockProps, Wrapper} from './mocks';

describe('non selectable table', () => {
  it('loading state', () => {
    render(<SortableTable {...mockProps} state="loading" />, {
      wrapper: Wrapper,
    });
    expect(screen.getByText('Column Header 1')).toBeInTheDocument();
    expect(screen.getByText('cell content 1')).toBeInTheDocument();
    expect(screen.queryByTestId('data-table-skeleton')).not.toBeInTheDocument();
    expect(screen.getByTestId('data-table-loader')).toBeInTheDocument();
    expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Data could not be fetched'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', {name: 'Select all rows'}),
    ).not.toBeInTheDocument();
  });

  it('empty state', () => {
    render(<SortableTable {...mockProps} state="empty" rows={[]} />, {
      wrapper: Wrapper,
    });
    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.queryByText('Column Header 1')).not.toBeInTheDocument();
    expect(screen.queryByText('cell content 1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.queryByTestId('data-table-skeleton')).not.toBeInTheDocument();
    expect(screen.getByText('List is empty')).toBeInTheDocument();
    expect(
      screen.queryByText('Data could not be fetched'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', {name: 'Select all rows'}),
    ).not.toBeInTheDocument();
  });

  it('error state', () => {
    render(<SortableTable {...mockProps} state="error" rows={[]} />, {
      wrapper: Wrapper,
    });
    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.queryByText('Column Header 1')).not.toBeInTheDocument();
    expect(screen.queryByText('cell content 1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.queryByTestId('data-table-skeleton')).not.toBeInTheDocument();
    expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', {name: 'Select all rows'}),
    ).not.toBeInTheDocument();
  });

  it('skeleton state', () => {
    render(<SortableTable {...mockProps} state="skeleton" rows={[]} />, {
      wrapper: Wrapper,
    });
    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.getByText('Column Header 1')).toBeInTheDocument();
    expect(screen.queryByText('cell content 1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();
    expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Data could not be fetched'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', {name: 'Select all rows'}),
    ).not.toBeInTheDocument();
  });

  it('content state', () => {
    render(<SortableTable {...mockProps} state="content" />, {
      wrapper: Wrapper,
    });

    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.getByText('Column Header 1')).toBeInTheDocument();
    expect(screen.getByText('cell content 1')).toBeInTheDocument();
    expect(screen.queryByTestId('data-table-skeleton')).not.toBeInTheDocument();
    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Data could not be fetched'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', {name: 'Select all rows'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', {name: /Select row/}),
    ).not.toBeInTheDocument();
  });
});
