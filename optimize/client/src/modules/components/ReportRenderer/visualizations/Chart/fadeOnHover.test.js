/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import fadeOnHover from './fadeOnHover';

jest.mock('chart.js/helpers', () => ({
  color: () => ({alpha: () => ({rgbString: () => 'fadded'})}),
}));

jest.useFakeTimers();

const chart = {
  canvas: document.createElement('canvas'),
  options: {},
  chartArea: {
    top: 100,
    bottom: 100,
    left: 0,
    right: 100,
  },
  data: {
    datasets: [],
  },
  update: jest.fn(),
};

beforeEach(() => {
  chart.data.datasets = [
    {backgroundColor: ['red', 'blue'], borderColor: ['blue', 'red']},
    {backgroundColor: ['green', 'yellow'], borderColor: ['green', 'yellow']},
  ];
});

it('should fadeout background color of the non hovered pie slices', () => {
  const plugin = fadeOnHover({visualization: 'pie'});
  plugin.afterInit(chart);

  const datasets = [{datasetIndex: 0, index: 0}];
  chart.options.onHover({}, datasets, chart);
  jest.runAllTimers();

  expect(chart.data.datasets[0].backgroundColor[0]).toBe('red');
  expect(chart.data.datasets[0].backgroundColor[1]).toBe('fadded');
  expect(chart.data.datasets[1].backgroundColor[0]).toBe('fadded');
  expect(chart.data.datasets[1].backgroundColor[1]).toBe('fadded');
});

it('should fadeout background color of the non hovered bar datasets', () => {
  const plugin = fadeOnHover({visualization: 'bar', isStacked: false});
  plugin.afterInit(chart);

  const datasets = [{datasetIndex: 1}];
  chart.options.onHover({}, datasets, chart);
  jest.runAllTimers();

  expect(chart.data.datasets).toEqual([
    {
      hovered: true,
      backgroundColor: ['fadded', 'fadded'],
      borderColor: ['fadded', 'fadded'],
      'original-backgroundColor': ['red', 'blue'],
      'original-borderColor': ['blue', 'red'],
    },
    {
      hovered: false,
      backgroundColor: ['green', 'yellow'],
      borderColor: ['green', 'yellow'],
      'original-backgroundColor': ['green', 'yellow'],
      'original-borderColor': ['green', 'yellow'],
    },
  ]);
});

it('should reset colors when hovering outside chart area', () => {
  const plugin = fadeOnHover({visualization: 'pie'});
  plugin.afterInit(chart);

  chart.options.onHover({}, [{datasetIndex: 1}], chart);
  jest.runAllTimers();

  plugin.afterEvent(chart, {event: {type: 'mouseout'}});

  expect(chart.data.datasets[0].backgroundColor[1]).not.toBe('fadded');
});

it('should delay the hover when entering the chart', () => {
  const plugin = fadeOnHover({visualization: 'pie'});
  plugin.afterInit(chart);

  // enter the chart
  chart.options.onHover({}, [], chart);
  chart.options.onHover({}, [{datasetIndex: 1}], chart);

  expect(chart.data.datasets[0].backgroundColor[1]).toBe('blue');

  jest.runAllTimers();
  expect(chart.data.datasets[0].backgroundColor[1]).toBe('fadded');
});

it('should not have a delay when moving the mouse after the timer elapses', () => {
  const plugin = fadeOnHover({visualization: 'pie'});
  plugin.afterInit(chart);

  // enter the chart
  chart.options.onHover({}, [], chart);
  chart.options.onHover({}, [{datasetIndex: 1}], chart);

  //run the timeout
  jest.runAllTimers();

  // hover over another item
  expect(chart.data.datasets[0].backgroundColor[0]).toBe('fadded');
  chart.options.onHover({}, [{datasetIndex: 0, index: 0}], chart);
  expect(chart.data.datasets[0].backgroundColor[0]).toBe('red');
});

it('should have the delay after leaving the chart and enter again', () => {
  const plugin = fadeOnHover({visualization: 'pie'});
  plugin.afterInit(chart);

  // enter the chart
  chart.options.onHover({}, [], chart);
  chart.options.onHover({}, [{datasetIndex: 1}], chart);

  // timer elapsed
  jest.runAllTimers();

  // leave the chart
  chart.options.onHover({}, [{datasetIndex: 1}], chart);
  chart.options.onHover({}, [], chart);

  // enter again
  expect(chart.data.datasets[0].backgroundColor[1]).toBe('blue');
  chart.options.onHover({}, [{datasetIndex: 1}], chart);
  expect(chart.data.datasets[0].backgroundColor[1]).toBe('blue');

  // after the delay
  jest.runAllTimers();
  expect(chart.data.datasets[0].backgroundColor[1]).toBe('fadded');
});
