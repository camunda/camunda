/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';
import {C3Navigation} from '@camunda/camunda-composite-components';

import {track} from 'tracking';
import {getOptimizeProfile, isEnterpriseMode} from 'config';

import {isEventBasedProcessEnabled} from './service';
import {Header} from './Header';
import WhatsNewModal from './WhatsNewModal';

jest.mock('config', () => ({
  getHeader: jest.fn().mockReturnValue({
    textColor: 'light',
    backgroundColor: '#000',
    logo: 'url',
  }),
  getOptimizeProfile: jest.fn().mockReturnValue('platform'),
  isEnterpriseMode: jest.fn().mockReturnValue(true),
  getWebappLinks: jest.fn().mockReturnValue({
    zeebe: 'http://zeebe.com',
    operate: 'http://operate.com',
    optimize: 'http://optimize.com',
  }),
  getOnboardingConfig: jest.fn().mockReturnValue({orgId: 'orgId'}),
  getNotificationsUrl: jest.fn().mockReturnValue('notificationsUrl'),
}));

jest.mock('./service', () => ({
  isEventBasedProcessEnabled: jest.fn().mockReturnValue(true),
  getUserToken: jest.fn().mockReturnValue('userToken'),
}));

jest.mock('react-router', () => ({
  ...jest.requireActual('react-router'),
  useLocation: jest.fn().mockImplementation(() => {
    return {pathname: '/testroute'};
  }),
}));

jest.mock('tracking', () => ({track: jest.fn()}));

function getNavItem(node, key) {
  const navItems = node.find(C3Navigation).prop('navbar').elements;
  return navItems.find((item) => item.key === key);
}

const props = {
  mightFail: async (data, cb) => cb(await data),
  location: {pathname: '/'},
  history: {push: jest.fn()},
};

it('should check if the event based process feature is enabled', async () => {
  isEventBasedProcessEnabled.mockClear();
  shallow(<Header {...props} />);

  await runLastEffect();

  expect(isEventBasedProcessEnabled).toHaveBeenCalled();
});

it('should show and hide the event based process nav item depending on authorization', async () => {
  isEventBasedProcessEnabled.mockReturnValueOnce(true);
  const enabled = shallow(<Header {...props} />);

  await runLastEffect();
  await enabled.update();

  expect(getNavItem(enabled, 'events')).toBeDefined();

  isEventBasedProcessEnabled.mockReturnValueOnce(false);
  const disabled = shallow(<Header {...props} />);

  await runLastEffect();
  await disabled.update();

  expect(getNavItem(disabled, 'events')).not.toBeDefined();
});

it('should hide event based process nav item in cloud environment', async () => {
  isEventBasedProcessEnabled.mockReturnValueOnce(true);
  getOptimizeProfile.mockReturnValueOnce('cloud');
  const node = shallow(<Header {...props} />);

  await runLastEffect();
  await node.update();

  expect(getNavItem(node, 'events')).not.toBeDefined();
});

it('should show license warning if enterpriseMode is not set', async () => {
  isEnterpriseMode.mockReturnValueOnce(false);
  const node = shallow(<Header {...props} />);

  await runLastEffect();
  await node.update();

  const tags = node.find(C3Navigation).prop('navbar').tags;
  expect(tags.find((tag) => tag.key === 'licenseWarning')).toBeDefined();
});

it('should open the whatsNewDialog on option click', async () => {
  const node = shallow(<Header {...props} />);

  expect(node.find(WhatsNewModal).prop('open')).toBe(false);

  const sideBarEleemnts = node.find(C3Navigation).prop('infoSideBar').elements;
  sideBarEleemnts.find((el) => el.key === 'whatsNew').onClick();

  expect(node.find(WhatsNewModal).prop('open')).toBe(true);
});

it('should no display navbar and sidebar is noAction prop is specified', () => {
  const node = shallow(<Header noActions />);

  expect(node.find(C3Navigation).prop('navbar')).toEqual({elements: []});
  expect(node.find(C3Navigation).prop('infoSideBar')).not.toBeDefined();
  expect(node.find(C3Navigation).prop('userSideBar')).not.toBeDefined();
});

it('should render sidebar links', async () => {
  const node = shallow(<Header {...props} />);

  runLastEffect();
  await flushPromises();

  expect(node.find(C3Navigation).prop('appBar').elements).toEqual([
    {
      active: false,
      href: 'http://zeebe.com',
      key: 'zeebe',
      label: 'Zeebe',
      ariaLabel: 'Zeebe',
      routeProps: undefined,
    },
    {
      active: false,
      href: 'http://operate.com',
      key: 'operate',
      label: 'Operate',
      ariaLabel: 'Operate',
      routeProps: undefined,
    },
    {
      active: true,
      href: 'http://optimize.com',
      key: 'optimize',
      label: 'Optimize',
      ariaLabel: 'Optimize',
      routeProps: {
        to: '/',
      },
    },
  ]);
});

it('should track app clicks from the app switcher', async () => {
  const node = shallow(<Header {...props} />);

  node.find(C3Navigation).prop('appBar').elementClicked('modeler');

  expect(track).toHaveBeenCalledWith('modeler:open');
});

it('should display the notifications component in cloud mode', async () => {
  getOptimizeProfile.mockReturnValueOnce('cloud');

  const node = shallow(<Header {...props} />);

  await runLastEffect();
  await node.update();

  expect(node.find('C3UserConfigurationProvider').props()).toEqual({
    endpoints: {notifications: 'notificationsUrl'},
    activeOrganizationId: 'orgId',
    userToken: 'userToken',
    getNewUserToken: expect.any(Function),
    children: expect.any(Array),
  });
  expect(node.find(C3Navigation).prop('notificationSideBar')).toEqual({
    ariaLabel: 'Notifications',
    isOpen: false,
    key: 'notifications',
  });
});
