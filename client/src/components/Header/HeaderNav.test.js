/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import HeaderNav from './HeaderNav';

jest.mock('./HeaderNavItem', () => {
  return () => <li>fo</li>;
});

it('renders without crashing', () => {
  mount(<HeaderNav />);
});

it('renders itself and its children', () => {
  const node = mount(
    <HeaderNav>
      <div>foo</div>
    </HeaderNav>
  );

  expect(node.find('.HeaderNav')).toExist();
  expect(node).toIncludeText('foo');
});
