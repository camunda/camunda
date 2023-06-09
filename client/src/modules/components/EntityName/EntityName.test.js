/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {CarbonPopover} from 'components';

import EntityName from './EntityName';

it('should show details content in a popover', () => {
  const node = shallow(<EntityName details="some details">My Report</EntityName>);

  expect(node.find(CarbonPopover)).toExist();
  expect(node.find(CarbonPopover).prop('children')).toBe('some details');
});

it('should render a Link if linkTo is set', () => {
  const node = shallow(<EntityName linkTo="report/1/">My Report</EntityName>);

  expect(node.find('h1')).not.toExist();
  expect(node.find('Link')).toExist();
  expect(node.find('Link').prop('to')).toBe('report/1/');
});
