/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import CollapseButton from './CollapseButton';
import {DIRECTION} from 'modules/constants';
import * as Styled from './styled';

describe('CollapseButton', () => {
  it('should render Styled.CollapseButton with click listener', () => {
    // given
    const onClick = jest.fn();
    const node = shallow(
      <CollapseButton onClick={onClick} direction={DIRECTION.UP} />
    );

    // then
    const StyledCollapseButtonNode = node.find(Styled.CollapseButton);
    expect(StyledCollapseButtonNode).toHaveLength(1);
    expect(StyledCollapseButtonNode.prop('onClick')).toBe(onClick);
  });

  it('should render Up icon if icon direction is UP', () => {
    // given
    const node = shallow(<CollapseButton direction={DIRECTION.UP} />);

    // then
    expect(node.find(Styled.Up)).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });

  it('should render Down icon if icon direction is DOWN', () => {
    // given
    const node = shallow(<CollapseButton direction={DIRECTION.DOWN} />);

    // then
    expect(node.find(Styled.Down)).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });

  it('should render Left icon if icon direction is LEFT', () => {
    // given
    const node = shallow(<CollapseButton direction={DIRECTION.LEFT} />);

    // then
    expect(node.find(Styled.Left)).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });

  it('should render Right icon if icon direction is RIGHT', () => {
    // given
    const node = shallow(<CollapseButton direction={DIRECTION.RIGHT} />);

    // then
    expect(node.find(Styled.Right)).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });
});
