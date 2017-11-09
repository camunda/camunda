import React from 'react';
import {mount} from 'enzyme';

import Chart from './Chart';
import ChartRenderer from 'chart.js';

jest.mock('chart.js', () => jest.fn());

it('should construct a Chart', () => {
  mount(<Chart data={{foo: 123}} />);

  expect(ChartRenderer).toHaveBeenCalled();
});

it('should display an error message for a non-object result (single number)', () => {
  const node = mount(<Chart data={7} />);

  expect(node).toIncludeText('Cannot display data');
});

it('should display an error message if no data is provided', () => {
  const node = mount(<Chart />);

  expect(node).toIncludeText('Cannot display data');
});

it('should use the provided type for the ChartRenderer', () => {
  ChartRenderer.mockReset();

  mount(<Chart data={{foo: 123}} type='visualization_type' />);

  expect(ChartRenderer.mock.calls[0][1].type).toBe('visualization_type');
});
