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

    expect(
      screen.getByRole('checkbox', {name: 'Select all instances'})
    ).not.toBeChecked();
    await user.click(
      screen.getByRole('checkbox', {name: 'Select all instances'})
    );

    expect(
      screen.getByRole('checkbox', {name: 'Select all instances'})
    ).toBeChecked();
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
      name: /Select instance/,
    });

    await user.click(firstCheckbox!);
    expect(firstRow?.onSelect).toHaveBeenCalledTimes(1);

    await user.click(secondCheckbox!);
    expect(secondRow?.onSelect).toHaveBeenCalledTimes(1);

    expect(firstRow?.checkIsSelected).toHaveBeenCalledTimes(1);
    expect(secondRow?.checkIsSelected).toHaveBeenCalledTimes(1);
  });
});
