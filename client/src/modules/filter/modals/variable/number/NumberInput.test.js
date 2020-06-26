/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import NumberInput from './NumberInput';
import {Input, LabeledInput} from 'components';

import {shallow} from 'enzyme';

const props = {
  filter: NumberInput.defaultFilter,
  setValid: jest.fn(),
  changeFilter: jest.fn(),
};

beforeEach(() => {
  props.changeFilter.mockClear();
});

it('should be initialized with an empty variable value', () => {
  expect(NumberInput.defaultFilter.values).toEqual(['']);
});

it('should store the input in the state value array at the correct position', () => {
  const node = shallow(
    <NumberInput {...props} filter={{operator: 'in', values: ['value0', 'value1', 'value2']}} />
  );

  node
    .find('.valueFields')
    .find(Input)
    .at(1)
    .simulate('change', {
      target: {getAttribute: jest.fn().mockReturnValue(1), value: 'newValue'},
    });

  expect(props.changeFilter).toHaveBeenCalledWith({
    operator: 'in',
    values: ['value0', 'newValue', 'value2'],
  });
});

it('should display the possibility to add another value', () => {
  const node = shallow(<NumberInput {...props} />);

  expect(node.find('.addValueButton')).toExist();
});

it('should add another value when clicking add another value button', () => {
  const node = shallow(<NumberInput {...props} filter={{operator: 'in', values: ['1']}} />);

  node.find('.addValueButton').simulate('click', {preventDefault: jest.fn()});

  expect(props.changeFilter).toHaveBeenCalledWith({
    operator: 'in',
    values: ['1', ''],
  });
});

it('should not have the possibility to remove the value if there is only one value', () => {
  const node = shallow(<NumberInput {...props} />);

  expect(node.find('.removeItemButton')).not.toExist();
});

it('should have the possibility to remove a value if there are multiple values', () => {
  const node = shallow(<NumberInput {...props} filter={{operator: 'in', values: ['1', '2']}} />);

  expect(node.find('.removeItemButton').length).toBe(2);
});

it('should remove all values except the first one if operator is "is less/greater than"', () => {
  const node = shallow(
    <NumberInput
      {...props}
      filter={{operator: 'in', values: ['123', '12', '17'], includeUndefined: false}}
    />
  );

  node.instance().setOperator('<')({preventDefault: () => null});
  expect(props.changeFilter).toHaveBeenCalledWith({
    operator: '<',
    values: ['123'],
    includeUndefined: false,
  });
});

it('should not show the add value button for greater and less than operators', () => {
  const node = shallow(<NumberInput {...props} filter={{operator: '<', values: ['']}} />);

  expect(node.find('.addValueButton')).not.toExist();
});

it('should disable add filter button if provided value is invalid', () => {
  const spy = jest.fn();
  const node = shallow(<NumberInput {...props} setValid={spy} />);

  node.setProps({filter: {operator: 'in', values: ['123xxxx'], includeUndefined: false}});

  expect(spy).toHaveBeenCalledWith(false);
});

it('should set includeUndefined to true when enabling the checkbox"', () => {
  const filter = {operator: 'in', values: ['123', '12', '17'], includeUndefined: false};
  const node = shallow(<NumberInput {...props} filter={filter} />);

  node.find(LabeledInput).prop('onChange')({target: {checked: true}});
  expect(props.changeFilter).toHaveBeenCalledWith({...filter, includeUndefined: true});
});

it('should parse an existing filter', () => {
  const parsed = NumberInput.parseFilter({data: {data: {values: ['123', null], operator: 'in'}}});

  expect(parsed).toEqual({operator: 'in', values: ['123'], includeUndefined: true});
});

it('should convert includeUndefined to a null entry in filter values', () => {
  const spy = jest.fn();
  NumberInput.addFilter(
    spy,
    'variable',
    {name: 'aVariableName', type: 'long'},
    {operator: 'in', values: ['123'], includeUndefined: true},
    false
  );

  expect(spy).toHaveBeenCalledWith({
    data: {data: {operator: 'in', values: ['123', null]}, name: 'aVariableName', type: 'long'},
    type: 'variable',
  });
});
