/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fadeOnHover from './fadeOnHover';

jest.mock('chart.js/helpers', () => ({
  color: () => ({alpha: () => ({rgbString: () => 'fadded'})}),
}));

function simulateEvent(node, evt, payload = {}) {
  const event = new MouseEvent(evt, {...payload, bubbles: true});
  Object.keys(payload).forEach((key) => (event[key] = payload[key]));
  node.dispatchEvent(event);
}

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

  const datasets = [{datasetIndex: 1}];
  chart.options.onHover({}, datasets, chart);

  simulateEvent(chart.canvas, 'mousemove', {offsetX: 0, offsetY: 300});

  expect(chart.data.datasets[0].backgroundColor[1]).not.toBe('fadded');
});
