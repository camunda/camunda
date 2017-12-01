import React from 'react';
import {mount} from 'enzyme';

import HeatmapOverlay from './HeatmapOverlay';
import {getHeatmap} from './service';

jest.mock('./service', () => {return {
  getHeatmap: jest.fn()
}});

const spy = jest.fn();

const viewer = {
  get: () => { return {
    _viewport: {appendChild: spy}
  }}
};
const data = 'some heatmap data';

it('create get a heatmap', () => {
  mount(<HeatmapOverlay viewer={viewer} data={data} />);

  expect(getHeatmap).toHaveBeenCalledWith(viewer, data);
});

it('append the heatmap to the viewer viewport', () => {
  const heatmap = {};
  getHeatmap.mockReturnValueOnce(heatmap);

  mount(<HeatmapOverlay viewer={viewer} data={data} />);

  expect(spy).toHaveBeenCalledWith(heatmap);
});
