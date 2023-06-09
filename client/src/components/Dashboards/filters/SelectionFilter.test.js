/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';

import {CarbonPopover} from 'components';

import SelectionFilter from './SelectionFilter';
import {getVariableValues} from './service';

const props = {
  filter: null,
  type: 'String',
  config: {
    name: 'stringVar',
    type: 'String',
    data: {
      operator: 'not in',
      values: ['aStringValue', null],
      allowCustomValues: false,
    },
  },
  setFilter: jest.fn(),
  reports: [{id: 'reportA'}],
};

jest.mock('debouncePromise', () => () => (fn) => fn());
jest.mock('./service', () => ({getVariableValues: jest.fn().mockReturnValue([])}));

beforeEach(() => {
  props.setFilter.mockClear();
  getVariableValues.mockClear();
});

it('should show the operator when no value is selected', () => {
  const node = shallow(<SelectionFilter {...props} />);

  expect(node.find(CarbonPopover).prop('title')).toMatchSnapshot();
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

  expect(node.find(CarbonPopover).prop('title')).toMatchSnapshot();
});

it('should show a hint depending on the operator', () => {
  const node = shallow(<SelectionFilter {...props} />);

  expect(node.find('.hint').text()).toBe('Values linked by nor logic');

  node.setProps({config: {data: {operator: 'in', values: [], allowCustomValues: false}}});
  expect(node.find('.hint').text()).toBe('Values linked by or logic');

  node.setProps({config: {data: {operator: '<', values: [], allowCustomValues: false}}});
  expect(node.find('.hint').text()).toBe('');

  node.setProps({config: {data: {operator: 'contains', values: [], allowCustomValues: false}}});
  expect(node.find('.hint').text()).toBe('Values linked by or logic');
});

describe('allowCustomValues', () => {
  const customProps = update(props, {
    config: {
      data: {allowCustomValues: {$set: true}},
      defaultValues: {$set: ['value', null, undefined]},
    },
  });

  it('should render a button to add values if allowCustomValues is set', () => {
    const node = shallow(<SelectionFilter {...customProps} />);

    expect(node.find('.customValueAddButton')).toExist();
  });

  it('should add a Typeahead input when adding a custom value', () => {
    const node = shallow(<SelectionFilter {...customProps} />);

    node.find('.customValueAddButton').simulate('click');

    expect(node.find('Typeahead')).toExist();
  });

  it('should load available values when opening the Typeahead', () => {
    const node = shallow(<SelectionFilter {...customProps} />);

    node.find('.customValueAddButton').simulate('click');
    node.find('Typeahead').simulate('open');

    expect(getVariableValues).toHaveBeenCalledWith(['reportA'], 'stringVar', 'String', 10, '');
  });

  it('should display filtered list of custom values', () => {
    const node = shallow(<SelectionFilter {...customProps} />);

    runAllEffects();

    const customValues = node.find('.customValue');

    expect(customValues.length).toBe(1);
  });
});
