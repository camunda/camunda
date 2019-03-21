import React from 'react';
import {shallow} from 'enzyme';
import PieChartConfig from './PieChartConfig';

const configuration = {
  alwaysShowAbsolute: true,
  alwaysShowRelative: false
};

const pieReport = {
  combined: false,
  data: {visualization: 'pie', view: {property: 'frequency'}, configuration}
};

it('it should display correct configuration for piechart', () => {
  const node = shallow(<PieChartConfig report={pieReport} />);
  expect(node).toMatchSnapshot();
});
