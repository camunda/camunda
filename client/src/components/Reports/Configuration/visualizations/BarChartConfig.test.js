import React from 'react';
import {shallow} from 'enzyme';
import BarChartConfig from './BarChartConfig';

const barReport = {
  combined: false,
  data: {visualization: 'bar', view: {property: 'frequency'}}
};

const props = {
  report: {combined: false}
};

const configuration = {
  showInstanceCount: false,
  color: '#1991c8',
  hideRelativeValue: false,
  hideAbsoluteValue: false,
  xLabel: '',
  yLabel: '',
  targetValue: {active: false}
};

it('it should display correct configuration for barchart', () => {
  const node = shallow(<BarChartConfig {...{report: barReport, configuration}} />);
  expect(node).toMatchSnapshot();
});

it('should not display show instance count and color picker for combined reports', () => {
  const node = shallow(
    <BarChartConfig
      {...{
        report: {combined: true, result: {test: {data: {view: {property: 'frequency'}}}}},
        configuration
      }}
    />
  );

  expect(node.find('ShowInstanceCount')).not.toBePresent();
  expect(node.find('ColorPicker')).not.toBePresent();
});
