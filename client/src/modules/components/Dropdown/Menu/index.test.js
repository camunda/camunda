/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {DROPDOWN_PLACEMENT} from 'modules/constants';

import Menu from './index';
import * as Styled from './styled';

describe('DropdownMenu', () => {
  let node;
  beforeEach(() => {
    node = shallow(
      <Menu onKeyDown={jest.fn()} placement={DROPDOWN_PLACEMENT.TOP}>
        <span>I am a Dropdown.Option Component</span>
      </Menu>
    );
  });

  it('should match snapshot', async () => {
    expect(node).toMatchSnapshot();
  });

  it('should renders its children', () => {
    expect(node.find(Styled.Li)).toExist();

    //when
    node = shallow(
      <Menu onKeyDown={jest.fn()} placement={DROPDOWN_PLACEMENT.TOP} />
    );
    //then
    expect(node.find(Styled.Li)).not.toExist();
  });
});
