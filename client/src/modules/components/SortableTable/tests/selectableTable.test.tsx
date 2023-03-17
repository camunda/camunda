/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {SortableTable} from '../';
import {mockProps, mockSelectableProps, Wrapper} from './mocks';

describe('selectable table', () => {
  it('loading state', () => {
    render(
      <SortableTable {...mockProps} {...mockSelectableProps} state="loading" />,
      {
        wrapper: Wrapper,
      }
    );
    expect(screen.getByTestId('instances-loader')).toBeInTheDocument();
    expect(screen.getByText('Column Header 1')).toBeInTheDocument();
    expect(screen.getByText('cell content 1')).toBeInTheDocument();

    expect(screen.queryByTestId('table-skeleton')).not.toBeInTheDocument();
    expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Data could not be fetched')
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('checkbox', {name: 'Select all instances'})
    ).toBeDisabled();
  });

  it('empty state', () => {
    render(
      <SortableTable
        {...mockProps}
        {...mockSelectableProps}
        state="empty"
        rows={[]}
      />,
      {
        wrapper: Wrapper,
      }
    );
    expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();
    expect(screen.getByText('Column Header 1')).toBeInTheDocument();
    expect(screen.queryByText('cell content 1')).not.toBeInTheDocument();

    expect(screen.queryByTestId('table-skeleton')).not.toBeInTheDocument();
    expect(screen.getByText('List is empty')).toBeInTheDocument();
    expect(
      screen.queryByText('Data could not be fetched')
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('checkbox', {name: 'Select all instances'})
    ).toBeDisabled();
  });

  it('error state', () => {
    render(
      <SortableTable
        {...mockProps}
        {...mockSelectableProps}
        state="error"
        rows={[]}
      />,
      {
        wrapper: Wrapper,
      }
    );
    expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();
    expect(screen.getByText('Column Header 1')).toBeInTheDocument();
    expect(screen.queryByText('cell content 1')).not.toBeInTheDocument();

    expect(screen.queryByTestId('table-skeleton')).not.toBeInTheDocument();
    expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
    expect(
      screen.getByRole('checkbox', {name: 'Select all instances'})
    ).toBeDisabled();
  });

  it('skeleton state', () => {
    render(
      <SortableTable
        {...mockProps}
        {...mockSelectableProps}
        state="skeleton"
        rows={[]}
      />,
      {
        wrapper: Wrapper,
      }
    );
    expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();
    expect(screen.getByText('Column Header 1')).toBeInTheDocument();
    expect(screen.queryByText('cell content 1')).not.toBeInTheDocument();
    expect(screen.getByTestId('table-skeleton')).toBeInTheDocument();
    expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Data could not be fetched')
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', {name: 'Select all instances'})
    ).not.toBeInTheDocument();
  });

  it('content state', () => {
    render(
      <SortableTable {...mockProps} {...mockSelectableProps} state="content" />,
      {
        wrapper: Wrapper,
      }
    );

    expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();
    expect(screen.getByText('Column Header 1')).toBeInTheDocument();
    expect(screen.getByText('cell content 1')).toBeInTheDocument();
    expect(screen.queryByTestId('table-skeleton')).not.toBeInTheDocument();
    expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();
    expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Data could not be fetched')
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('checkbox', {name: 'Select all instances'})
    ).toBeEnabled();
    expect(
      screen.getAllByRole('checkbox', {name: /Select instance/})
    ).toHaveLength(4);
  });
});
