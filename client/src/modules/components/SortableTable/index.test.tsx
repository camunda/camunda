/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MemoryRouter} from 'react-router-dom';
import {SortableTable} from './';
import {mockProps, mockSelectableProps} from './index.setup';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </ThemeProvider>
  );
};

describe('SortableTable', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('should display selectable table', () => {
    it('loading state', () => {
      render(
        <SortableTable
          {...mockProps}
          {...mockSelectableProps}
          state="loading"
        />,
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
        <SortableTable
          {...mockProps}
          {...mockSelectableProps}
          state="content"
        />,
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

  describe('should display non-selectable table', () => {
    it('loading state', () => {
      render(<SortableTable {...mockProps} state="loading" />, {
        wrapper: Wrapper,
      });
      expect(screen.getByText('Column Header 1')).toBeInTheDocument();
      expect(screen.getByText('cell content 1')).toBeInTheDocument();
      expect(screen.queryByTestId('table-skeleton')).not.toBeInTheDocument();
      expect(screen.getByTestId('instances-loader')).toBeInTheDocument();
      expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
      expect(
        screen.queryByText('Data could not be fetched')
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('checkbox', {name: 'Select all instances'})
      ).not.toBeInTheDocument();
    });

    it('empty state', () => {
      render(<SortableTable {...mockProps} state="empty" rows={[]} />, {
        wrapper: Wrapper,
      });
      expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();
      expect(screen.getByText('Column Header 1')).toBeInTheDocument();
      expect(screen.queryByText('cell content 1')).not.toBeInTheDocument();
      expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();
      expect(screen.queryByTestId('table-skeleton')).not.toBeInTheDocument();
      expect(screen.getByText('List is empty')).toBeInTheDocument();
      expect(
        screen.queryByText('Data could not be fetched')
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('checkbox', {name: 'Select all instances'})
      ).not.toBeInTheDocument();
    });

    it('error state', () => {
      render(<SortableTable {...mockProps} state="error" rows={[]} />, {
        wrapper: Wrapper,
      });
      expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();
      expect(screen.getByText('Column Header 1')).toBeInTheDocument();
      expect(screen.queryByText('cell content 1')).not.toBeInTheDocument();
      expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();
      expect(screen.queryByTestId('table-skeleton')).not.toBeInTheDocument();
      expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
      expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
      expect(
        screen.queryByRole('checkbox', {name: 'Select all instances'})
      ).not.toBeInTheDocument();
    });

    it('skeleton state', () => {
      render(<SortableTable {...mockProps} state="skeleton" rows={[]} />, {
        wrapper: Wrapper,
      });
      expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();
      expect(screen.getByText('Column Header 1')).toBeInTheDocument();
      expect(screen.queryByText('cell content 1')).not.toBeInTheDocument();
      expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();
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
      render(<SortableTable {...mockProps} state="content" />, {
        wrapper: Wrapper,
      });

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
        screen.queryByRole('checkbox', {name: 'Select all instances'})
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('checkbox', {name: /Select instance/})
      ).not.toBeInTheDocument();
    });
  });

  it('should select all rows', () => {
    render(
      <SortableTable {...mockProps} {...mockSelectableProps} state="content" />,
      {
        wrapper: Wrapper,
      }
    );

    expect(
      screen.getByRole('checkbox', {name: 'Select all instances'})
    ).not.toBeChecked();
    userEvent.click(
      screen.getByRole('checkbox', {name: 'Select all instances'})
    );

    expect(
      screen.getByRole('checkbox', {name: 'Select all instances'})
    ).toBeChecked();
    expect(mockSelectableProps.onSelectAll).toHaveBeenCalledTimes(1);
  });

  it('should select one row', () => {
    render(
      <SortableTable {...mockProps} {...mockSelectableProps} state="content" />,
      {
        wrapper: Wrapper,
      }
    );

    const [firstRow, secondRow] = mockProps.rows;

    const [firstCheckbox, secondCheckbox] = screen.getAllByRole('checkbox', {
      name: /Select instance/,
    });

    userEvent.click(firstCheckbox!);
    expect(firstRow?.onSelect).toHaveBeenCalledTimes(1);

    userEvent.click(secondCheckbox!);
    expect(secondRow?.onSelect).toHaveBeenCalledTimes(1);

    expect(firstRow?.checkIsSelected).toHaveBeenCalledTimes(1);
    expect(secondRow?.checkIsSelected).toHaveBeenCalledTimes(1);
  });
});
