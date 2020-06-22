/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import SortIcon from './index';
import {SORT_ORDER} from 'modules/constants';
import * as Styled from './styled';

describe('SortIcon', () => {
  it('should render an Up icon if order is asc', () => {
    // given
    const node = shallow(<SortIcon sortOrder="asc" />);

    // then
    const UpNode = node.find(Styled.Up);
    expect(UpNode).toHaveLength(1);
    expect(UpNode.prop('sortOrder')).toBe(SORT_ORDER.ASC);
    expect(node).toMatchSnapshot();
  });

  it('should render a Down icon if order is desc', () => {
    // given
    const node = shallow(<SortIcon sortOrder="desc" />);

    // then
    const UpNode = node.find(Styled.Down);
    expect(UpNode).toHaveLength(1);
    expect(UpNode.prop('sortOrder')).toBe(SORT_ORDER.DESC);
    expect(node).toMatchSnapshot();
  });

  it('should render a Down icon if order is null', () => {
    // given
    const node = shallow(<SortIcon sortOrder={null} />);

    // then
    const UpNode = node.find(Styled.Down);
    expect(UpNode).toHaveLength(1);
    expect(UpNode.prop('sortOrder')).toBe(null);
    expect(node).toMatchSnapshot();
  });
});
