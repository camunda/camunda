/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import SubNav from './SubNav';

it('renders without crashing', () => {
  shallow(<SubNav />);
});

it('renders itself and its children', () => {
  const node = shallow(
    <SubNav>
      <div>foo</div>
    </SubNav>
  );

  expect(node.find('.SubNav')).toExist();
  expect(node).toIncludeText('foo');
});
