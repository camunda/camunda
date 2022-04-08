/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {PickerDateInput} from './PickerDateInput';
import {Input} from 'components';

it('should create a text input field', () => {
  const node = shallow(<PickerDateInput />);

  expect(node.find(Input)).toExist();
});

it('should have field with value equal to formated date', () => {
  const node = shallow(<PickerDateInput value="test" />);

  expect(node.find(Input)).toHaveValue('test');
});

it('should trigger onDateChange callback when input changes to valid date', () => {
  const spy = jest.fn();
  const node = shallow(<PickerDateInput onChange={spy} />);

  node.find(Input).simulate('change', {
    target: {
      value: '2016-05-07',
    },
  });

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0]).toBe('2016-05-07');
});

it('should trigger onSubmit when pressing the enter key', () => {
  const spy = jest.fn();
  const node = shallow(<PickerDateInput onSubmit={spy} />);

  node.find(Input).simulate('keyDown', {key: 'Enter'});

  expect(spy).toHaveBeenCalled();
});

it('should add isInvalid prop to true when input changes to invalid date', () => {
  const node = shallow(<PickerDateInput value="invalidValue" isInvalid={true} />);

  expect(node.find('.PickerDateInputWarning')).toExist();
});
