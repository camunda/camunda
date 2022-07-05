/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MemoryRouter} from 'react-router-dom';
import {ColumnHeader} from './index';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </ThemeProvider>
  );
};

describe('ColumnHeader', () => {
  const mockPropsWithSorting = {
    active: false,
    label: 'Start Date',
    onSort: jest.fn(),
    sortKey: 'startDate',
  };

  it('should render a button if the column is sortable', () => {
    render(<ColumnHeader {...mockPropsWithSorting} />, {
      wrapper: Wrapper,
    });
    expect(screen.getByText(mockPropsWithSorting.label)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Sort by startDate'})
    ).toBeInTheDocument();
  });

  it('should only render the text if the column is not sortable', () => {
    render(<ColumnHeader label="Start Date" />, {wrapper: Wrapper});
    expect(screen.getByText('Start Date')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Sort by startDate'})
    ).not.toBeInTheDocument();
  });
});
