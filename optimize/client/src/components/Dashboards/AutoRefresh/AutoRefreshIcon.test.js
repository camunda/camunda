/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {mount, shallow} from 'enzyme';

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

it('should show normal refresh icon when no interval is set', () => {
  const node = shallow(<AutoRefreshIcon />);

  expect(node.find('ForwardRef(UpdateNow)')).toExist();
});

it('should show a custom svg when interval is set', () => {
  const node = shallow(<AutoRefreshIcon interval={5} />);

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
