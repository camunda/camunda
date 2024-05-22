/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import {getOptimizeProfile} from 'config';

import Login from './Login';
import PlatformLogin from './PlatformLogin';

jest.mock('config', () => ({
  getOptimizeProfile: jest.fn(),
}));

const props = {
  onLogin: jest.fn(),
};

let originalWindowLocation = window.location;

beforeEach(() => {
  Object.defineProperty(window, 'location', {
    configurable: true,
    enumerable: true,
    value: new URL(window.location.href),
  });
});

afterEach(() => {
  Object.defineProperty(window, 'location', {
    configurable: true,
    enumerable: true,
    value: originalWindowLocation,
  });
});

it('should display the Optimize default login page in platform mode', async () => {
  (getOptimizeProfile as jest.Mock).mockReturnValueOnce('platform');
  const node = shallow(<Login {...props} />);

  await runAllEffects();

  expect(node.find(PlatformLogin)).toExist();
});

it('should do a whole page refresh in C8 mode to reinitialize login flow', async () => {
  (getOptimizeProfile as jest.Mock).mockReturnValueOnce('cloud');

  window.location.reload = jest.fn();
  const node = shallow(<Login {...props} />);

  await runAllEffects();

  expect(node.find(PlatformLogin)).not.toExist();
  expect(window.location.reload).toHaveBeenCalled();
});
