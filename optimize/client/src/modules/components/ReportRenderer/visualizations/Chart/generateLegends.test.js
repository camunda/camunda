/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import generateLegends from './generateLegends';

jest.mock('translation', () => ({
  t: (key) => key,
}));

function createChart({legendHeight = 200, outerRadius = 100, legendHitBoxes = [], labels = []}) {
  return {
    id: 'test-chart',
    _metasets: [{controller: {outerRadius}}],
    legend: {
      height: legendHeight,
      legendHitBoxes,
    },
    options: {
      plugins: {
        legend: {
          autoCollapse: true,
          maxHeight: undefined,
          labels: {
            generateLabels: () =>
              labels.map((text) => ({
                text,
                fillStyle: 'blue',
                strokeStyle: 'blue',
              })),
          },
        },
      },
    },
    canvas: document.createElement('canvas'),
    ctx: {
      fillStyle: '',
      fillText: jest.fn(),
    },
    update: jest.fn(),
  };
}

beforeEach(() => {
  jest.clearAllMocks();
});

it('should not crash when legendHitBoxes has fewer items than expected', () => {
  const chart = createChart({
    legendHeight: 200,
    outerRadius: 100,
    labels: ['Label A', 'Label B'],
    legendHitBoxes: [
      {left: 0, width: 50, top: 10},
      {left: 0, width: 50, top: 30},
      {left: 0, width: 50, top: 50},
    ],
  });

  generateLegends.beforeInit(chart);
  // First call triggers truncation
  generateLegends.beforeDraw(chart);
  // After truncation, previouslyTruncated is set — second call attempts addText
  generateLegends.beforeDraw(chart);

  // Should not throw — the text should be placed at the last available hitBox
  expect(chart.ctx.fillText).toHaveBeenCalled();
});

it('should not crash when legendHitBoxes is empty', () => {
  const chart = createChart({
    legendHeight: 200,
    outerRadius: 100,
    labels: ['Label A', 'Label B'],
    legendHitBoxes: [],
  });

  generateLegends.beforeInit(chart);
  generateLegends.beforeDraw(chart);
  generateLegends.beforeDraw(chart);

  // Should not throw
  expect(chart.ctx.fillText).not.toHaveBeenCalled();
});

it('should skip drawing when autoCollapse is disabled', () => {
  const chart = createChart({legendHeight: 200, outerRadius: 100});
  chart.options.plugins.legend.autoCollapse = false;

  generateLegends.beforeDraw(chart);

  expect(chart.update).not.toHaveBeenCalled();
  expect(chart.ctx.fillText).not.toHaveBeenCalled();
});

it('should not truncate when legend height is smaller than chart radius', () => {
  const chart = createChart({
    legendHeight: 50,
    outerRadius: 100,
    labels: ['Label A', 'Label B', 'Label C'],
    legendHitBoxes: [
      {left: 0, width: 50, top: 10},
      {left: 0, width: 50, top: 30},
      {left: 0, width: 50, top: 50},
    ],
  });

  generateLegends.beforeInit(chart);
  generateLegends.beforeDraw(chart);

  expect(chart.update).not.toHaveBeenCalled();
});
