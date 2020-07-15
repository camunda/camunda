/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import ValueListInput from './ValueListInput';
import {Input, LabeledInput} from 'components';

import {shallow} from 'enzyme';

const props = {
  filter: {operator: 'in', values: [''], includeUndefined: false},
  allowMultiple: true,
  allowUndefined: true,
  onChange: jest.fn(),
};

beforeEach(() => {
  props.onChange.mockClear();
});

it('should store the input in the state value array at the correct position', () => {
  const node = shallow(
    <ValueListInput {...props} filter={{operator: 'in', values: ['value0', 'value1', 'value2']}} />
  );

  node
    .find(Input)
    .at(1)
    .simulate('change', {
      target: {getAttribute: jest.fn().mockReturnValue(1), value: 'newValue'},
    });

  expect(props.onChange).toHaveBeenCalledWith({
    operator: 'in',
    values: ['value0', 'newValue', 'value2'],
  });
});

it('should display the possibility to add another value', () => {
  const node = shallow(<ValueListInput {...props} />);

  expect(node.find('.addValueButton')).toExist();
});

it('should add another value when clicking add another value button', () => {
  const node = shallow(<ValueListInput {...props} filter={{operator: 'in', values: ['1']}} />);

  node.find('.addValueButton').simulate('click', {preventDefault: jest.fn()});

  expect(props.onChange).toHaveBeenCalledWith({
    operator: 'in',
    values: ['1', ''],
  });
});

it('should not have the possibility to remove the value if there is only one value', () => {
  const node = shallow(<ValueListInput {...props} />);

  expect(node.find('.removeItemButton')).not.toExist();
});

it('should have the possibility to remove a value if there are multiple values', () => {
  const node = shallow(<ValueListInput {...props} filter={{operator: 'in', values: ['1', '2']}} />);

  expect(node.find('.removeItemButton').length).toBe(2);
});

it('should set includeUndefined to true when enabling the checkbox"', () => {
  const filter = {operator: 'in', values: ['123', '12', '17'], includeUndefined: false};
  const node = shallow(<ValueListInput {...props} filter={filter} />);

  node.find(LabeledInput).prop('onChange')({target: {checked: true}});
  expect(props.onChange).toHaveBeenCalledWith({...filter, includeUndefined: true});
});

it('should add a provided className', () => {
  const node = shallow(<ValueListInput {...props} className="anotherClass" />);

  expect(node).toHaveClassName('anotherClass');
});

it('should not show undefined option if allowUndefined is not set', () => {
  const node = shallow(<ValueListInput {...props} allowUndefined={undefined} />);

  expect(node.find('.undefinedOption')).not.toExist();
});

it('should not allow adding another value if allowMultiple is not set', () => {
  const node = shallow(<ValueListInput {...props} allowMultiple={undefined} />);

  expect(node.find('.addValueButton')).not.toExist();
});
