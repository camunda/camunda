import React from 'react';
import {shallow} from 'enzyme';

import HeaderSortIcon from './HeaderSortIcon';
import * as Styled from './styled';

describe('HeaderSortIcon', () => {
  it('should render SortIcon with sortOrder null', () => {
    // given
    const mockProps = {
      sorting: {sortBy: 'foo', sortOrder: 'asc'},
      sortKey: 'bar',
      handleSorting: jest.fn()
    };
    const node = shallow(<HeaderSortIcon {...mockProps} />);

    // then
    const SortIconNode = node.find(Styled.SortIcon);
    expect(SortIconNode).toHaveLength(1);
    expect(SortIconNode.prop('sortOrder')).toBe(null);
    expect(node).toMatchSnapshot();
  });

  it('should render SortIcon with sortOrder', () => {
    // given
    const mockProps = {
      sorting: {sortBy: 'foo', sortOrder: 'asc'},
      sortKey: 'foo',
      handleSorting: jest.fn()
    };
    const node = shallow(<HeaderSortIcon {...mockProps} />);

    // then
    const SortIconNode = node.find(Styled.SortIcon);
    expect(SortIconNode).toHaveLength(1);
    expect(SortIconNode.prop('sortOrder')).toBe(mockProps.sorting.sortOrder);
    expect(node).toMatchSnapshot();
  });

  it('should call handleSorting() with sortKey on click', () => {
    // given
    const mockProps = {
      sorting: {sortBy: 'foo', sortOrder: 'asc'},
      sortKey: 'foo',
      handleSorting: jest.fn()
    };
    const node = shallow(<HeaderSortIcon {...mockProps} />);
    const SortIconNode = node.find(Styled.SortIcon);

    // when
    SortIconNode.simulate('click');

    // then
    expect(mockProps.handleSorting).toBeCalledWith(mockProps.sortKey);
  });

  it('should not call handleSorting() if it is disabled ', () => {
    // given
    const mockProps = {
      sorting: {sortBy: 'foo', sortOrder: 'asc'},
      sortKey: 'foo',
      handleSorting: jest.fn(),
      disabled: true
    };
    const node = shallow(<HeaderSortIcon {...mockProps} />);
    const SortIconNode = node.find(Styled.SortIcon);

    // when
    SortIconNode.simulate('click');

    // then
    expect(mockProps.handleSorting).not.toHaveBeenCalled();
    expect(SortIconNode.prop('sortOrder')).toBe(null);
    expect(SortIconNode.prop('disabled')).toBe(true);
    expect(node).toMatchSnapshot();
  });
});
