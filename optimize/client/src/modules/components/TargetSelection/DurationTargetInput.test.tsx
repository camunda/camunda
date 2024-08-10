/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import DurationTargetInput from './DurationTargetInput';

const validProps = {
  baseline: {value: '12', unit: 'weeks'},
  target: {value: '15', unit: 'months'},
  disabled: false,
  onChange: jest.fn(),
};

it('should display the current target values', () => {
  const node = shallow(<DurationTargetInput {...validProps} />);

  expect(node.find('TextInput').first()).toHaveValue('15');
  expect(node.find('Select').first()).toHaveValue('months');
  expect(node.find({labelText: 'Baseline'})).toHaveValue('12');
  expect(node.find('Select').at(1)).toHaveValue('weeks');
});

it('should update target values', () => {
  const spy = jest.fn();
  const node = shallow(<DurationTargetInput {...validProps} onChange={spy} />);

  node
    .find('TextInput')
    .first()
    .simulate('change', {target: {value: '73'}});

  expect(spy).toHaveBeenCalledWith('target', 'value', '73');
});

it('should show an error message if an input field does not have a valid value', async () => {
  const node = shallow(
    <DurationTargetInput {...validProps} target={{value: 'five', unit: 'seconds'}} />
  );

  expect(node.find('TextInput').at(0).prop('invalid')).toBe(true);
  expect(node.find('TextInput').at(0).prop('invalidText')).toBe('Enter a positive number');
});

it('should show an error message if target is below baseline', async () => {
  const node = shallow(
    <DurationTargetInput
      {...validProps}
      baseline={{value: 160, unit: 'seconds'}}
      target={{value: 2, unit: 'minutes'}}
    />
  );

  expect(node.find('TextInput').at(0).prop('invalid')).toBe(true);
  expect(node.find('TextInput').at(0).prop('invalidText')).toBe(
    'Target must be greater than baseline'
  );
});

it('should show an error message if an input field does not have a valid value', async () => {
  const node = shallow(
    <DurationTargetInput {...validProps} baseline={{value: 'notAValidValue', unit: ''}} />
  );

  expect(node.find('TextInput').at(1).prop('invalid')).toBe(true);
  expect(node.find('TextInput').at(1).prop('invalidText')).toBe('Enter a positive number');
});

it('should invoke the onChange prop on button click', async () => {
  const spy = jest.fn();
  const node = shallow(<DurationTargetInput {...validProps} onChange={spy} />);

  node.find('RadioButton').first().simulate('click');

  expect(spy).toHaveBeenCalledWith('target', 'isBelow', false);
});

it('should hide baseline if specified', async () => {
  const node = shallow(<DurationTargetInput {...validProps} hideBaseLine />);

  expect(node.find('TextInput').at(1)).not.toExist();
});
