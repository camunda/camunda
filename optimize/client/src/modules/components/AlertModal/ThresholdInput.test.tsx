/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {TextInput} from '@carbon/react';

import {Select} from 'components';

import ThresholdInput from './ThresholdInput';

const props = {
  labelText: 'label',
  id: 'id',
  type: 'duration',
  value: {value: '123', unit: 'minutes'},
  onChange: jest.fn(),
};

it('should contain a single input field if the type is not duration', () => {
  const node = shallow(<ThresholdInput {...props} type="number" value="123" />);

  expect(node.find(TextInput)).toExist();
  expect(node.find(Select)).not.toExist();
});

it('should contain a input and a select field if the type is duration', () => {
  const node = shallow(<ThresholdInput {...props} />);

  expect(node.find(TextInput)).toExist();
  expect(node.find(Select)).toExist();
});

it('should call the change handler when changing the value', () => {
  const spy = jest.fn();
  const node = shallow(<ThresholdInput {...props} onChange={spy} />);

  node.find(TextInput).simulate('change', {target: {value: '1234'}});

  expect(spy).toHaveBeenCalledWith({value: '1234', unit: 'minutes'});
});
