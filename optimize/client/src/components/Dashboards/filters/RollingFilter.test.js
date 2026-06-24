/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {NumberInput} from '@carbon/react';

import {numberParser} from 'services';

import RollingFilter from './RollingFilter';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  numberParser: {isPositiveInt: jest.fn().mockReturnValue(true)},
}));

jest.mock('debounce', () => jest.fn((fn) => fn));

const props = {
  filter: {
    start: {value: 2, unit: 'days'},
  },
};

it('should load initial values correctly', () => {
  const node = shallow(<RollingFilter {...props} />);

  expect(node.find(NumberInput).prop('value')).toBe(2);
  expect(node.find('Select').prop('value')).toBe('days');
});

it('should invoke onChange when update the value input or the unit selection', () => {
  const spy = jest.fn();
  const node = shallow(<RollingFilter {...props} onChange={spy} />);

  node.find(NumberInput).simulate('change', undefined, {value: '3'});
  expect(spy).toHaveBeenCalledWith({value: 3});

  spy.mockClear();

  node.find('Select').simulate('change', 'months');
  expect(spy).toHaveBeenCalledWith({unit: 'months'});
});

it('should prevent the user from typing non integer values into the input except for empty values', () => {
  numberParser.isPositiveInt.mockReturnValue(false);
  const spy = jest.fn();
  const node = shallow(<RollingFilter {...props} onChange={spy} />);

  node.find(NumberInput).simulate('change', undefined, {value: 'a'});
  expect(spy).not.toHaveBeenCalled();

  node.find(NumberInput).simulate('change', undefined, {value: ''});
  expect(spy).toHaveBeenCalled();
});
