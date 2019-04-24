/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import NumberInput from './NumberInput';
import {Input} from 'components';

import {shallow} from 'enzyme';

const props = {
  filter: NumberInput.defaultFilter,
  setValid: jest.fn()
};

it('should be initialized with an empty variable value', () => {
  expect(NumberInput.defaultFilter.values).toEqual(['']);
});

it('should store the input in the state value array at the correct position', () => {
  const spy = jest.fn();
  const node = shallow(
    <NumberInput
      {...props}
      filter={{operator: 'in', values: ['value0', 'value1', 'value2']}}
      changeFilter={spy}
    />
  );

  node
    .find('.VariableFilter__valueFields')
    .find(Input)
    .at(1)
    .simulate('change', {
      target: {getAttribute: jest.fn().mockReturnValue(1), value: 'newValue'}
    });

  expect(spy).toHaveBeenCalledWith({operator: 'in', values: ['value0', 'newValue', 'value2']});
});

it('should display the possibility to add another value', () => {
  const node = shallow(<NumberInput {...props} />);

  expect(node.find('.NumberInput__addValueButton')).toBePresent();
});

it('should add another value when clicking add another value button', () => {
  const spy = jest.fn();
  const node = shallow(<NumberInput {...props} changeFilter={spy} />);

  node.find('.NumberInput__addValueButton').simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalledWith({operator: 'in', values: ['', '']});
});

it('should not have the possibility to remove the value if there is only one value', () => {
  const node = shallow(<NumberInput {...props} />);

  expect(node.find('.NumberInput__removeItemButton').exists()).toBeFalsy();
});

it('should have the possibility to remove a value if there are multiple values', () => {
  const node = shallow(<NumberInput {...props} filter={{operator: 'in', values: ['1', '2']}} />);

  expect(node.find('.NumberInput__removeItemButton').length).toBe(2);
});

it('should remove all values except the first one if operator is "is less/greater than"', () => {
  const spy = jest.fn();
  const node = shallow(
    <NumberInput
      {...props}
      filter={{operator: 'in', values: ['123', '12', '17']}}
      changeFilter={spy}
    />
  );

  node.instance().setOperator('<')({preventDefault: () => null});
  expect(spy).toHaveBeenCalledWith({operator: '<', values: ['123']});
});

it('should not show the add value button for greater and less than operators', () => {
  const node = shallow(<NumberInput {...props} filter={{operator: '<', values: ['']}} />);

  expect(node.find('.NumberInput__addValueButton')).not.toBePresent();
});

it('should disable add filter button if provided value is invalid', () => {
  const spy = jest.fn();
  const node = shallow(<NumberInput {...props} setValid={spy} />);

  node.setProps({filter: {operator: 'in', values: ['123xxxx']}});

  expect(spy).toHaveBeenCalledWith(false);
});
