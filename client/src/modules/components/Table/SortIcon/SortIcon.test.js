import React from 'react';
import {shallow} from 'enzyme';

import SortIcon from './SortIcon';
import {ORDER} from './constants';
import * as Styled from './styled';

describe('SortIcon', () => {
  it('should render an Up icon if order is asc', () => {
    // given
    const node = shallow(<SortIcon order="asc" />);

    // then
    const UpNode = node.find(Styled.Up);
    expect(UpNode).toHaveLength(1);
    expect(UpNode.prop('order')).toBe(ORDER.ASC);
    expect(node).toMatchSnapshot();
  });

  it('should render a Down icon if order is desc', () => {
    // given
    const node = shallow(<SortIcon order="desc" />);

    // then
    const UpNode = node.find(Styled.Down);
    expect(UpNode).toHaveLength(1);
    expect(UpNode.prop('order')).toBe(ORDER.DESC);
    expect(node).toMatchSnapshot();
  });

  it('should render a Down icon if order is undefined', () => {
    // given
    const node = shallow(<SortIcon order={undefined} />);

    // then
    const UpNode = node.find(Styled.Down);
    expect(UpNode).toHaveLength(1);
    expect(UpNode.prop('order')).toBe(undefined);
    expect(node).toMatchSnapshot();
  });
});
