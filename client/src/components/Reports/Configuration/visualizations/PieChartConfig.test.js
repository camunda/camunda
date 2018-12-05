import React from 'react';
import {shallow} from 'enzyme';
import PieChartConfig from './PieChartConfig';

const pieReport = {
  combined: false,
  data: {visualization: 'pie', view: {property: 'frequency'}}
};

const configuration = {
  hideRelativeValue: false,
  hideAbsoluteValue: false
};

it('it should display correct configuration for piechart', () => {
  const node = shallow(<PieChartConfig {...{report: pieReport, configuration}} />);
  expect(node).toMatchSnapshot();
});
