/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  formatTooltip,
  formatTooltipTitle,
  getLabel,
  getTooltipLabelColor,
  hasReportPersistedTooltips,
} from './service';

describe('formatTooltip', () => {
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

  it('should return undefined for null values', () => {
    const response = formatTooltip({
      dataset: {data: [null], label: 'testLabel'},
      dataIndex: 0,
      configuration: {},
      formatter: (v) => v,
      instanceCount: 5,
      isDuration: true,
      showLabel: true,
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

describe('formatTooltipTitle', () => {
  it('should not change the title if enough space is available', () => {
    expect(formatTooltipTitle('This is a sample tooltip title', 1000)).toBe(
      'This is a sample tooltip title'
    );
  });

  it('should wrap text at space characters', () => {
    expect(formatTooltipTitle('This is a sample tooltip title', 100)).toBe(
      'This is a\nsample\ntooltip title'
    );
  });

  it('should handle strings without spaces well', () => {
    expect(formatTooltipTitle('AAAAAAAAAAAAAAAAAAAAAAAAAAA', 100)).toBe(
      'AAAAAAAAAAAAAA\nAAAAAAAAAAAAA'
    );
  });

  it('should handle null values well', () => {
    expect(formatTooltipTitle(null, null)).toBe('');
  });
});

describe('getLabel', () => {
  it('should return property label', () => {
    expect(getLabel({property: 'frequency'})).toBe('Count');
    expect(getLabel({property: 'duration'})).toBe('Duration');
  });

  it('should return label with aggregation type', () => {
    expect(getLabel({property: 'frequency', aggregationType: {type: 'sum'}})).toBe('Count - Sum');
    expect(
      getLabel({property: 'frequency', aggregationType: {type: 'percentile', value: 100}})
    ).toBe('Count - P100');
  });

  it('should return label with user task duration', () => {
    expect(getLabel({property: 'duration', userTaskDurationTime: 'idle'})).toBe(
      'Unassigned Duration'
    );
  });
});

describe('hasReportPersistedTooltips', () => {
  it('should return false when none of alwaysShow is true', () => {
    const report = {
      data: {
        configuration: {alwaysShowAbsolute: false, alwaysShowRelative: false},
      },
    };
    expect(hasReportPersistedTooltips(report)).toBe(false);
  });

  it('should return true when showing absolute values and report is duration', () => {
    expect(
      hasReportPersistedTooltips({
        data: {
          view: {properties: ['duration']},
          configuration: {alwaysShowAbsolute: false, alwaysShowRelative: true},
        },
      })
    ).toBe(false);
    expect(
      hasReportPersistedTooltips({
        data: {
          view: {properties: ['duration']},
          configuration: {alwaysShowAbsolute: true, alwaysShowRelative: false},
        },
      })
    ).toBe(true);
  });

  it('should return true when no duration report has either of alwaysShow properties set to true', () => {
    expect(
      hasReportPersistedTooltips({
        data: {
          configuration: {alwaysShowAbsolute: true, alwaysShowRelative: false},
        },
      })
    ).toBe(true);

    expect(
      hasReportPersistedTooltips({
        data: {
          configuration: {alwaysShowAbsolute: false, alwaysShowRelative: true},
        },
      })
    ).toBe(true);
  });
});
