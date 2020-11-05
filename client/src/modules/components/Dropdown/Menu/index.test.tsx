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
  let node: any;
  beforeEach(() => {
    node = shallow(
      // @ts-expect-error ts-migrate(2769) FIXME: Type 'string' is not assignable to type '"top" | "... Remove this comment to see the full error message
      <Menu onKeyDown={jest.fn()} placement={DROPDOWN_PLACEMENT.TOP}>
        <span>I am a Dropdown.Option Component</span>
      </Menu>
    );
  });

  it('should renders its children', () => {
    expect(node.find(Styled.Li)).toExist();

    //when
    node = shallow(
      // @ts-expect-error ts-migrate(2769) FIXME: Type 'string' is not assignable to type '"top" | "... Remove this comment to see the full error message
      <Menu onKeyDown={jest.fn()} placement={DROPDOWN_PLACEMENT.TOP} />
    );
    //then
    expect(node.find(Styled.Li)).not.toExist();
  });
});
