/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {evaluateGoals} from './service';
import {ResultPreview} from './ResultPreview';

jest.mock('debounce', () => jest.fn((fn) => fn));
jest.mock('./service', () => ({evaluateGoals: jest.fn()}));

const props = {
  processDefinitionKey: 'aDefKey',
  goals: [],
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('invoke evaluate goals on load or goal changes', () => {
  const node = shallow(<ResultPreview {...props} />);

  runLastEffect();

  expect(evaluateGoals).toHaveBeenCalledWith(props.processDefinitionKey, props.goals);

  evaluateGoals.mockClear();

  node.setProps({
    goals: [
      {
        type: 'targetDuration',
        percentile: '25',
        value: '1',
        unit: 'weeks',
      },
    ],
  });

  runLastEffect();

  expect(evaluateGoals).toHaveBeenCalled();
});
