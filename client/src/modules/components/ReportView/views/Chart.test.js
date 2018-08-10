import React from 'react';
import {mount} from 'enzyme';

import ThemedChart from './Chart';
import ChartRenderer from 'chart.js';

import {getRelativeValue} from './service';
import {formatters} from 'services';

const {WrappedComponent: Chart} = ThemedChart;

const {convertToMilliseconds} = formatters;

jest.mock('chart.js', () =>
  jest.fn(() => {
    return {
      destroy: jest.fn()
    };
  })
);

jest.mock('./service', () => {
  return {
    getRelativeValue: jest.fn()
  };
});

jest.mock('services', () => {
  return {
    formatters: {convertToMilliseconds: jest.fn()}
  };
});

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

it('should show nice ticks for duration formats on the y axis', () => {
  const data = {foo: 7 * 24 * 60 * 60 * 1000};
  const node = mount(<Chart data={data} />);

  const config = node.instance().createDurationFormattingOptions(data, 0);

  expect(config.stepSize).toBe(1 * 24 * 60 * 60 * 1000);
  expect(config.callback(3 * 24 * 60 * 60 * 1000)).toBe('3d');
});

it('should generate colors', () => {
  const node = mount(<Chart data={{foo: 123}} />);

  const colors = node.instance().createColors(7);

  expect(colors).toHaveLength(7);
  expect(colors[5]).not.toEqual(colors[6]);
});

it('should include the relative value in tooltips', () => {
  getRelativeValue.mockClear();
  getRelativeValue.mockReturnValue('12.3%');

  const data = {foo: 123};
  const node = mount(
    <Chart
      data={data}
      processInstanceCount={5}
      property="frequency"
      targetValue={{active: false}}
      formatter={v => v}
    />
  );

  const response = node
    .instance()
    .createChartOptions('bar')
    .tooltips.callbacks.label({index: 0, datasetIndex: 0}, {datasets: [{data: [3]}]});

  expect(getRelativeValue).toHaveBeenCalledWith(3, 5);
  expect(response).toBe('3 (12.3%)');
});

it('should return the default bar color if targetvalue is not active', () => {
  const data = {foo: 123};
  const node = mount(<Chart data={data} />);

  const value = node.instance().determineBarColor({active: false, values: null}, data);
  expect(value).toEqual('#1991c8');
});

it('should return red color for all bars below a target value', () => {
  const data = {foo: 123, bar: 5};
  const node = mount(<Chart data={data} />);

  const value = node.instance().determineBarColor(
    {
      active: true,
      values: {
        isBelow: false,
        target: '10',
        dateFormat: ''
      }
    },
    data
  );
  expect(value).toEqual(['#1991c8', '#A62A31']);
});

it('should set LineAt option to 0 if the target value is not active', () => {
  const data = {foo: 123, bar: 5};
  const node = mount(<Chart data={data} />);
  const value = node.instance().getFormattedTargetValue({active: false, values: {}});
  expect(value).toBe(0);
});

it('should set LineAt option to target value if it is active', () => {
  const data = {foo: 123, bar: 5};
  const node = mount(<Chart data={data} />);
  const value = node.instance().getFormattedTargetValue({active: true, values: {target: 10}});
  expect(value).toBe(10);
});

it('should invoke convertToMilliSeconds when target value is set to Date Format', () => {
  const data = {foo: 123, bar: 5};
  const node = mount(<Chart data={data} />);
  node
    .instance()
    .getFormattedTargetValue({active: true, values: {target: 10, dateFormat: 'millis'}});
  expect(convertToMilliseconds).toBeCalledWith(10, 'millis');
});
