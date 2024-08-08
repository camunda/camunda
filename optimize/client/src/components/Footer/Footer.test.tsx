/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import Footer from './Footer';
import {getOptimizeVersion, getOptimizeProfile} from 'config';

import ConnectionStatus from './ConnectionStatus';

jest.mock('config', () => {
  return {
    getOptimizeVersion: jest.fn(),
    getOptimizeProfile: jest.fn().mockReturnValue('platform'),
  };
});

it('includes the version number retrieved from back-end', async () => {
  const version = 'alpha';
  (getOptimizeVersion as jest.Mock).mockReturnValue(version);

  const node = shallow(<Footer />);

  runLastEffect();
  await flushPromises();

  expect(node.find('.colophon')).toIncludeText(version);
});

it('should not show status in cloud environment', async () => {
  (getOptimizeProfile as jest.Mock).mockReturnValueOnce('cloud');
  const node = shallow(<Footer />);

  runLastEffect();
  await flushPromises();

  expect(node.find(ConnectionStatus)).not.toExist();
});
