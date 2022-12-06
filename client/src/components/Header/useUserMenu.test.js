/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {useHistory} from 'react-router-dom';
import {shallow} from 'enzyme';

import {areSettingsManuallyConfirmed, isLogoutHidden} from 'config';

import useUserMenu from './useUserMenu';

jest.mock('config', () => ({
  isLogoutHidden: jest.fn().mockReturnValue(false),
  areSettingsManuallyConfirmed: jest.fn().mockReturnValue(true),
}));

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useHistory: jest.fn(),
}));

const props = {
  user: {authorizations: ['telemetry_administration'], name: 'userName'},
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  setTelemetrySettingsOpen: jest.fn(),
};

const UserMenu = (props) => {
  const menu = useUserMenu(props);
  return <div {...menu} />;
};

it('should go to temporary logout route on logout', () => {
  const spy = {replace: jest.fn()};
  useHistory.mockReturnValue(spy);
  const node = shallow(<UserMenu />);

  const logout = node.props().bottomElements[0];
  logout.onClick();
  expect(spy.replace).toHaveBeenCalledWith('/logout');
});

it('should hide Telemetry settings entry if the user is not authorized', () => {
  const spy = jest.fn();
  areSettingsManuallyConfirmed.mockReturnValueOnce(false);
  shallow(
    <UserMenu
      {...props}
      user={{name: 'Johnny Depp', authorizations: []}}
      setTelemetrySettingsOpen={spy}
    />
  );

  runLastEffect();

  expect(spy).not.toHaveBeenCalled();
});

it('should hide logout button if specified by the ui config', () => {
  isLogoutHidden.mockReturnValueOnce(true);
  const node = shallow(<UserMenu {...props} />);

  runLastEffect();

  const logout = node.props().bottomElements[0];
  expect(logout).not.toBeDefined();
});

it('should automatically open the telemetry settings modal if the settings are not confirmed', () => {
  areSettingsManuallyConfirmed.mockReturnValueOnce(false);
  const spy = jest.fn();
  shallow(<UserMenu {...props} setTelemetrySettingsOpen={spy} />);

  runLastEffect();

  expect(spy).toHaveBeenCalledWith(true);
});

it('should open telemetry settings modal when clicking the telemetry option', () => {
  const spy = jest.fn();
  const node = shallow(<UserMenu {...props} setTelemetrySettingsOpen={spy} />);

  const telemetry = node.props().elements.find(({key}) => key === 'telemetry');
  telemetry.onClick();

  expect(spy).toHaveBeenCalledWith(true);
});
