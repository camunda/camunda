import React from 'react';
import {shallow} from 'enzyme';

import ThemedChart from './Chart';
import ChartRenderer from 'chart.js';

import {getRelativeValue, uniteResults} from './service';
import {formatters} from 'services';

const {WrappedComponent: Chart} = ThemedChart;

const {convertToMilliseconds} = formatters;

jest.mock('./service', () => {
  return {
    getRelativeValue: jest.fn(),
    uniteResults: jest.fn().mockReturnValue([{a: 123, b: 5}])
  };
});

jest.mock('services', () => {
  return {
    formatters: {convertToMilliseconds: jest.fn()}
  };
});

it('should construct a Chart', () => {
  shallow(<Chart configuration={{}} data={{foo: 123}} />);

  expect(ChartRenderer).toHaveBeenCalled();
});

it('should display an error message for a non-object result (single number)', () => {
  const node = shallow(<Chart configuration={{}} data={7} errorMessage="Error" />);

  expect(node.find('ReportBlankSlate').prop('message')).toBe('Error');
});

it('should display an error message if no data is provided', () => {
  const node = shallow(<Chart configuration={{}} errorMessage="Error" />);

  expect(node.find('ReportBlankSlate').prop('message')).toBe('Error');
});

it('should use the provided type for the ChartRenderer', () => {
  ChartRenderer.mockClear();

  shallow(<Chart configuration={{}} data={{foo: 123}} type="visualization_type" />);

  expect(ChartRenderer.mock.calls[0][1].type).toBe('visualization_type');
});

it('should use the special targetLine type when target values are enabled on a line chart', () => {
  ChartRenderer.mockClear();

  shallow(
    <Chart
      configuration={{}}
      data={{foo: 123}}
      type="line"
      targetValue={{active: true, values: {isBelow: true, value: 1}}}
    />
  );

  expect(ChartRenderer.mock.calls[0][1].type).toBe('targetLine');
});

it('should change type for the ChartRenderer if props were updated', () => {
  ChartRenderer.mockClear();

  const chart = shallow(<Chart configuration={{}} data={{foo: 123}} type="visualization_type" />);
  chart.setProps({type: 'new_visualization_type'});

  expect(ChartRenderer.mock.calls[1][1].type).toBe('new_visualization_type');
});

it('should not display an error message if data is valid', () => {
  const node = shallow(<Chart configuration={{}} data={{foo: 123}} errorMessage="Error" />);

  expect(node.find('ReportBlankSlate')).not.toBePresent();
});

it('should destroy chart if no data is provided', () => {
  const node = shallow(<Chart configuration={{}} errorMessage="Error" />);

  expect(node.chart).toBe(undefined);
});

it('should render chart even if type does not change', () => {
  ChartRenderer.mockClear();

  const chart = shallow(<Chart configuration={{}} data={{foo: 123}} type="visualization_type" />);
  chart.setProps({type: 'visualization_type'});

  expect(ChartRenderer.mock.calls[1][1].type).toBe('visualization_type');
});

it('should display an error message if there was data and the second time no data is provided', () => {
  const node = shallow(<Chart configuration={{}} data={{foo: 123}} errorMessage="Error" />);

  node.setProps({data: null});

  expect(node.find('ReportBlankSlate').prop('message')).toBe('Error');
});

it('should show nice ticks for duration formats on the y axis', () => {
  const data = {foo: 7 * 24 * 60 * 60 * 1000};
  const node = shallow(<Chart configuration={{}} data={data} />);

  const config = node.instance().createDurationFormattingOptions(data, 0);

  expect(config.stepSize).toBe(1 * 24 * 60 * 60 * 1000);
  expect(config.callback(3 * 24 * 60 * 60 * 1000)).toBe('3d');
});

it('should generate colors', () => {
  const node = shallow(<Chart configuration={{}} data={{foo: 123}} />);

  const colors = node.instance().createColors(7);

  expect(colors).toHaveLength(7);
  expect(colors[5]).not.toEqual(colors[6]);
});

it('should include the relative value in tooltips', () => {
  getRelativeValue.mockClear();
  getRelativeValue.mockReturnValue('12.3%');

  const data = {foo: 123};
  const node = shallow(
    <Chart
      configuration={{}}
      data={data}
      processInstanceCount={5}
      property="frequency"
      combined={false}
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
  const node = shallow(<Chart configuration={{}} data={data} />);

  const value = node.instance().determineBarColor({active: false, values: null}, data, 'testColor');
  expect(value).toEqual('testColor');
});

it('should return red color for all bars below a target value', () => {
  const data = {foo: 123, bar: 5};
  const node = shallow(<Chart configuration={{}} data={data} />);

  const value = node.instance().determineBarColor(
    {
      active: true,
      values: {
        isBelow: false,
        target: '10',
        dateFormat: ''
      }
    },
    data,
    'testColor'
  );
  expect(value).toEqual(['testColor', '#A62A31']);
});

it('should set LineAt option to 0 if the target value is not active', () => {
  const data = {foo: 123, bar: 5};
  const node = shallow(<Chart configuration={{}} data={data} />);
  const value = node.instance().getFormattedTargetValue({active: false, values: {}});
  expect(value).toBe(0);
});

it('should set LineAt option to target value if it is active', () => {
  const data = {foo: 123, bar: 5};
  const node = shallow(<Chart configuration={{}} data={data} />);
  const value = node.instance().getFormattedTargetValue({active: true, values: {target: 10}});
  expect(value).toBe(10);
});

it('should invoke convertToMilliSeconds when target value is set to Date Format', () => {
  const data = {foo: 123, bar: 5};
  const node = shallow(<Chart configuration={{}} data={data} />);
  node
    .instance()
    .getFormattedTargetValue({active: true, values: {target: 10, dateFormat: 'millis'}});
  expect(convertToMilliseconds).toBeCalledWith(10, 'millis');
});

it('should create two datasets for line chart with target values', () => {
  const data = {foo: 123, bar: 5, dar: 5};
  const node = shallow(<Chart configuration={{}} data={data} />);
  const targetValue = {values: {target: 10}};
  const lineChartData = node
    .instance()
    .createSingleTargetLineDataset(targetValue, data, 'blue', 'test');
  expect(lineChartData).toHaveLength(2);
});

it('should create two datasets for each report in combined line charts with target values', () => {
  const data = {foo: 123, bar: 5, dar: 5};
  const node = shallow(<Chart configuration={{}} data={data} reportsNames={['test1', 'test2']} />);
  const targetValue = {values: {target: 10}};
  const lineChartData = node
    .instance()
    .createCombinedTargetLineDatasets([data, data], targetValue, ['blue', 'yellow']);
  expect(lineChartData).toHaveLength(4);
});

it('should return correct chart data object for a single report', () => {
  const data = {foo: 123, bar: 5};
  const node = shallow(<Chart configuration={{color: 'testColor'}} data={data} />);

  const result = node.instance().createChartData(data, 'line');

  expect(result).toEqual({
    labels: ['foo', 'bar'],
    datasets: [
      {
        label: undefined,
        legendColor: 'testColor',
        data: [123, 5],
        borderColor: 'testColor',
        backgroundColor: 'transparent',
        borderWidth: 2
      }
    ]
  });
});

it('should return correct chart data object for a combined report', () => {
  const data = [{foo: 123, bar: 5}, {foo: 1, dar: 3}];
  uniteResults.mockReturnValue(data);
  const node = shallow(
    <Chart configuration={{}} data={data} combined={true} reportsNames={['Report A', 'Report B']} />
  );

  const result = node.instance().createChartData(data, 'line');
  expect(result).toEqual({
    labels: ['foo', 'bar', 'dar'],
    datasets: [
      {
        label: 'Report A',
        data: [123, 5],
        borderColor: '#1991c8',
        legendColor: '#1991c8',
        backgroundColor: 'transparent',
        borderWidth: 2
      },
      {
        label: 'Report B',
        data: [1, 3],
        legendColor: 'hsl(180, 65%, 50%)',
        borderColor: 'hsl(180, 65%, 50%)',
        backgroundColor: 'transparent',
        borderWidth: 2
      }
    ]
  });
});

it('should filter labels with undefined names and show correct label coloring', () => {
  const data = [{foo: 123, bar: 5}, {foo: 1, dar: 3}];
  const node = shallow(<Chart configuration={{}} data={data} />);

  const datasets = node.instance().generateLegendLabels({
    data: {
      datasets: [
        {label: undefined, backgroundColor: [], legendColor: 'red'},
        {label: 'test', backgroundColor: ['blue', 'yellow'], legendColor: 'red'}
      ]
    }
  });

  expect(datasets).toEqual([{text: 'test', fillStyle: 'red', strokeStyle: 'red'}]);
});

it('should return correct colors for the tooltip label', () => {
  const data = {foo: 123};
  const node = shallow(<Chart configuration={{}} data={data} />);

  const color = 'testColor';

  const response = node
    .instance()
    .createChartOptions('bar')
    .tooltips.callbacks.labelColor({datasetIndex: 0}, {data: {datasets: [{legendColor: color}]}});

  expect(response).toEqual({
    borderColor: color,
    backgroundColor: color
  });
});
