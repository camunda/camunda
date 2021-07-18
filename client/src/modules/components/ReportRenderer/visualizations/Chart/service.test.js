/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatTooltip, calculateLinePosition, getTooltipLabelColor} from './service';

it('should include the relative value in tooltips', () => {
  const response = formatTooltip({
    dataset: {data: [2.5]},
    dataIndex: 0,
    configuration: {},
    formatter: (v) => v,
    instanceCount: 5,
    isDuration: false,
  });

  expect(response).toBe('2.5\u00A0(50%)');
});

it('should return undefined tooltip for target line dataset', () => {
  const response = formatTooltip({
    dataset: {data: [2.5], isTarget: true},
    dataIndex: 0,
    configuration: {},
    formatter: (v) => v,
    instanceCount: 5,
    isDuration: false,
  });

  expect(response).toBe(undefined);
});

it('should display a label before the data if specified', () => {
  const response = formatTooltip({
    dataset: {data: [2], label: 'testLabel'},
    dataIndex: 0,
    configuration: {},
    formatter: (v) => v,
    instanceCount: 5,
    isDuration: true,
    showLabel: true,
  });

  expect(response).toBe('testLabel: 2');
});

it('should generate correct colors in label tooltips for pie charts ', () => {
  const response = getTooltipLabelColor(
    {dataIndex: 0, dataset: {backgroundColor: ['testColor1'], legendColor: 'testColor2'}},
    'pie'
  );

  expect(response).toEqual({
    borderColor: 'testColor1',
    backgroundColor: 'testColor1',
  });
});

it('should generate correct colors in label tooltips for bar charts', () => {
  const response = getTooltipLabelColor(
    {dataIndex: 0, dataset: {backgroundColor: ['testColor1'], legendColor: 'testColor2'}},
    'bar'
  );

  expect(response).toEqual({
    borderColor: 'testColor2',
    backgroundColor: 'testColor2',
  });
});

it('should calculate the correct position for the target value line', () => {
  expect(
    calculateLinePosition({
      scales: {
        'axis-0': {
          max: 100,
          height: 100,
          top: 0,
        },
      },
      options: {
        lineAt: 20,
      },
    })
  ).toBe(80); // inverted y axis: height - lineAt = 100 - 20 = 80
});
