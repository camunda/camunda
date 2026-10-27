/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import {useUiConfig} from 'hooks';

import HeaderV2 from '.';

const defaultUiConfig = {
  optimizeProfile: 'ccsm',
  onboarding: {
    orgId: 'orgId',
    clusterId: 'clusterId',
  },
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

jest.mock('../service', () => ({
  getUserToken: jest.fn().mockReturnValue('userToken'),
}));

beforeEach(() => {
  (useUiConfig as jest.Mock).mockReturnValue(defaultUiConfig);
});

it('should not provide cluster configuration context outside of cloud', async () => {
  const node = shallow(<HeaderV2 />);

  await runLastEffect();
  await node.update();

  expect(node.find('NavbarWrapper').prop('isCloud')).toBe(false);
});

it('should provide cluster configuration context in cloud mode', async () => {
  (useUiConfig as jest.Mock).mockReturnValue({...defaultUiConfig, optimizeProfile: 'cloud'});

  const node = shallow(<HeaderV2 />);

  await runLastEffect();
  await node.update();

  expect(node.find('NavbarWrapper').props()).toMatchObject({
    isCloud: true,
    organizationId: 'orgId',
    clusterId: 'clusterId',
    userToken: 'userToken',
    getNewUserToken: expect.any(Function),
  });
});
