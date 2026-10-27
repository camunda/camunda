/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';

import {Popover} from 'components';

import SelectionFilter from './SelectionFilter';
import {getVariableValues} from './service';
import {Filter} from '@carbon/icons-react';
import {ComboBox} from '@carbon/react';

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

  const popoverButtonLabel = shallow(node.find(Popover).prop('trigger')).find(
    '.cds--list-box__label'
  );

  expect(popoverButtonLabel.text()).toContain('is not ...');
  expect(popoverButtonLabel.find(Filter)).toExist();
});

it('should allow selecting values', () => {
  const node = shallow(<SelectionFilter {...props} />);

  const valueSwitch = node.find('Toggle').first();
  expect(valueSwitch).toExist();
  expect(valueSwitch.prop('labelText')).toBe('aStringValue');

  valueSwitch.simulate('toggle', true);

  expect(props.setFilter).toHaveBeenCalledWith({operator: 'not in', values: ['aStringValue']});
});

it('should abbreviate multiple string selections', () => {
  const node = shallow(
    <SelectionFilter {...props} filter={{operator: 'not in', values: ['aStringValue', null]}} />
  );

  const popoverButtonLabel = shallow(node.find(Popover).prop('trigger')).find(
    '.cds--list-box__label'
  );

  expect(popoverButtonLabel.find(Filter)).toExist();
  expect(popoverButtonLabel.find('VariablePreview').prop('filter')).toEqual({
    operator: 'not in',
    values: ['multiple'],
  });
});

it('should show a hint depending on the operator', () => {
  const node = shallow(<SelectionFilter {...props} />);

  expect(node.find('FormGroup').prop('legendText')).toBe('Values linked by nor logic');

  node.setProps({config: {data: {operator: 'in', values: [], allowCustomValues: false}}});
  expect(node.find('FormGroup').prop('legendText')).toBe('Values linked by or logic');

  node.setProps({config: {data: {operator: '<', values: [], allowCustomValues: false}}});
  expect(node.find('FormGroup').prop('legendText')).toBe('');

  node.setProps({config: {data: {operator: 'contains', values: [], allowCustomValues: false}}});
  expect(node.find('FormGroup').prop('legendText')).toBe('Values linked by or logic');
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

  it('should add a Combobox input when adding a custom value', () => {
    const node = shallow(<SelectionFilter {...customProps} />);

    node.find('.customValueAddButton').simulate('click');

    expect(node.find(ComboBox)).toExist();
  });

  it('should load available values when opening the Combobox', async () => {
    const node = shallow(<SelectionFilter {...customProps} />);

    node.find('.customValueAddButton').simulate('click');
    // For combobox component the input change event is triggered on open (and of course when typing)
    node.find(ComboBox).simulate('inputChange', '');

    expect(getVariableValues).toHaveBeenCalledWith(['reportA'], 'stringVar', 'String', 10, '');
  });

  it('should display filtered list of custom values', () => {
    const node = shallow(<SelectionFilter {...customProps} />);

    runAllEffects();

    const customValues = node.find('.customValue');

    expect(customValues.length).toBe(1);
  });
});
