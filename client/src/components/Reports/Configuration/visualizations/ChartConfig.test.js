import React from 'react';
import {shallow} from 'enzyme';
import ChartConfig from './ChartConfig';

const getReportByVis = visualization => ({data: {visualization, view: {property: 'frequency'}}});

const lineReport = getReportByVis('line');
const barReport = getReportByVis('bar');
const pieReport = getReportByVis('pie');

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
  const node = shallow(<ChartConfig {...{report: lineReport, configuration}} />);
  expect(node).toMatchSnapshot();
});

it('it should display correct configuration for barchart', () => {
  const node = shallow(<ChartConfig {...{report: barReport, configuration}} />);
  expect(node).toMatchSnapshot();
});

it('it should display correct configuration for piechart', () => {
  const node = shallow(<ChartConfig {...{report: pieReport, configuration}} />);
  expect(node).toMatchSnapshot();
});

it('should reset to defaults when the property changes', () => {
  expect(
    ChartConfig.onUpdate(
      {report: {data: {view: {property: 'prev'}}}},
      {report: {data: {view: {property: 'new'}}}}
    )
  ).toEqual({
    ...ChartConfig.defaults,
    targetValue: null
  });
});

it('should reset to defaults when the entity changes', () => {
  expect(
    ChartConfig.onUpdate(
      {report: {data: {view: {entity: 'prev'}}}},
      {report: {data: {view: {entity: 'new'}}}}
    )
  ).toEqual({
    ...ChartConfig.defaults,
    targetValue: null
  });
});

it('should reset to defaults when visualization type changes', () => {
  expect(
    ChartConfig.onUpdate(
      {type: 'prev', report: {data: {view: {entity: 'test'}}}},
      {type: 'new', report: {data: {view: {entity: 'test'}}}}
    )
  ).toEqual({
    ...ChartConfig.defaults,
    targetValue: null
  });
});

it('should not reset to defaults when visualization type changes from line to bar or reverse', () => {
  expect(
    ChartConfig.onUpdate(
      {type: 'bar', report: {data: {view: {entity: 'test'}}}},
      {type: 'line', report: {data: {view: {entity: 'test'}}}}
    )
  ).toEqual(undefined);
});
