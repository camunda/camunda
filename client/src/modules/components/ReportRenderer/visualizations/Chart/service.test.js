/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatTooltip, calculateLinePosition, getTooltipLabelColor} from './service';

it('should include the relative value in tooltips', () => {
  const response = formatTooltip(
    {index: 0, datasetIndex: 0},
    {datasets: [{data: [2.5]}]},
    false,
    {},
    v => v,
    [5],
    false,
    'bar'
  );

  expect(response).toBe('2.5\u00A0(50%)');
});

it('should generate correct colors in label tooltips for pie charts ', () => {
  const response = getTooltipLabelColor(
    {index: 0, datasetIndex: 0},
    {data: {datasets: [{backgroundColor: ['testColor1'], legendColor: 'testColor2'}]}},
    'pie'
  );

  expect(response).toEqual({
    borderColor: 'testColor1',
    backgroundColor: 'testColor1'
  });
});

it('should generate correct colors in label tooltips for bar charts', () => {
  const response = getTooltipLabelColor(
    {index: 0, datasetIndex: 0},
    {data: {datasets: [{backgroundColor: ['testColor1'], legendColor: 'testColor2'}]}},
    'bar'
  );

  expect(response).toEqual({
    borderColor: 'testColor2',
    backgroundColor: 'testColor2'
  });
});

it('should calculate the correct position for the target value line', () => {
  expect(
    calculateLinePosition({
      scales: {
        test: {
          max: 100,
          height: 100,
          top: 0
        }
      },
      options: {
        scales: {
          yAxes: [{id: 'test'}]
        },
        lineAt: 20
      }
    })
  ).toBe(80); // inverted y axis: height - lineAt = 100 - 20 = 80
});
