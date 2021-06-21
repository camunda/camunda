/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Footer from './Footer';
import {getOptimizeVersion, isOptimizeCloudEnvironment} from 'config';

import ConnectionStatus from './ConnectionStatus';

jest.mock('config', () => {
  return {
    getOptimizeVersion: jest.fn(),
    isOptimizeCloudEnvironment: jest.fn().mockReturnValue(false),
  };
});

it('includes the version number retrieved from back-end', async () => {
  const version = 'alpha';
  getOptimizeVersion.mockReturnValue(version);

  const node = shallow(<Footer />);

  await flushPromises();

  expect(node.find('.colophon')).toIncludeText(version);
});

it('should not show status in cloud environment', async () => {
  isOptimizeCloudEnvironment.mockReturnValueOnce(true);
  const node = shallow(<Footer />);

  node.setState({
    loaded: true,
  });

  await flushPromises();

  expect(node.find(ConnectionStatus)).not.toExist();
});
