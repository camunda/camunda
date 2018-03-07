import React from 'react';
import {mount} from 'enzyme';

import Chart from './Chart';
import ChartRenderer from 'chart.js';

jest.mock('chart.js', () =>
  jest.fn(() => {
    return {
      destroy: jest.fn()
    };
  })
);

it('should construct a Chart', () => {
  mount(<Chart data={{foo: 123}} />);

  expect(ChartRenderer).toHaveBeenCalled();
});

it('should display an error message for a non-object result (single number)', () => {
  const node = mount(<Chart data={7} errorMessage="Error" />);

  expect(node).toIncludeText('Error');
});

it('should display an error message if no data is provided', () => {
  const node = mount(<Chart errorMessage="Error" />);

  expect(node).toIncludeText('Error');
});

it('should use the provided type for the ChartRenderer', () => {
  ChartRenderer.mockClear();

  mount(<Chart data={{foo: 123}} type="visualization_type" />);

  expect(ChartRenderer.mock.calls[0][1].type).toBe('visualization_type');
});

it('should change type for the ChartRenderer if props were updated', () => {
  ChartRenderer.mockClear();

  const chart = mount(<Chart data={{foo: 123}} type="visualization_type" />);
  chart.setProps({type: 'new_visualization_type'});

  expect(ChartRenderer.mock.calls[1][1].type).toBe('new_visualization_type');
});

it('should not display an error message if data is valid', () => {
  const node = mount(<Chart data={{foo: 123}} errorMessage="Error" />);

  expect(node).not.toIncludeText('Error');
});

it('should destroy chart if no data is provided', () => {
  const node = mount(<Chart errorMessage="Error" />);

  expect(node.chart).toBe(undefined);
});

it('should render chart even if type does not change', () => {
  ChartRenderer.mockClear();

  const chart = mount(<Chart data={{foo: 123}} type="visualization_type" />);
  chart.setProps({type: 'visualization_type'});

  expect(ChartRenderer.mock.calls[1][1].type).toBe('visualization_type');
});

it('should display an error message if there was data and the second time no data is provided', () => {
  const node = mount(<Chart data={{foo: 123}} errorMessage="Error" />);

  node.setProps({data: null});

  expect(node).toIncludeText('Error');
});
