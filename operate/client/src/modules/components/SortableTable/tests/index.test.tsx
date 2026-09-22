/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {SortableTable} from '../';
import {mockProps, mockSelectableProps, Wrapper} from './mocks';

describe('SortableTable', () => {
  it('should select all rows', async () => {
    const {user} = render(
      <SortableTable {...mockProps} {...mockSelectableProps} state="content" />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(screen.getByRole('checkbox', {name: 'Select all rows'}));

    expect(
      screen.queryByRole('checkbox', {name: 'Select all rows'}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('checkbox', {name: 'Unselect all rows'}),
    ).toBeInTheDocument();
    expect(mockSelectableProps.onSelectAll).toHaveBeenCalledTimes(1);
  });

  it('should select one row', async () => {
    const {user} = render(
      <SortableTable {...mockProps} {...mockSelectableProps} state="content" />,
      {
        wrapper: Wrapper,
      },
    );

    const [firstRow, secondRow] = mockProps.rows;

    const [firstCheckbox, secondCheckbox] = screen.getAllByRole('checkbox', {
      name: /Select row/,
    });

    await user.click(firstCheckbox!);
    expect(mockSelectableProps.onSelect).toHaveBeenNthCalledWith(
      1,
      firstRow!.id,
    );

    await user.click(secondCheckbox!);
    expect(mockSelectableProps.onSelect).toHaveBeenNthCalledWith(
      2,
      secondRow!.id,
    );

    expect(mockSelectableProps.checkIsRowSelected).toHaveBeenNthCalledWith(
      1,
      firstRow!.id,
    );
    expect(mockSelectableProps.checkIsRowSelected).toHaveBeenNthCalledWith(
      2,
      secondRow!.id,
    );
  });

  it('should not render expanded content while a row is collapsed', async () => {
    const [firstRow] = mockProps.rows;

    const {user} = render(
      <SortableTable
        {...mockProps}
        state="content"
        isExpandable
        expandedContent={{
          [firstRow!.id]: <button>expanded action</button>,
        }}
      />,
      {wrapper: Wrapper},
    );

    // then a collapsed row keeps its expanded content out of the accessibility
    // tree, because Carbon only collapses that row to zero height, which would
    // otherwise leave the content invisible but still focusable
    expect(
      screen.queryByRole('button', {name: 'expanded action'}),
    ).not.toBeInTheDocument();

    // when the row is expanded
    await user.click(
      screen.getAllByRole('button', {name: /expand current row/i})[0]!,
    );

    // then its content becomes available
    expect(
      screen.getByRole('button', {name: 'expanded action'}),
    ).toBeInTheDocument();
  });

  it('should not announce a non sortable column as a sort control', () => {
    render(
      <SortableTable
        {...mockProps}
        state="content"
        headerColumns={[
          {header: 'Sortable Column', key: 'columnHeader1'},
          {header: 'Fixed Column', key: 'columnHeader2', isDisabled: true},
        ]}
      />,
      {wrapper: Wrapper},
    );

    expect(
      screen.getByRole('button', {name: 'Sort by Sortable Column'}),
    ).toBeInTheDocument();

    // then the disabled column is named by its own label rather than offering a
    // sort it cannot perform
    expect(
      screen.getByRole('columnheader', {name: 'Fixed Column'}),
    ).toBeInTheDocument();
    expect(screen.queryByTitle('Sort by Fixed Column')).not.toBeInTheDocument();
  });
});
