/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import AutoRefreshSelect from './AutoRefreshSelect';

jest.useFakeTimers();
jest.spyOn(global, 'setInterval');
jest.spyOn(global, 'clearInterval');

const interval = 600;

beforeEach(() => {
  setInterval.mockClear();
  clearInterval.mockClear();
});

it('should set an interval on the refresh function when passing a refresh rate and an onRefresh handler', () => {
  const refreshFunc = jest.fn();
  shallow(<AutoRefreshSelect refreshRateMs={interval} onRefresh={refreshFunc} />);

  expect(setInterval).toHaveBeenCalledWith(refreshFunc, interval);

  jest.advanceTimersByTime(interval);
  expect(refreshFunc).toHaveBeenCalled();
});

it('should select a new refresh interval', () => {
  const refreshFunc = jest.fn();
  const spy = jest.fn();
  const node = shallow(<AutoRefreshSelect onChange={spy} onRefresh={refreshFunc} />);
  const newIternvalMins = 5;

  node.find({value: '5'}).simulate('change');

  expect(setInterval).toHaveBeenCalledWith(refreshFunc, newIternvalMins * 60 * 1000);
  expect(spy).toHaveBeenCalledWith(newIternvalMins * 60 * 1000);
});

it('should turn off refresh interval', () => {
  const spy = jest.fn();
  const node = shallow(<AutoRefreshSelect onChange={spy} onRefresh={jest.fn()} />);

  node.find({value: 'off'}).simulate('change');

  expect(clearInterval).toHaveBeenCalled();
  expect(setInterval).not.toHaveBeenCalled();
  expect(spy).toHaveBeenCalledWith(null);
});
