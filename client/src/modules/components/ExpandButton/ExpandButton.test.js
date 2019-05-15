/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import ExpandButton from './ExpandButton';
import * as Styled from './styled';

describe('ExpandButton', () => {
  it('should render arrow icon', () => {
    // given
    const node = shallow(
      <ExpandButton isExpanded={false} expandTheme="collapse" />
    );

    // when
    const ArrowIconNode = node.find(Styled.ArrowIcon);

    // then
    expect(ArrowIconNode).toExist();
  });

  it('should render provided children inside the button', () => {
    // given
    const node = shallow(
      <ExpandButton expandTheme="collapse">
        <div id="child1">child node 1</div>
        <div id="child2">child node 2</div>
      </ExpandButton>
    );

    // when
    const ArrowIconNode = node.find(Styled.ArrowIcon);
    const ChildNode1 = node.find('#child1');
    const ChildNode2 = node.find('#child2');

    // then
    expect(ArrowIconNode).toExist();
    expect(ChildNode1).toExist();
    expect(ChildNode2).toExist();
    expect(node.children()).toHaveLength(3);
  });
});
