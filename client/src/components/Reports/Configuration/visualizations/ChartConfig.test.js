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

it('should invok onchange when changing switch for the point markers on line chart', () => {
  const spy = jest.fn();
  const node = shallow(<ChartConfig {...{report: lineReport, configuration}} onChange={spy} />);

  node
    .find('Switch')
    .first()
    .simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith('pointMarkers', false);
});

it('should invok onchange when changing xlabel/ylabel input on bar chart', () => {
  const spy = jest.fn();
  const node = shallow(<ChartConfig {...{report: barReport, configuration}} onChange={spy} />);

  node
    .find('LabeledInput')
    .first()
    .simulate('change', {target: {value: 'testLabel'}});

  expect(spy).toHaveBeenCalledWith('xLabel', 'testLabel');
});

it('should invok onchange when enabling target value switch', () => {
  const spy = jest.fn();
  const node = shallow(<ChartConfig {...{report: lineReport, configuration}} onChange={spy} />);

  node.find('.goalLine Switch').simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith('targetValue', {
    active: true,
    values: {dateFormat: '', isBelow: false, target: ''}
  });
});
