import React from 'react';
import {shallow} from 'enzyme';

import ExpandButton from './ExpandButton';
import {ICON_DIRECTION} from 'modules/constants/expandIcon';
import * as Styled from './styled';

describe('ExpandButton', () => {
  it('should render Styled.ExpandButton with click listener', () => {
    // given
    const onClick = jest.fn();
    const node = shallow(
      <ExpandButton onClick={onClick} iconDirection={ICON_DIRECTION.UP} />
    );

    // then
    const StyledExpandButtonNode = node.find(Styled.ExpandButton);
    expect(StyledExpandButtonNode).toHaveLength(1);
    expect(StyledExpandButtonNode.prop('onClick')).toBe(onClick);
  });

  it('should render Up icon if icon direction is UP', () => {
    // given
    const node = shallow(<ExpandButton iconDirection={ICON_DIRECTION.UP} />);

    // then
    expect(node.find(Styled.Up)).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });

  it('should render Down icon if icon direction is DOWN', () => {
    // given
    const node = shallow(<ExpandButton iconDirection={ICON_DIRECTION.DOWN} />);

    // then
    expect(node.find(Styled.Down)).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });

  it('should render Left icon if icon direction is LEFT', () => {
    // given
    const node = shallow(<ExpandButton iconDirection={ICON_DIRECTION.LEFT} />);

    // then
    expect(node.find(Styled.Left)).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });

  it('should render Right icon if icon direction is RIGHT', () => {
    // given
    const node = shallow(<ExpandButton iconDirection={ICON_DIRECTION.RIGHT} />);

    // then
    expect(node.find(Styled.Right)).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });
});
