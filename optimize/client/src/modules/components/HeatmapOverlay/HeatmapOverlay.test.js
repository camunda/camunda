/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import HeatmapOverlay from './HeatmapOverlay';
import {getHeatmap} from './service';

jest.mock('./service', () => {
  return {
    getHeatmap: jest.fn(),
  };
});

jest.mock('./Tooltip', () => 'foo');

jest.spyOn(document.body, 'querySelector').mockReturnValue({
  classList: {
    add: jest.fn(),
  },
});

const appendSpy = jest.fn();
const removeSpy = jest.fn();
const eventSpy = jest.fn();

const viewer = {
  get: () => {
    return {
      _viewport: {appendChild: appendSpy, removeChild: removeSpy},
      on: eventSpy,
    };
  },
};
const data = 'some heatmap data';

it('create get a heatmap', () => {
  shallow(<HeatmapOverlay viewer={viewer} data={data} />);

  expect(getHeatmap).toHaveBeenCalledWith(viewer, data, undefined);
});

it('append the heatmap to the viewer viewport', () => {
  const heatmap = {};
  getHeatmap.mockReturnValueOnce(heatmap);

  shallow(<HeatmapOverlay viewer={viewer} data={data} />);

  expect(appendSpy).toHaveBeenCalledWith(heatmap);
});

it('should update heatmap if data changes', () => {
  appendSpy.mockReset();

  const heatmap = {};
  const anotherHeatmap = {something: 'something'};
  getHeatmap.mockReturnValueOnce(heatmap).mockReturnValueOnce(anotherHeatmap);

  const node = shallow(<HeatmapOverlay viewer={viewer} data={data} />);
  node.setState({data: 'some other heatmap data'});

  expect(removeSpy).toHaveBeenCalledWith(heatmap);
  expect(appendSpy).toHaveBeenCalledTimes(2);
  expect(appendSpy).toHaveBeenLastCalledWith(anotherHeatmap);
});

it('should attach onClick if passed', () => {
  const spy = jest.fn();
  shallow(<HeatmapOverlay viewer={viewer} data={data} onNodeClick={spy} />);
  expect(eventSpy).toHaveBeenCalledWith('element.click', spy);
});
