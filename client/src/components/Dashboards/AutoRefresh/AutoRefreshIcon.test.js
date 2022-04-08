/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {mount} from 'enzyme';

import AutoRefreshIcon from './AutoRefreshIcon';

jest.mock('components', () => {
  return {
    Icon: ({type, children}) => (
      <span>
        Type: {type} {children}
      </span>
    ),
  };
});

it('should show normal autorefreshicon when no interval is set', () => {
  const node = mount(<AutoRefreshIcon />);

  expect(node).toIncludeText('Type: autorefresh');
});

it('should show a custom svg when interval is set', () => {
  const node = mount(<AutoRefreshIcon interval={5} />);

  expect(node.find('svg')).toExist();
});

it('should fill the svg circle according to the time passed', () => {
  const originalNow = Date.now;
  Date.now = () => 1;

  const node = mount(<AutoRefreshIcon interval={6} />);

  Date.now = () => 4;

  node.instance().animate();

  expect(node.find('.AutoRefreshIcon__outline').html()).toContain('d="M 8 1 A 7 7 0 0 1 8 15"');

  Date.now = originalNow;
});
