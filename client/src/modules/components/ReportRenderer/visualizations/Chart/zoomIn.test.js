/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {parseISO} from 'date-fns';

import {format, BACKEND_DATE_FORMAT} from 'dates';

import zoomIn from './zoomIn';

function simulateEvent(node, evt, payload = {}) {
  const event = new MouseEvent(evt, {...payload, bubbles: true});
  Object.keys(payload).forEach((key) => (event[key] = payload[key]));
  node.dispatchEvent(event);
}

let chart, updateReport, plugin, container;

beforeEach(() => {
  const canvas = document.createElement('canvas');
  container = document.createElement('div');
  container.appendChild(canvas);
  document.body.appendChild(container);

  chart = {
    canvas,
    options: {},
    chartArea: {
      left: 0,
      right: 100,
    },
  };
  updateReport = jest.fn();

  plugin = zoomIn({
    updateReport,
    filters: [],
    type: 'instanceStartDate',
    valueRange: {
      min: parseISO('2019-01-01T00:00:00.000'),
      max: parseISO('2019-01-31T00:00:00.000'),
    },
  });
  plugin.afterInit(chart);

  chart.options.onHover = chart.options.onHover.bind(chart);
});

afterEach(() => {
  document.body.removeChild(container);
});

it('should create a startDate filter on zoom interaction', () => {
  chart.options.onHover({native: {offsetX: 20}});
  simulateEvent(chart.canvas, 'mousedown', {offsetX: 20});
  chart.options.onHover({native: {offsetX: 70}});
  simulateEvent(chart.canvas, 'mousemove', {offsetX: 70, movementX: 50});
  simulateEvent(chart.canvas, 'mouseup');

  expect(updateReport).toHaveBeenCalledWith(
    {
      filter: {
        $set: [
          {
            data: {
              end: format(parseISO('2019-01-22T00:00:00'), BACKEND_DATE_FORMAT),
              start: format(parseISO('2019-01-07T00:00:00'), BACKEND_DATE_FORMAT),
              type: 'fixed',
            },
            type: 'instanceStartDate',
            filterLevel: 'instance',
          },
        ],
      },
    },
    true
  );
});

it('should clean up when the chart is destroyed', () => {
  const container = chart.canvas.parentNode;
  const selectionIndicator = container.querySelector('.selectionIndicator');

  expect(container.contains(selectionIndicator)).toBe(true);

  plugin.destroy();

  expect(container.contains(selectionIndicator)).toBe(false);
});
