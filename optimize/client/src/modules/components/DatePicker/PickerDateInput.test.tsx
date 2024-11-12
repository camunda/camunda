/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {PickerDateInput} from './PickerDateInput';

const props = {
  id: 'id',
  labelText: 'label',
  onChange: jest.fn(),
  onSubmit: jest.fn(),
};

it('should create a text input field', () => {
  const node = shallow(<PickerDateInput {...props} />);

  expect(node.find('TextInput')).toExist();
});

it('should have field with value equal to formated date', () => {
  const node = shallow(<PickerDateInput {...props} value="test" />);

  expect(node.find('TextInput')).toHaveValue('test');
});

it('should trigger onDateChange callback when input changes to valid date', () => {
  const spy = jest.fn();
  const node = shallow(<PickerDateInput {...props} onChange={spy} />);

  node.find('TextInput').simulate('change', {
    target: {
      value: '2016-05-07',
    },
  });

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0]).toBe('2016-05-07');
});

it('should trigger onSubmit when pressing the enter key', () => {
  const spy = jest.fn();
  const node = shallow(<PickerDateInput {...props} onSubmit={spy} />);

  node.find('TextInput').simulate('keyDown', {key: 'Enter'});

  expect(spy).toHaveBeenCalled();
});

it('should add invalid prop to true when input changes to invalid date', () => {
  const node = shallow(<PickerDateInput {...props} value="invalidValue" invalid={true} />);

  expect(node.find('.PickerDateInput')).toHaveClassName('isInvalid');
  expect(node.find('TextInput').prop('invalid')).toBe(true);
});
