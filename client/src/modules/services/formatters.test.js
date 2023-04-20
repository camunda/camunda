/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  frequency as frequencyFormatter,
  duration as durationFormatter,
  percentage as percentageFormatter,
  convertDurationToObject,
  convertDurationToSingleNumber,
  convertToMilliseconds,
  getHighlightedText,
  camelCaseToLabel,
  formatReportResult,
  createDurationFormattingOptions,
  formatFileName,
  getRelativeValue,
  formatVersions,
  formatTenants,
  convertToDecimalTimeUnit,
  formatLabel,
} from './formatters';
const nbsp = '\u00A0';

jest.mock('chart.js', () => {
  return {Chart: {defaults: {scales: {logarithmic: {ticks: {callback: () => ''}}}}}};
});

describe('frequencyFormatter', () => {
  it('should do nothing for numbers < 1000', () => {
    expect(frequencyFormatter(4)).toBe('4');
    expect(frequencyFormatter(194)).toBe('194');
  });

  it('should handle zero well', () => {
    expect(frequencyFormatter(0)).toBe('0');
  });

  it('should format numbers', () => {
    expect(frequencyFormatter(6934)).toBe(new Intl.NumberFormat().format(6934));
    expect(frequencyFormatter(61934)).toBe(new Intl.NumberFormat().format(61934));
    expect(frequencyFormatter(761934)).toBe(new Intl.NumberFormat().format(761934));
    expect(frequencyFormatter(2349875982)).toBe(new Intl.NumberFormat().format(2349875982));
  });

  it('should use a precision', () => {
    expect(frequencyFormatter(123, 1)).toBe(new Intl.NumberFormat().format(100));
    expect(frequencyFormatter(12345, 2)).toBe(new Intl.NumberFormat().format(12) + ' thousand');
    expect(frequencyFormatter(12345, 9)).toBe(new Intl.NumberFormat().format(12.345) + ' thousand');
    expect(frequencyFormatter(12345678, 2)).toBe(new Intl.NumberFormat().format(12) + ' million');
    expect(frequencyFormatter(12345678, 4)).toBe(
      new Intl.NumberFormat().format(12.35) + ' million'
    );

    expect(frequencyFormatter(-123, 1)).toBe(new Intl.NumberFormat().format(-100));
    expect(frequencyFormatter(-42821, 2)).toBe(new Intl.NumberFormat().format(-43) + ' thousand');

    expect(frequencyFormatter(0.1234, 1)).toBe(new Intl.NumberFormat().format(0));
    expect(frequencyFormatter(-0.1234, 2)).toBe(new Intl.NumberFormat().format(-0.1));
    expect(frequencyFormatter(-0.1234, 4)).toBe(new Intl.NumberFormat().format(-0.123));
    expect(frequencyFormatter(70900000000000, 2)).toBe(
      new Intl.NumberFormat().format(71) + ' trillion'
    );
  });

  it('should return -- for nondefined values', () => {
    expect(frequencyFormatter()).toBe('--');
    expect(frequencyFormatter('')).toBe('--');
    expect(frequencyFormatter(null)).toBe('--');
  });
});

describe('durationFormatter', () => {
  it('should format ms input into human readable string', () => {
    const time = 27128;

    expect(durationFormatter(time)).toBe(`27s${nbsp}128ms`);
  });

  it('should handle zero well', () => {
    expect(durationFormatter(0)).toBe('0ms');
  });

  it('should single unit well', () => {
    expect(durationFormatter(5 * 60 * 60 * 1000)).toBe('5h');
  });

  it('should handle single millisecond durations', () => {
    expect(durationFormatter(1)).toBe('1ms');
  });

  it('should handle millisecond durations that are below 1', () => {
    expect(durationFormatter({value: 0.2, unit: 'millis'})).toBe('0.2ms');
  });

  it('should truncate to 2 significant figures for milliseconds', () => {
    expect(durationFormatter({value: 0.24324234234, unit: 'millis'})).toBe('0.24ms');
    expect(durationFormatter({value: 1.234343, unit: 'seconds'})).toBe(`1s${nbsp}234.34ms`);
  });

  it('should not floor millisecond durations only', () => {
    expect(durationFormatter({value: 1.3, unit: 'millis'})).toBe('1.3ms');
    expect(durationFormatter({value: 1.2, unit: 'seconds'})).toBe(`1s${nbsp}200ms`);
  });

  it('should handle a time object', () => {
    expect(durationFormatter({value: 14, unit: 'seconds'})).toBe('14s');
  });

  it('should normalize a time object', () => {
    expect(durationFormatter({value: 15, unit: 'days'})).toBe(`2wk${nbsp}1d`);
  });

  it('should use a precision', () => {
    expect(durationFormatter(123456789, 2)).toBe(`1${nbsp}day${nbsp}10${nbsp}hours`);
    expect(durationFormatter(29009802502, 3)).toBe(
      `11${nbsp}months${nbsp}5${nbsp}days${nbsp}18${nbsp}hours`
    );
    expect(durationFormatter(123456789, 4)).toBe(
      `1${nbsp}day${nbsp}10${nbsp}hours${nbsp}17${nbsp}minutes${nbsp}37${nbsp}seconds`
    );
  });

  it('should use default precision of 3 when the precision is turned off in the report', () => {
    expect(durationFormatter(29009802502, null)).toBe(
      `11${nbsp}months${nbsp}5${nbsp}days${nbsp}18${nbsp}hours`
    );
  });

  it('should return -- for nondefined values', () => {
    expect(durationFormatter()).toBe('--');
    expect(durationFormatter('')).toBe('--');
    expect(durationFormatter(null)).toBe('--');
  });

  it('should return value in short notation', () => {
    expect(durationFormatter(123456789, undefined, true)).toBe(
      `1d${nbsp}10h${nbsp}17min${nbsp}36s${nbsp}789ms`
    );
    expect(durationFormatter(123456789, null, true)).toBe(`1d${nbsp}10h${nbsp}18min`);
  });
});

describe('percentageFormatter', () => {
  it('should format percentage input', () => {
    expect(percentageFormatter(100)).toBe('100%');
  });

  it('show only display two significant figures', () => {
    expect(percentageFormatter(8.456454343434)).toBe('8.46%');
  });

  it('should return -- for nondefined values', () => {
    expect(percentageFormatter()).toBe('--');
    expect(percentageFormatter('')).toBe('--');
    expect(percentageFormatter(null)).toBe('--');
  });
});

describe('convertDurationToObject', () => {
  it('should return an object with value and unit', () => {
    const result = convertDurationToObject(123);

    expect(result.unit).toBeDefined();
    expect(result.value).toBeDefined();
  });

  it('should convert a millisecond value', () => {
    expect(convertDurationToObject(123)).toEqual({value: '123', unit: 'millis'});
    expect(convertDurationToObject(4 * 60 * 1000)).toEqual({value: '4', unit: 'minutes'});
    expect(convertDurationToObject(1000)).toEqual({value: '1', unit: 'seconds'});
    expect(convertDurationToObject(1001)).toEqual({value: '1001', unit: 'millis'});
  });
});

describe('convertToDecimalTimeUnit', () => {
  expect(convertToDecimalTimeUnit(123)).toEqual({value: '123', unit: 'millis'});
  expect(convertToDecimalTimeUnit(4 * 60 * 1000)).toEqual({value: '4', unit: 'minutes'});
  expect(convertToDecimalTimeUnit(1000)).toEqual({value: '1', unit: 'seconds'});
  expect(convertToDecimalTimeUnit(1001)).toEqual({value: '1.001', unit: 'seconds'});
});

describe('convertDurationToSingleNumber', () => {
  it('should return simple numbers unprocessed', () => {
    expect(convertDurationToSingleNumber(123)).toBe(123);
  });

  it('should convert duration objects to millis', () => {
    expect(convertDurationToSingleNumber({value: '123', unit: 'millis'})).toBe(123);
    expect(convertDurationToSingleNumber({value: '2', unit: 'minutes'})).toBe(2 * 60 * 1000);
    expect(convertDurationToSingleNumber({value: '1.5', unit: 'seconds'})).toBe(1500);
  });
});

describe('convertToMilliseconds', () => {
  expect(convertToMilliseconds(5, 'seconds')).toBe(5000);
  expect(convertToMilliseconds(2, 'months')).toBe(5184000000);
  expect(convertToMilliseconds(3, 'hours')).toBe(10800000);
  expect(convertToMilliseconds(100, 'millis')).toBe(100);
});

describe('camelCaseToLabel', () => {
  expect(camelCaseToLabel('fooBar')).toBe('Foo Bar');
  expect(camelCaseToLabel('startDate')).toBe('Start Date');
});

const exampleDurationReport = {
  name: 'report A',
  combined: false,
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      properties: ['foo'],
    },
    groupBy: {
      type: 'startDate',
      value: {
        unit: 'day',
      },
    },
    visualization: 'table',
    configuration: {sorting: null},
  },
  result: {
    instanceCount: 100,
    data: [
      {key: '2015-03-25T12:00:00Z', label: '2015-03-25T12:00:00Z', value: 2},
      {key: '2015-03-26T12:00:00Z', label: '2015-03-26T12:00:00Z', value: 3},
      {key: 'missing', label: 'null/undefined', value: 5},
    ],
  },
};

jest.mock('services', () => {
  return {
    reportConfig: {
      getLabelFor: () => 'foo',
      view: {foo: {data: 'foo', label: 'viewfoo'}},
      groupBy: {
        foo: {data: 'foo', label: 'groupbyfoo'},
      },
    },
  };
});

it('should adjust dates to units', () => {
  const formatedResult = formatReportResult(
    exampleDurationReport.data,
    exampleDurationReport.result.data
  );
  expect(formatedResult).toEqual([
    {key: '2015-03-25T12:00:00Z', label: '2015-03-25', value: 2},
    {key: '2015-03-26T12:00:00Z', label: '2015-03-26', value: 3},
    {key: 'missing', label: 'null/undefined', value: 5},
  ]);
});

it('should adjust groupby Start Date option to unit', () => {
  const specialExampleReport = {
    ...exampleDurationReport,
    data: {
      ...exampleDurationReport.data,
      groupBy: {
        type: 'startDate',
        value: {unit: 'month'},
      },
    },
    result: {
      ...exampleDurationReport.result,
      data: [exampleDurationReport.result.data[0]],
    },
  };
  const formatedResult = formatReportResult(
    specialExampleReport.data,
    specialExampleReport.result.data
  );
  expect(formatedResult).toEqual([{key: '2015-03-25T12:00:00Z', label: 'Mar 2015', value: 2}]);
});

it('should adjust groupby Variable Date option to unit', () => {
  const specialExampleReport = {
    ...exampleDurationReport,
    data: {
      ...exampleDurationReport.data,
      groupBy: {
        type: 'variable',
        value: {type: 'Date'},
      },
      configuration: {
        groupByDateVariableUnit: 'day',
      },
    },
  };
  const formatedResult = formatReportResult(
    specialExampleReport.data,
    specialExampleReport.result.data
  );

  expect(formatedResult[0].label).not.toContain('2015-03-25T');
  expect(formatedResult[0].label).toContain('2015-03-25');
});

describe('automatic interval selection', () => {
  const autoData = {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      properties: ['foo'],
    },
    groupBy: {
      type: 'startDate',
      value: {
        unit: 'automatic',
      },
    },
    visualization: 'table',
    configuration: {sorting: null},
  };

  it('should use seconds when interval is less than hour', () => {
    const result = [
      {key: '2017-12-27T14:21:56.000', label: '2017-12-27T14:21:56.000', value: 2},
      {key: '2017-12-27T14:21:57.000', label: '2017-12-27T14:21:57.000', value: 3},
    ];

    const formatedResult = formatReportResult(autoData, result);

    expect(formatedResult).toEqual([
      {key: result[0].key, label: '2017-12-27 14:21:56', value: 2},
      {key: result[1].key, label: '2017-12-27 14:21:57', value: 3},
    ]);
  });

  it('should use hours when interval is less than a day', () => {
    const result = [
      {key: '2017-12-27T13:21:56.000', label: '2017-12-27T13:21:56.000', value: 2},
      {key: '2017-12-27T14:21:57.000', label: '2017-12-27T14:21:57.000', value: 3},
    ];

    const formatedResult = formatReportResult(autoData, result);

    expect(formatedResult).toEqual([
      {key: result[0].key, label: '2017-12-27 13:00:00', value: 2},
      {key: result[1].key, label: '2017-12-27 14:00:00', value: 3},
    ]);
  });

  it('should use day when interval is less than a month', () => {
    const result = [
      {key: '2017-12-20T13:21:56.000', label: '2017-12-20T13:21:56.000', value: 2},
      {key: '2017-12-27T14:21:57.000', label: '2017-12-27T14:21:57.000', value: 3},
    ];

    const formatedResult = formatReportResult(autoData, result);

    expect(formatedResult).toEqual([
      {key: result[0].key, label: '2017-12-20', value: 2},
      {key: result[1].key, label: '2017-12-27', value: 3},
    ]);
  });

  it('should use month when interval is less than a year', () => {
    const result = [
      {key: '2017-05-27T13:21:56.000', label: '2017-05-27T13:21:56.000', value: 2},
      {key: '2017-12-27T14:21:57.000', label: '2017-12-27T14:21:57.000', value: 3},
    ];

    const formatedResult = formatReportResult(autoData, result);

    expect(formatedResult).toEqual([
      {key: result[0].key, label: 'May 2017', value: 2},
      {key: result[1].key, label: 'Dec 2017', value: 3},
    ]);
  });

  it('should use year when interval is greater than/equal a year', () => {
    const result = [
      {key: '2015-12-27T13:21:56.000', label: '2015-12-27T13:21:56.000', value: 2},
      {key: '2017-12-27T14:21:57.000', label: '2017-12-27T14:21:57.000', value: 3},
    ];

    const formatedResult = formatReportResult(autoData, result);

    expect(formatedResult).toEqual([
      {key: result[0].key, label: '2015 ', value: 2},
      {key: result[1].key, label: '2017 ', value: 3},
    ]);
  });
});

describe('createDurationFormattingOptions', () => {
  const maxDuration = 7 * 24 * 60 * 60 * 1000;

  it('should show nice ticks for duration formats on the y axis', () => {
    const config = createDurationFormattingOptions(0, maxDuration);

    expect(config.stepSize).toBe(1 * 24 * 60 * 60 * 1000);
    expect(config.callback(3 * 24 * 60 * 60 * 1000)).toBe('3d');
  });

  it('should show nice ticks for duration formats on the x axis', () => {
    const config = createDurationFormattingOptions(0, maxDuration);
    config.type = 'category';
    config.getLabels = () => [(2 * 24 * 60 * 60 * 1000).toString()];

    expect(config.stepSize).toBe(1 * 24 * 60 * 60 * 1000);
    expect(config.callback(0)).toBe('2d');
  });

  it('should show skip duration tick values that do not have a log scale value', () => {
    const config = createDurationFormattingOptions(0, maxDuration, true);

    expect(config.callback(3 * 24 * 60 * 60 * 1000)).toBe('');
  });
});

describe('File name formatting', () => {
  const formattedFileName = formatFileName('/*File name,1:');
  expect(formattedFileName).toBe('__file_name_1_');

  const anotherFileName = formatFileName('<another?|name>');
  expect(anotherFileName).toBe('_another__name_');
});

describe('getRelativeValue', () => {
  it('should return correct relative value', () => {
    expect(getRelativeValue(30, 100)).toBe('30%');
  });

  it('should return 0% if value is 0 regardless of total value', () => {
    expect(getRelativeValue(0, 0)).toBe('0%');
  });

  it('should return -- if value is null or not defined', () => {
    expect(getRelativeValue(null, 5)).toBe('--');
  });
});

describe('formatVersions', () => {
  it('should work with an empty versions array', () => {
    expect(formatVersions([])).toBe('None');
  });
  it('should work with special versions', () => {
    expect(formatVersions(['latest'])).toBe('Latest');
    expect(formatVersions(['all'])).toBe('All');
  });
  it('should work with explicit versions', () => {
    expect(formatVersions(['3'])).toBe('3');
    expect(formatVersions(['1', '2', '3'])).toBe('1, 2, 3');
  });
});

describe('formatTenants', () => {
  const tenantInfo = [
    {id: null, name: 'Not Defined'},
    {id: 'a', name: 'Tenant A'},
    {id: 'b', name: 'Tenant B'},
    {id: 'c', name: null},
    {id: '__unauthorizedTenantId__', name: 'Unauthorized Tenant'},
    {id: '__unauthorizedTenantId__', name: 'Unauthorized Tenant'},
  ];

  it('should correctly identify a selection of all tenants', () => {
    expect(
      formatTenants(
        [null, 'a', 'b', 'c', '__unauthorizedTenantId__', '__unauthorizedTenantId__'],
        tenantInfo
      )
    ).toBe('All');
  });

  it('should work with no tenants selected', () => {
    expect(formatTenants([], tenantInfo)).toBe('None');
  });

  it('should correctly format unauthorized tenants', () => {
    expect(formatTenants(['__unauthorizedTenantId__'], tenantInfo)).toBe('(Unauthorized Tenant)');
  });

  it('should correctly format special "null" tenant', () => {
    expect(formatTenants([null], tenantInfo)).toBe('Not defined');
  });

  it('should use tenant names', () => {
    expect(formatTenants(['a', 'b'], tenantInfo)).toBe('Tenant A, Tenant B');
  });

  it('should fall back to tenant ids if no name is set', () => {
    expect(formatTenants(['b', 'c'], tenantInfo)).toBe('Tenant B, c');
  });
});

describe('formatLabel', () => {
  it('should shorten to long string label', () => {
    const string = new Array(55).join('a'); //generates string of length 54 filled with letter 'a'
    const result = formatLabel(string);

    expect(result.length).toBe(53);
    expect(result.slice(50)).toBe('...');
  });

  it('should shorten to long number label', () => {
    const string = new Array(55).join('1'); //generates string of length 54 filled with letter '1'

    expect(formatLabel(string)).toBe('1.111111111111111e+53');
  });

  it('should not change object labels and null values', () => {
    const object = {};

    expect(formatLabel(object)).toBe(object);
    expect(formatLabel(null)).toBe(null);
  });

  it('should only format number labels when numberOnly is true', () => {
    const numberString = new Array(55).join('1');
    const string = new Array(55).join('a');

    expect(formatLabel(numberString, true)).toBe('1.111111111111111e+53');
    expect(formatLabel(string, true)).toBe(string);
  });
});
