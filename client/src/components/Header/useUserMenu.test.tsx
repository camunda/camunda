/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps} from 'react';
import {runLastEffect} from '__mocks__/react';
import {useHistory} from 'react-router-dom';
import {shallow} from 'enzyme';

import {areSettingsManuallyConfirmed, isLogoutHidden} from 'config';
import {useUser} from 'hooks';

import useUserMenu from './useUserMenu';

jest.mock('config', () => ({
  isLogoutHidden: jest.fn().mockReturnValue(false),
  areSettingsManuallyConfirmed: jest.fn().mockReturnValue(true),
}));

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useHistory: jest.fn(),
}));

jest.mock('hooks', () => ({
  useErrorHandling: jest
    .fn()
    .mockReturnValue({mightFail: jest.fn().mockImplementation((data, cb) => cb(data))}),
  useUser: jest
    .fn()
    .mockReturnValue({user: {authorizations: ['telemetry_administration'], name: 'userName'}}),
}));

const props = {
  setTelemetrySettingsOpen: jest.fn(),
};

const UserMenu = (props: {setTelemetrySettingsOpen: (open: boolean) => true}) => {
  const menu = useUserMenu(props);
  return <div {...(menu as ComponentProps<'div'>)} />;
};

it('should go to temporary logout route on logout', () => {
  const spy = {replace: jest.fn()};
  (useHistory as jest.Mock).mockReturnValue(spy);
  const node = shallow(<UserMenu {...props} />);

  const logout = node.props().bottomElements[0];
  logout.onClick();
  expect(spy.replace).toHaveBeenCalledWith('/logout');
});

it('should hide Telemetry settings entry if the user is not authorized', () => {
  const spy = jest.fn();
  (areSettingsManuallyConfirmed as jest.Mock).mockReturnValueOnce(false);
  (useUser as jest.Mock).mockReturnValueOnce({user: {name: 'Johnny Depp', authorizations: []}});
  shallow(<UserMenu {...props} setTelemetrySettingsOpen={spy} />);

  runLastEffect();

  expect(spy).not.toHaveBeenCalled();
});

it('should hide logout button if specified by the ui config', () => {
  (isLogoutHidden as jest.Mock).mockReturnValueOnce(true);
  const node = shallow(<UserMenu {...props} />);

  runLastEffect();

  const logout = node.props().bottomElements[0];
  expect(logout).not.toBeDefined();
});

it('should automatically open the telemetry settings modal if the settings are not confirmed', () => {
  (areSettingsManuallyConfirmed as jest.Mock).mockReturnValueOnce(false);
  const spy = jest.fn();
  shallow(<UserMenu {...props} setTelemetrySettingsOpen={spy} />);

  runLastEffect();

  expect(spy).toHaveBeenCalledWith(true);
});

it('should open telemetry settings modal when clicking the telemetry option', () => {
  const spy = jest.fn();
  const node = shallow(<UserMenu {...props} setTelemetrySettingsOpen={spy} />);

  const telemetry = node.props().elements.find(({key}: {key: string}) => key === 'telemetry');
  telemetry.onClick();

  expect(spy).toHaveBeenCalledWith(true);
});
