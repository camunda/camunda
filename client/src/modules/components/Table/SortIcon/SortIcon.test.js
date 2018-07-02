import React from 'react';
import {shallow} from 'enzyme';

import SortIcon from './SortIcon';
import * as Styled from './styled';

describe('SortIcon', () => {
  it('should render an Up icon if order is asc', () => {
    // given
    const node = shallow(<SortIcon order="asc" />);

    // then
    const UpNode = node.find(Styled.Up);
    expect(UpNode).toHaveLength(1);
    expect(UpNode.prop('order')).toBe('asc');
    expect(node).toMatchSnapshot();
  });

  it('should render a Down icon if order is desc', () => {
    // given
    const node = shallow(<SortIcon order="desc" />);

    // then
    const UpNode = node.find(Styled.Down);
    expect(UpNode).toHaveLength(1);
    expect(UpNode.prop('order')).toBe('desc');
    expect(node).toMatchSnapshot();
  });

  it('should render a Down icon if order is null', () => {
    // given
    const node = shallow(<SortIcon order={null} />);

    // then
    const UpNode = node.find(Styled.Down);
    expect(UpNode).toHaveLength(1);
    expect(UpNode.prop('order')).toBe(null);
    expect(node).toMatchSnapshot();
  });
});
