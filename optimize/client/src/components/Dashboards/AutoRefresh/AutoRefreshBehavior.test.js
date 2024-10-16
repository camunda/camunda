/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import AutoRefreshBehavior from './AutoRefreshBehavior';

jest.useFakeTimers();
jest.spyOn(global, 'setInterval');
jest.spyOn(global, 'clearInterval');

const ReportSpy = jest.fn();
const interval = 600;

it('should register an interval with the specified interval duration and function', () => {
  shallow(<AutoRefreshBehavior loadTileData={ReportSpy} interval={interval} />);

  expect(setInterval).toHaveBeenCalledTimes(1);
});

it('should clear the interval when component is unmounted', () => {
  const node = shallow(<AutoRefreshBehavior loadTileData={ReportSpy} interval={interval} />);
  node.unmount();

  expect(clearInterval).toHaveBeenCalledTimes(1);
});

it('should update the interval when the interval property changes', () => {
  const node = shallow(<AutoRefreshBehavior loadTileData={ReportSpy} interval={interval} />);

  clearInterval.mockClear();
  node.setProps({interval: 1000});

  expect(clearInterval).toHaveBeenCalledTimes(1);
  expect(setInterval).toHaveBeenCalled();
});

it('should invoke the loadTileData function after the interval duration ends', async () => {
  shallow(<AutoRefreshBehavior loadTileData={ReportSpy} interval={interval} />);
  jest.advanceTimersByTime(700);
  expect(ReportSpy).toHaveBeenCalled();
});
