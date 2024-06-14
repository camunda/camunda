/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runLastEffect} from '__mocks__/react';
import {ShallowWrapper, shallow} from 'enzyme';
import {C3Navigation, C3NavigationProps} from '@camunda/camunda-composite-components';

import {track} from 'tracking';
import {useUiConfig} from 'hooks';

import {isEventBasedProcessEnabled} from './service';
import Header from './Header';

const defaultUiConfig = {
  optimizeProfile: 'platform',
  enterpriseMode: true,
  webappsLinks: {
    optimize: 'http://optimize.com',
    console: 'http://console.com',
    operate: 'http://operate.com',
  },
  onboarding: {orgId: 'orgId'},
  notificationsUrl: 'notificationsUrl',
};

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn(async (data, cb) => cb(await data)),
  })),
  useDocs: jest.fn(() => ({
    getBaseDocsUrl: () => 'docsUrl',
  })),
  useUser: jest.fn(() => ({
    user: undefined,
  })),
  useUiConfig: jest.fn(() => defaultUiConfig),
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

function getNavItem(node: ShallowWrapper, key: string) {
  const navItems = node.find(C3Navigation).prop<C3NavigationProps['navbar']>('navbar').elements;
  return navItems.find((item) => item.key === key);
}

beforeEach(() => {
  jest.clearAllMocks();
});

it('should check if the event-based process feature is enabled', async () => {
  (isEventBasedProcessEnabled as jest.Mock).mockClear();
  shallow(<Header />);

  await runLastEffect();

  expect(isEventBasedProcessEnabled).toHaveBeenCalled();
});

it('should show and hide the event-based process nav item depending on authorization', async () => {
  (isEventBasedProcessEnabled as jest.Mock).mockReturnValueOnce(true);
  const enabled = shallow(<Header />);

  await runLastEffect();
  await enabled.update();

  expect(getNavItem(enabled, 'events')).toBeDefined();

  (isEventBasedProcessEnabled as jest.Mock).mockReturnValueOnce(false);
  const disabled = shallow(<Header />);

  await runLastEffect();
  await disabled.update();

  expect(getNavItem(disabled, 'events')).not.toBeDefined();
});

it('should hide event-based process nav item in cloud environment', async () => {
  (isEventBasedProcessEnabled as jest.Mock).mockReturnValueOnce(true);
  (useUiConfig as jest.Mock).mockReturnValueOnce({...defaultUiConfig, optimizeProfile: 'cloud'});
  const node = shallow(<Header />);

  await runLastEffect();
  await node.update();

  expect(getNavItem(node, 'events')).not.toBeDefined();
});

it('should show license warning if enterpriseMode is not set', async () => {
  (useUiConfig as jest.Mock).mockReturnValue({...defaultUiConfig, enterpriseMode: false});
  const node = shallow(<Header />);

  await runLastEffect();
  await node.update();

  const tags = node.find(C3Navigation).prop<C3NavigationProps['navbar']>('navbar').tags;
  expect(tags?.find((tag) => tag.key === 'licenseWarning')).toBeDefined();
});

it('should not display navbar and sidebar if noAction prop is specified', () => {
  const node = shallow(<Header noActions />);

  expect(node.find(C3Navigation).prop('navbar')).toEqual({elements: []});
  expect(node.find(C3Navigation).prop('infoSideBar')).not.toBeDefined();
  expect(node.find(C3Navigation).prop('userSideBar')).not.toBeDefined();
});

it('should render sidebar links', async () => {
  const node = shallow(<Header />);

  runLastEffect();
  await flushPromises();

  expect(node.find(C3Navigation).prop('appBar').elements).toEqual([
    {
      active: false,
      href: 'http://console.com',
      key: 'console',
      label: 'Console',
      ariaLabel: 'Console',
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
  const node = shallow(<Header />);

  node.find(C3Navigation).prop('appBar').elementClicked('modeler');

  expect(track).toHaveBeenCalledWith('modeler:open');
});

it('should display the notifications component in cloud mode', async () => {
  (useUiConfig as jest.Mock).mockReturnValue({...defaultUiConfig, optimizeProfile: 'cloud'});

  const node = shallow(<Header />);

  await runLastEffect();
  await node.update();

  expect(node.find('NavbarWrapper').props()).toMatchObject({
    isCloud: true,
    notificationsUrl: 'notificationsUrl',
    organizationId: 'orgId',
    userToken: 'userToken',
    getNewUserToken: expect.any(Function),
  });
  expect(node.find(C3Navigation).prop('notificationSideBar')).toEqual({
    ariaLabel: 'Notifications',
    isOpen: false,
  });
});

it('should display a warning if optimize is running in opensearch mode', async () => {
  (useUiConfig as jest.Mock).mockReturnValue({
    ...defaultUiConfig,
    optimizeDatabase: 'opensearch',
  });
  const node = shallow(<Header />);

  await runLastEffect();
  await node.update();

  const tags = node.find(C3Navigation).prop('navbar').tags;

  expect(tags[0].key).toBe('opensearchWarning');
});
