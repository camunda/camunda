/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {getOptimizeVersion} from 'config';

import DocsLink from './DocsLink';

jest.mock('config', () => ({getOptimizeVersion: jest.fn().mockReturnValue('3.0')}));

it('should render correct docs link', async () => {
  const node = shallow(<DocsLink location="testLocation">{(link) => link}</DocsLink>);

  runLastEffect();
  await flushPromises();

  expect(getOptimizeVersion).toHaveBeenCalled();
  expect(node.text()).toBe('https://docs.camunda.org/optimize/3.0/testLocation');
});
