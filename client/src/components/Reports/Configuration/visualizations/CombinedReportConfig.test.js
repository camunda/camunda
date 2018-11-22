import React from 'react';
import {shallow} from 'enzyme';
import CombinedReportConfig from './CombinedReportConfig';

const getReportByVis = visualization => ({
  data: {reportIds: ['test1']},
  result: {
    test1: {data: {visualization, view: {property: 'frequency'}}}
  }
});

const lineReport = getReportByVis('line');
const barReport = getReportByVis('bar');
const numberReport = getReportByVis('number');
const tableReport = getReportByVis('table');

const configuration = {
  showInstanceCount: false,
  color: '#1991c8',
  pointMarkers: true,
  hideRelativeValue: false,
  hideAbsoluteValue: false,
  xLabel: '',
  yLabel: '',
  targetValue: null
};

it('it should display correct configuration for linechart', () => {
  const node = shallow(<CombinedReportConfig {...{report: lineReport, configuration}} />);
  expect(node).toMatchSnapshot();
});

it('it should display correct configuration for barchart', () => {
  const node = shallow(<CombinedReportConfig {...{report: barReport, configuration}} />);
  expect(node).toMatchSnapshot();
});

it('it should display correct configuration for number report', () => {
  const node = shallow(<CombinedReportConfig {...{report: numberReport, configuration}} />);
  expect(node).toMatchSnapshot();
});

it('it should display correct configuration for table report', () => {
  const node = shallow(<CombinedReportConfig {...{report: tableReport, configuration}} />);
  expect(node).toMatchSnapshot();
});

it('should reset to defaults when the visualization changes', () => {
  expect(
    CombinedReportConfig.onUpdate(
      {
        report: {
          data: {reportIds: ['test1']},
          result: {
            test1: {data: {visualization: 'prev', view: {property: 'frequency'}}}
          }
        }
      },
      {
        report: {
          data: {reportIds: ['test1']},
          result: {
            test1: {data: {visualization: 'new', view: {property: 'frequency'}}}
          }
        }
      }
    )
  ).toEqual(CombinedReportConfig.defaults);
});
