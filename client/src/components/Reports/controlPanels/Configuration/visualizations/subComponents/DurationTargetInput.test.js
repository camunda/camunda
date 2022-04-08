/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {LabeledInput} from 'components';

import DurationTargetInput from './DurationTargetInput';

const validProps = {
  baseline: {value: '12', unit: 'weeks'},
  target: {value: '15', unit: 'months'},
  disabled: false,
};

it('should display the current target values', () => {
  const node = shallow(<DurationTargetInput {...validProps} />);

  expect(node.find(LabeledInput).first()).toHaveValue('12');
  expect(node.find('Select').first()).toHaveValue('weeks');
  expect(node.find(LabeledInput).at(1)).toHaveValue('15');
  expect(node.find('Select').at(1)).toHaveValue('months');
});

it('should update target values', () => {
  const spy = jest.fn();
  const node = shallow(<DurationTargetInput {...validProps} onChange={spy} />);

  node
    .find(LabeledInput)
    .at(1)
    .simulate('change', {target: {value: '73'}});

  expect(spy).toHaveBeenCalledWith('target', 'value', '73');
});

it('should show an error message if an input field does not have a valid value', async () => {
  const node = shallow(
    <DurationTargetInput {...validProps} target={{value: 'five', unit: 'seconds'}} />
  );

  expect(node.find('Message')).toExist();
});

it('should show an error message if target is below baseline', async () => {
  const node = shallow(
    <DurationTargetInput
      baseline={{value: 160, unit: 'seconds'}}
      target={{value: 2, unit: 'minutes'}}
    />
  );

  expect(node.find('Message')).toExist();
});
