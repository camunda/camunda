/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {MenuItemSelectable} from '@carbon/react';

import {MenuDropdown} from 'components';

import {FilterBar, DATE_PRESETS} from './FilterBar';

const props = {
  preset: '30d',
  onPresetChange: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should render one option for each date preset', () => {
  const node = shallow(<FilterBar {...props} />);

  expect(node.find(MenuItemSelectable)).toHaveLength(DATE_PRESETS.length);
});

it('should mark only the active preset as selected', () => {
  const node = shallow(<FilterBar {...props} preset="7d" />);

  const selectedItems = node.find(MenuItemSelectable).filterWhere((n) => n.prop('selected'));
  expect(selectedItems).toHaveLength(1);
  expect(selectedItems.prop('label')).toBe('Last 7 days');
});

it('should show the active preset label in the dropdown trigger', () => {
  const node = shallow(<FilterBar {...props} preset="3m" />);

  expect(node.find(MenuDropdown).prop('label')).toBe('Last 3 months');
});

it('should call onPresetChange with the preset id when an option is selected', () => {
  const node = shallow(<FilterBar {...props} />);

  node.find(MenuItemSelectable).at(0).prop('onChange')();

  expect(props.onPresetChange).toHaveBeenCalledWith('7d');
});

it('should default to Last 30 days being selected when preset is 30d', () => {
  const node = shallow(<FilterBar {...props} preset="30d" />);

  const selectedItem = node.find(MenuItemSelectable).filterWhere((n) => n.prop('selected'));
  expect(selectedItem.prop('label')).toBe('Last 30 days');
});
