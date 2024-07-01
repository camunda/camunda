/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {getFlowNodeNames} from 'services';

import {FlowNodeResolver} from './FlowNodeResolver';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  getFlowNodeNames: jest.fn().mockReturnValue({a: 'FlowNode A'}),
}));

const props = {
  definition: {key: 'key', versions: ['all'], tenantIds: [null]},
  mightFail: (data, fn) => fn(data),
};

it('should load flow node names and call render function with them', () => {
  const spy = jest.fn();
  shallow(<FlowNodeResolver {...props} render={spy} />);

  runLastEffect();

  expect(getFlowNodeNames).toHaveBeenCalledWith('key', 'all', null);
  expect(spy).toHaveBeenCalledWith({a: 'FlowNode A'});
});

it('should call render function with null if flow nodes are not loaded yet', () => {
  const spy = jest.fn();
  shallow(<FlowNodeResolver {...props} render={spy} />);

  expect(spy).toHaveBeenCalledWith(null);
});
