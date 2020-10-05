/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {isLogoutHidden, areSettingsManuallyConfirmed} from 'config';

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

  node.find('.UserMenu [title="Logout"]').simulate('click');
  expect(props.history.push).toHaveBeenCalledWith('/logout');
});

it('should hide Telemetry settings entry if the user is not authorized', async () => {
  const node = shallow(<UserMenu {...props} user={{name: 'Johnny Depp', authorizations: []}} />);

  expect(node.find('.UserMenu [title="Telemetry Settings"]')).not.toExist();
});

it('should hide logout button if specified by the ui config', async () => {
  isLogoutHidden.mockReturnValueOnce(true);
  const node = shallow(<UserMenu {...props} />);
  runLastEffect();

  expect(node.find('.UserMenu [title="Logout"]')).not.toExist();
});

it('should automatically open the telemetry settings modal if the settings are not confirmed', async () => {
  areSettingsManuallyConfirmed.mockReturnValueOnce(false);
  const node = shallow(<UserMenu {...props} />);

  await runLastEffect();

  expect(node.find(TelemetrySettings).prop('open')).toBe(true);
});

it('should open telemetry settings modal when clicking the telemetry option', async () => {
  const node = shallow(<UserMenu {...props} />);

  node.find('.UserMenu [title="Telemetry Settings"]').simulate('click');

  expect(node.find(TelemetrySettings).prop('open')).toBe(true);
});
