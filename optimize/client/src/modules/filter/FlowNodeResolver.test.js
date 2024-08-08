/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
