import React from 'react';
import {shallow} from 'enzyme';
import LineChartConfig from './LineChartConfig';

const lineReport = {
  combined: false,
  data: {visualization: 'line', view: {property: 'frequency'}}
};

const configuration = {
  showInstanceCount: false,
  color: '#1991c8',
  pointMarkers: true,
  hideRelativeValue: false,
  hideAbsoluteValue: false,
  xLabel: '',
  yLabel: '',
  targetValue: {active: false}
};

it('it should display correct configuration for linechart', () => {
  const node = shallow(<LineChartConfig {...{report: lineReport, configuration}} />);
  expect(node).toMatchSnapshot();
});
