/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter} from 'react-router-dom';
import {render, screen} from 'common/testing/testing-library';
import {ColumnHeader} from './ColumnHeader';
import {Table, TableHead, TableRow} from '@carbon/react';
import {LocationLog} from 'common/testing/LocationLog';

function getWrapper(initialEntry: string = '/') {
  const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
    <MemoryRouter initialEntries={[initialEntry]}>
      <Table>
        <TableHead>
          <TableRow>{children}</TableRow>
        </TableHead>
      </Table>
      <LocationLog />
    </MemoryRouter>
  );

  return Wrapper;
}

describe('<ColumnHeader />', () => {
  it('should render non-sortable header when no sortKey is provided', () => {
    render(
      <ColumnHeader
        label="Status"
        isDisabled={false}
        sortKey={undefined}
        isDefault={false}
      >
        Status
      </ColumnHeader>,
      {
        wrapper: getWrapper(),
      },
    );

    const header = screen.getByText('Status');
    expect(header).toBeInTheDocument();
    expect(header.closest('th')).not.toHaveAttribute('aria-sort');
  });

  it('should render non-sortable header when isDisabled is true', () => {
    render(
      <ColumnHeader
        label="Details"
        isDisabled={true}
        sortKey="details"
        isDefault={false}
      >
        Details
      </ColumnHeader>,
      {
        wrapper: getWrapper(),
      },
    );

    const header = screen.getByText('Details');
    expect(header).toBeInTheDocument();
    expect(header.closest('th')).not.toHaveAttribute('aria-sort');
  });

  it('should show default sort indicator when isDefault and no URL param', () => {
    render(
      <ColumnHeader
        label="Time"
        isDisabled={false}
        sortKey="timestamp"
        isDefault={true}
      >
        Time
      </ColumnHeader>,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByRole('columnheader')).toHaveAttribute(
      'aria-sort',
      'descending',
    );
  });

  it('should navigate on click and update URL', async () => {
    const {user} = render(
      <ColumnHeader
        label="Operation"
        isDisabled={false}
        sortKey="operationType"
        isDefault={false}
      >
        Operation
      </ColumnHeader>,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(screen.getByRole('button', {name: /sort by operation/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/');
    expect(screen.getByTestId('search')).toHaveTextContent(
      'sort=operationType%2Bdesc',
    );
  });

  it('should toggle sort order when clicking same column', async () => {
    const {user} = render(
      <ColumnHeader
        label="Time"
        isDisabled={false}
        sortKey="timestamp"
        isDefault={false}
      >
        Operation
      </ColumnHeader>,
      {
        wrapper: getWrapper('/?sort=timestamp%2Bdesc'),
      },
    );

    expect(screen.getByRole('columnheader')).toHaveAttribute(
      'aria-sort',
      'descending',
    );

    await user.click(screen.getByRole('button', {name: /sort by time/i}));

    expect(screen.getByTestId('search')).toHaveTextContent(
      'sort=timestamp%2Basc',
    );
  });

  it('should show correct sort direction based on URL params', () => {
    render(
      <ColumnHeader
        label="Actor"
        isDisabled={false}
        sortKey="actorId"
        isDefault={false}
      >
        Actor
      </ColumnHeader>,
      {
        wrapper: getWrapper('/?sort=actorId%2Basc'),
      },
    );

    expect(screen.getByRole('columnheader')).toHaveAttribute(
      'aria-sort',
      'ascending',
    );
  });

  it('should show no sort indicator for inactive column', () => {
    render(
      <ColumnHeader
        label="Operation"
        isDisabled={false}
        sortKey="operationType"
        isDefault={false}
      >
        Operation
      </ColumnHeader>,
      {
        wrapper: getWrapper('/?sort=timestamp%2Basc'),
      },
    );

    expect(screen.getByRole('columnheader')).toHaveAttribute(
      'aria-sort',
      'none',
    );
  });
});
