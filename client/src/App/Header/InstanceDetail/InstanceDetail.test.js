/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import * as Styled from './styled';

import InstanceDetail from './InstanceDetail';

describe('InstanceDetail', () => {
  it('should render state icon and instance id', () => {
    // given
    const instanceMock = {id: 'foo', state: 'ACTIVE'};
    const node = shallow(<InstanceDetail instance={instanceMock} />);

    // then
    const StateIconNode = node.find(Styled.StateIcon);
    expect(StateIconNode).toHaveLength(1);
    expect(StateIconNode.prop('state')).toBe(instanceMock.state);
    expect(node.text()).toContain(instanceMock.id);
    expect(node).toMatchSnapshot();
  });
});
