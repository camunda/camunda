/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import AutoRefreshSelect from './AutoRefreshSelect';
import {Select} from 'components';

jest.useFakeTimers();

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

  node.find(Select).simulate('change', newIternvalMins);

  expect(setInterval).toHaveBeenCalledWith(refreshFunc, newIternvalMins * 60 * 1000);
  expect(spy).toHaveBeenCalledWith(newIternvalMins * 60 * 1000);
});

it('should select a new refresh interval', () => {
  const refreshFunc = jest.fn();
  const spy = jest.fn();
  const node = shallow(<AutoRefreshSelect onChange={spy} onRefresh={refreshFunc} />);
  const newIternvalMins = 5;

  node.find(Select).simulate('change', newIternvalMins);

  expect(setInterval).toHaveBeenCalledWith(refreshFunc, newIternvalMins * 60 * 1000);
  expect(spy).toHaveBeenCalledWith(newIternvalMins * 60 * 1000);
});

it('should turn off refresh interval', () => {
  const spy = jest.fn();
  const node = shallow(<AutoRefreshSelect onChange={spy} onRefresh={jest.fn()} />);

  node.find(Select).simulate('change', 'off');

  expect(clearInterval).toHaveBeenCalled();
  expect(setInterval).not.toHaveBeenCalled();
  expect(spy).toHaveBeenCalledWith(null);
});
