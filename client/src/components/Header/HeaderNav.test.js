/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import HeaderNav from './HeaderNav';

it('renders without crashing', () => {
  shallow(<HeaderNav />);
});

it('renders itself and its children', () => {
  const node = shallow(
    <HeaderNav>
      <div>foo</div>
    </HeaderNav>
  );

  expect(node.find('.HeaderNav')).toExist();
  expect(node).toIncludeText('foo');
});
