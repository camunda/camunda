/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import CountTargetInput from './CountTargetInput';

const validProps = {
  baseline: 10,
  target: 200,
  disabled: false,
  onChange: jest.fn(),
};

it('should display the current target values', () => {
  const node = shallow(<CountTargetInput {...validProps} />);

  expect(node.find('TextInput').first()).toHaveValue(200);
  expect(node.find({labelText: 'Baseline'})).toHaveValue(10);
});

it('should update target values', () => {
  const spy = jest.fn();
  const node = shallow(<CountTargetInput {...validProps} onChange={spy} />);

  node
    .find('TextInput')
    .first()
    .simulate('change', {target: {value: '73'}});

  expect(spy).toHaveBeenCalledWith('target', '73');
});

it('should show an error message if an input field does not have a valid value', async () => {
  const node = shallow(<CountTargetInput {...validProps} baseline={'notAValidValue'} />);

  expect(node.find('TextInput').at(1).prop('invalid')).toBe(true);
  expect(node.find('TextInput').at(1).prop('invalidText')).toBe('Enter a positive number');
});

it('should show an error message if target is below baseline', async () => {
  const node = shallow(<CountTargetInput {...validProps} baseline={50} target={4} />);

  expect(node.find('TextInput').at(0).prop('invalid')).toBe(true);
  expect(node.find('TextInput').at(0).prop('invalidText')).toBe(
    'Target must be greater than baseline'
  );
});

it('should invoke the onChange prop on button click', async () => {
  const spy = jest.fn();
  const node = shallow(<CountTargetInput {...validProps} onChange={spy} />);

  node.find('RadioButton').first().simulate('click');

  expect(spy).toHaveBeenCalledWith('isBelow', false);
});

it('should hide baseline if specified', async () => {
  const node = shallow(<CountTargetInput {...validProps} hideBaseLine />);

  expect(node.find('TextInput').at(1)).not.toExist();
});
