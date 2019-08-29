/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import ColumnHeader from './ColumnHeader';

describe('ColumnHeader', () => {
  const mockPropsWithSorting = {
    active: false,
    label: 'Start Time',
    onSort: jest.fn(),
    sortKey: 'startDate',
    sorting: {}
  };

  it('should render a button if the column is sortable', () => {
    const node = shallow(<ColumnHeader {...mockPropsWithSorting} />);
    expect(node.text()).toBe(mockPropsWithSorting.label);
    expect(node.prop('onClick')).not.toBe(undefined);
  });

  it('should call onSort when clicking', () => {
    const node = shallow(<ColumnHeader {...mockPropsWithSorting} />);

    node.simulate('click');

    expect(mockPropsWithSorting.onSort).toHaveBeenCalledWith(
      mockPropsWithSorting.sortKey
    );

    mockPropsWithSorting.onSort.mockClear();
  });

  it('should call not call onSort when clicking id the column is disabled', () => {
    const node = shallow(<ColumnHeader {...mockPropsWithSorting} disabled />);
    node.simulate('click');
    expect(mockPropsWithSorting.onSort).not.toHaveBeenCalled();
  });

  it('should only render the text if the column is not sortable', () => {
    const node = shallow(<ColumnHeader label="Start time" />);
    expect(node.text()).toBe('Start time');
    expect(node.prop('onClick')).toBe(undefined);
    expect(node.find('Styled.SortIcon').length).toBe(0);
  });
});
