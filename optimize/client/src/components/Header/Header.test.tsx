/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';
import {C3Navigation, C3NavigationProps} from '@camunda/camunda-composite-components';

import {track} from 'tracking';
import {useUiConfig} from 'hooks';

import Header from './Header';

const defaultUiConfig = {
  optimizeProfile: 'ccsm',
  enterpriseMode: true,
  webappsLinks: {
    optimize: 'http://optimize.com',
    console: 'http://console.com',
    operate: 'http://operate.com',
  },
  onboarding: {
    orgId: 'orgId',
    clusterId: 'clusterId',
  },
  notificationsUrl: 'notificationsUrl',
  validLicense: true,
  licenseType: 'production',
  expiresAt: null,
  commercial: true,
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
  getUserToken: jest.fn().mockReturnValue('userToken'),
}));

jest.mock('react-router', () => ({
  ...jest.requireActual('react-router'),
  useLocation: jest.fn().mockImplementation(() => {
    return {pathname: '/testroute'};
  }),
}));

jest.mock('tracking', () => ({track: jest.fn()}));

beforeEach(() => {
  (useUiConfig as jest.Mock).mockReturnValue(defaultUiConfig);
});

it('should show license tag if not in saas', async () => {
  const node = shallow(<Header />);

  await runLastEffect();
  await node.update();

  expect(
    node.find(C3Navigation).prop<C3NavigationProps['navbar']>('navbar').licenseTag
  ).toMatchObject({
    isProductionLicense: true,
    show: true,
  });
});

it('should hide license tag in saas', async () => {
  (useUiConfig as jest.Mock).mockReturnValue({...defaultUiConfig, licenseType: 'saas'});
  const node = shallow(<Header />);

  await runLastEffect();
  await node.update();

  expect(
    node.find(C3Navigation).prop<C3NavigationProps['navbar']>('navbar').licenseTag
  ).toMatchObject({
    isProductionLicense: true,
    show: false,
  });
});

it('should pass expiration date to the C3 navbar', async () => {
  const todayDate = new Date().toISOString();
  (useUiConfig as jest.Mock).mockReturnValue({...defaultUiConfig, expiresAt: todayDate});
  const node = shallow(<Header />);

  await runLastEffect();
  await node.update();

  expect(
    node.find(C3Navigation).prop<C3NavigationProps['navbar']>('navbar').licenseTag
  ).toMatchObject({
    isProductionLicense: true,
    show: true,
    expiresAt: todayDate,
    isCommercial: true,
  });
});

it('should pass an undefined expiration to the c3 navbar if the expiration value is null', async () => {
  const node = shallow(<Header />);

  await runLastEffect();
  await node.update();

  expect(
    node.find(C3Navigation).prop<C3NavigationProps['navbar']>('navbar').licenseTag
  ).toMatchObject({
    isProductionLicense: true,
    show: true,
    expiresAt: undefined,
    isCommercial: true,
  });
});

it('should pass the commercial property to the c3 navbar', async () => {
  (useUiConfig as jest.Mock).mockReturnValue({...defaultUiConfig, commercial: false});
  const node = shallow(<Header />);

  await runLastEffect();
  await node.update();

  expect(
    node.find(C3Navigation).prop<C3NavigationProps['navbar']>('navbar').licenseTag
  ).toMatchObject({
    isProductionLicense: true,
    show: true,
    expiresAt: undefined,
    isCommercial: false,
  });
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
    organizationId: 'orgId',
    clusterId: 'clusterId',
    userToken: 'userToken',
    getNewUserToken: expect.any(Function),
  });
  expect(node.find(C3Navigation).prop('notificationSideBar')).toEqual({
    ariaLabel: 'Notifications',
    isOpen: false,
  });
});
