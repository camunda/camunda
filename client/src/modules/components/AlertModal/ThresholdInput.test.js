/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ThresholdInput from './ThresholdInput';

import {Input, Select} from 'components';

it('should contain a single input field if the type is not duration', () => {
  const node = shallow(<ThresholdInput type="number" value="123" />);

  expect(node.find(Input)).toExist();
  expect(node.find(Select)).not.toExist();
});

it('should contain a input and a select field if the type is duration', () => {
  const node = shallow(<ThresholdInput type="duration" value={{value: '123', unit: 'minutes'}} />);

  expect(node.find(Input)).toExist();
  expect(node.find(Select)).toExist();
});

it('should call the change handler when changing the value', () => {
  const spy = jest.fn();
  const node = shallow(
    <ThresholdInput onChange={spy} type="duration" value={{value: '123', unit: 'minutes'}} />
  );

  node.find(Input).simulate('change', {target: {value: '1234'}});

  expect(spy).toHaveBeenCalledWith({value: '1234', unit: 'minutes'});
});
