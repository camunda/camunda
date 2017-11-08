import React from 'react';
import {mount} from 'enzyme';

import BarChart from './BarChart';
import Chart from 'chart.js';

jest.mock('chart.js', () => jest.fn());

it('should construct a Chart', () => {
  mount(<BarChart data={{foo: 123}} />);

  expect(Chart).toHaveBeenCalled();
});

it('should display an error message for a non-object result (single number)', () => {
  const node = mount(<BarChart data={7} />);

  expect(node).toIncludeText('Cannot display data');
});

it('should display an error message if no data is provided', () => {
  const node = mount(<BarChart />);

  expect(node).toIncludeText('Cannot display data');
});
