/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {isLogoutHidden, areSettingsManuallyConfirmed} from 'config';
import {Dropdown} from 'components';

import {TelemetrySettings} from './TelemetrySettings';
import {UserMenu} from './UserMenu';

jest.mock('config', () => ({
  isLogoutHidden: jest.fn().mockReturnValue(false),
  areSettingsManuallyConfirmed: jest.fn().mockReturnValue(true),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  user: {authorizations: ['telemetry_administration'], name: 'userName'},
  history: {push: jest.fn()},
};

it('matches the snapshot', async () => {
  const node = shallow(<UserMenu {...props} />);

  expect(node).toMatchSnapshot();
});

it('should go to temporary logout route on logout', () => {
  const node = shallow(<UserMenu {...props} />);

  node.find(Dropdown.Option).at(1).simulate('click');
  expect(props.history.push).toHaveBeenCalledWith('/logout');
});

it('should hide Telemetry settings entry if the user is not authorized', async () => {
  const node = shallow(<UserMenu {...props} user={{name: 'Johnny Depp', authorizations: []}} />);

  expect(node.find(Dropdown.Option)).toHaveLength(1);
});

it('should hide logout button if specified by the ui config', async () => {
  isLogoutHidden.mockReturnValueOnce(true);
  const node = shallow(<UserMenu {...props} />);
  runLastEffect();

  expect(node.find(Dropdown.Option)).toHaveLength(1);
});

it('should automatically open the telemetry settings modal if the settings are not confirmed', async () => {
  areSettingsManuallyConfirmed.mockReturnValueOnce(false);
  const node = shallow(<UserMenu {...props} />);

  await runLastEffect();

  expect(node.find(TelemetrySettings).prop('open')).toBe(true);
});

it('should open telemetry settings modal when clicking the telemetry option', async () => {
  const node = shallow(<UserMenu {...props} />);

  node.find(Dropdown.Option).at(0).simulate('click');

  expect(node.find(TelemetrySettings).prop('open')).toBe(true);
});
