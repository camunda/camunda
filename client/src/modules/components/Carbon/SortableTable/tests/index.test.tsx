/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
      }
    );

    await user.click(screen.getByRole('checkbox', {name: 'Select all rows'}));

    expect(
      screen.queryByRole('checkbox', {name: 'Select all rows'})
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('checkbox', {name: 'Unselect all rows'})
    ).toBeInTheDocument();
    expect(mockSelectableProps.onSelectAll).toHaveBeenCalledTimes(1);
  });

  it('should select one row', async () => {
    const {user} = render(
      <SortableTable {...mockProps} {...mockSelectableProps} state="content" />,
      {
        wrapper: Wrapper,
      }
    );

    const [firstRow, secondRow] = mockProps.rows;

    const [firstCheckbox, secondCheckbox] = screen.getAllByRole('checkbox', {
      name: /Select row/,
    });

    await user.click(firstCheckbox!);
    expect(mockSelectableProps.onSelect).toHaveBeenNthCalledWith(
      1,
      firstRow!.id
    );

    await user.click(secondCheckbox!);
    expect(mockSelectableProps.onSelect).toHaveBeenNthCalledWith(
      2,
      secondRow!.id
    );

    expect(mockSelectableProps.checkIsRowSelected).toHaveBeenNthCalledWith(
      1,
      firstRow!.id
    );
    expect(mockSelectableProps.checkIsRowSelected).toHaveBeenNthCalledWith(
      2,
      secondRow!.id
    );
  });
});
