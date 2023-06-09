/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  frequency as frequencyFormatter,
  percentage as percentageFormatter,
  camelCaseToLabel,
  formatReportResult,
  createDurationFormattingOptions,
  formatFileName,
  getRelativeValue,
  formatVersions,
  formatTenants,
  formatLabel,
} from './formatters';

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
