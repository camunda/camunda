/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {Popover} from 'components';

import EntityName from './EntityName';

it('should show details content in a popover', () => {
  const node = shallow(<EntityName details="some details" name="My Report" />);

  expect(node.find(Popover)).toExist();
  expect(node.find(Popover).prop('children')).toBe('some details');
});

it('should render a Link if linkTo is set', () => {
  const node = shallow(<EntityName linkTo="report/1/" name="My Report" />);

  expect(node.find('h1')).not.toExist();
  expect(node.find('Link')).toExist();
  expect(node.find('Link').prop('to')).toBe('report/1/');
});
