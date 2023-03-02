/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {getOptimizeProfile} from 'config';

import Login from './Login';
import PlatformLogin from './PlatformLogin';

jest.mock('config', () => ({
  getOptimizeProfile: jest.fn(),
}));

it('should display the Optimize default login page in platform mode', async () => {
  getOptimizeProfile.mockReturnValueOnce('platform');
  const node = shallow(<Login />);

  await runAllEffects();

  expect(node.find(PlatformLogin)).toExist();
});

it('should do a whole page refresh in C8 mode to reinitialize login flow', async () => {
  getOptimizeProfile.mockReturnValueOnce('cloud');

  delete window.location;
  window.location = {reload: jest.fn()};
  const node = shallow(<Login />);

  await runAllEffects();

  expect(node.find(PlatformLogin)).not.toExist();
  expect(window.location.reload).toHaveBeenCalled();
});
