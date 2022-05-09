/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

const appendSpy = jest.fn();
const removeSpy = jest.fn();

const viewer = {
  get: () => {
    return {
      _viewport: {appendChild: appendSpy, removeChild: removeSpy},
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
