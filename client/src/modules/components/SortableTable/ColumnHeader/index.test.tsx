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
import {LocationLog} from 'modules/utils/LocationLog';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter>
        {children} <LocationLog />
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('ColumnHeader', () => {
  it('should render a button if the column is sortable', () => {
    render(<ColumnHeader label="Start Date" sortKey="startDate" />, {
      wrapper: Wrapper,
    });
    expect(screen.getByText('Start Date')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Sort by Start Date'})
    ).toBeInTheDocument();
  });

  it('should only render the text if the column is not sortable', () => {
    render(<ColumnHeader label="Start Date" />, {
      wrapper: Wrapper,
    });
    expect(screen.getByText('Start Date')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Sort by Start Date'})
    ).not.toBeInTheDocument();
  });

  it('should toggle sorting', async () => {
    const {user} = render(<ColumnHeader label="Version" sortKey="version" />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByRole('button', {name: 'Sort by Version'}));

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?sort=version%2Bdesc$/
    );
  });

  it('should toggle sorting correctly when field is sorted by default', async () => {
    const {user} = render(
      <ColumnHeader label="Version" sortKey="version" isDefault />,
      {
        wrapper: Wrapper,
      }
    );

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByRole('button', {name: 'Sort by Version'}));

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?sort=version%2Basc$/
    );
  });
});
