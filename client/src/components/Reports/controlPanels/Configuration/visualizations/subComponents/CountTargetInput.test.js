/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button, LabeledInput} from 'components';

import CountTargetInput from './CountTargetInput';

const validProps = {
  baseline: 10,
  target: 200,
  disabled: false,
};

it('should display the current target values', () => {
  const node = shallow(<CountTargetInput {...validProps} />);

  expect(node.find(LabeledInput).first()).toHaveValue(200);
  expect(node.find({label: 'Baseline'})).toHaveValue(10);
});

it('should update target values', () => {
  const spy = jest.fn();
  const node = shallow(<CountTargetInput {...validProps} onChange={spy} />);

  node
    .find(LabeledInput)
    .first()
    .simulate('change', {target: {value: '73'}});

  expect(spy).toHaveBeenCalledWith('target', '73');
});

it('should show an error message if an input field does not have a valid value', async () => {
  const node = shallow(<CountTargetInput {...validProps} baseline={'notAValidValue'} />);

  expect(node.find('Message')).toExist();
});

it('should show an error message if target is below baseline', async () => {
  const node = shallow(<CountTargetInput baseline={50} target={4} />);

  expect(node.find('Message')).toExist();
});

it('should invoke the onChange prop on button click', async () => {
  const spy = jest.fn();
  const node = shallow(<CountTargetInput {...validProps} onChange={spy} />);

  node.find(Button).first().simulate('click');

  expect(spy).toHaveBeenCalledWith('isBelow', false);
});
