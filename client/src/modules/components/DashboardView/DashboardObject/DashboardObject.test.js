/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import DashboardObject from './DashboardObject';

const props = {
  x: 1,
  y: 1,
  width: 2,
  height: 2,
  tileDimensions: {
    outerWidth: 12,
    innerWidth: 6,
    outerHeight: 12
  }
};

it('should have all positioning styles', () => {
  const node = mount(<DashboardObject {...props} />);

  expect(node.find('.DashboardObject')).toHaveStyle('top', 14);
  expect(node.find('.DashboardObject')).toHaveStyle('left', 14);
  expect(node.find('.DashboardObject')).toHaveStyle('width', 19);
  expect(node.find('.DashboardObject')).toHaveStyle('height', 19);
});

it('should include the provided children', () => {
  const node = mount(<DashboardObject {...props}>Content</DashboardObject>);

  expect(node).toIncludeText('Content');
});
