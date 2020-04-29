/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Dropdown, ActionItem} from 'components';

import FiltersEdit from './FiltersEdit';

const props = {
  availableFilters: [],
  setAvailableFilters: jest.fn(),
};

beforeEach(() => {
  props.setAvailableFilters.mockClear();
});

it('should contain a dropdown to add more filters', () => {
  const node = shallow(<FiltersEdit {...props} />);

  expect(node.find(Dropdown)).toExist();

  node.find(Dropdown.Option).at(0).simulate('click');
  expect(props.setAvailableFilters).toHaveBeenCalledWith([{type: 'startDate'}]);
});

it('should show added filters', () => {
  const node = shallow(<FiltersEdit {...props} availableFilters={[{type: 'state'}]} />);

  expect(node.find(ActionItem)).toExist();
});

it('should allow removing existing filters', () => {
  const node = shallow(<FiltersEdit {...props} availableFilters={[{type: 'state'}]} />);

  node.find(ActionItem).simulate('click');

  expect(props.setAvailableFilters).toHaveBeenCalledWith([]);
});

it('should not allow adding the same filter twice', () => {
  const node = shallow(<FiltersEdit {...props} availableFilters={[{type: 'startDate'}]} />);

  expect(node.find(Dropdown.Option).at(0)).toBeDisabled();
});
