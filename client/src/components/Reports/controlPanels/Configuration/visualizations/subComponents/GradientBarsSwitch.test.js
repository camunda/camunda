/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import GradientBarsSwitch from './GradientBarsSwitch';

it('should contain a switch that is checked if gradient bars are shown', () => {
  const node = shallow(<GradientBarsSwitch configuration={{showGradientBars: true}} />);

  expect(node.find('Switch').prop('checked')).toBe(true);

  node.setProps({configuration: {showGradientBars: false}});

  expect(node.find('Switch').prop('checked')).toBe(false);
});

it('should call the onChange method when toggling the switch', () => {
  const spy = jest.fn();

  const node = shallow(
    <GradientBarsSwitch configuration={{showGradientBars: true}} onChange={spy} />
  );

  node.find('Switch').simulate('change', {target: {checked: false}});

  expect(spy).toHaveBeenCalledWith({showGradientBars: {$set: false}});
});
