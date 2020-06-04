/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import SelectionFilter from './SelectionFilter';

const props = {
  filter: null,
  type: 'String',
  config: {
    operator: 'not in',
    values: ['aStringValue', null],
  },
  setFilter: jest.fn(),
};

beforeEach(() => {
  props.setFilter.mockClear();
});

it('should show the operator when no value is selected', () => {
  const node = shallow(<SelectionFilter {...props} />);

  expect(node.find('Popover').prop('title')).toMatchSnapshot();
});

it('should allow selecting values', () => {
  const node = shallow(<SelectionFilter {...props} />);

  const valueSwitch = node.find('Switch').first();

  expect(valueSwitch).toExist();
  expect(valueSwitch.prop('label')).toBe('aStringValue');

  valueSwitch.simulate('change', {target: {checked: true}});

  expect(props.setFilter).toHaveBeenCalledWith({operator: 'not in', values: ['aStringValue']});
});

it('should abbreviate multiple string selections', () => {
  const node = shallow(
    <SelectionFilter {...props} filter={{operator: 'not in', values: ['aStringValue', null]}} />
  );

  expect(node.find('Popover').prop('title')).toMatchSnapshot();
});

it('should show a hint depending on the operator', () => {
  const node = shallow(<SelectionFilter {...props} />);

  expect(node.find('.hint').text()).toBe('Values linked by nor logic');

  node.setProps({config: {operator: 'in', values: []}});
  expect(node.find('.hint').text()).toBe('Values linked by or logic');

  node.setProps({config: {operator: '<', values: []}});
  expect(node.find('.hint').text()).toBe('');
});
