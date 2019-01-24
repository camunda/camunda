import {getCombinedTableProps, formatResult, getCombinedChartProps} from './service';

const exampleDurationReport = {
  name: 'report A',
  combined: false,
  processInstanceCount: 100,
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      operation: 'foo'
    },
    groupBy: {
      type: 'startDate',
      value: {
        unit: 'day'
      }
    },
    visualization: 'table',
    configuration: {}
  },
  result: {
    '2015-03-25T12:00:00Z': 2,
    '2015-03-26T12:00:00Z': 3
  }
};

const combinedReport = {
  combined: true,
  data: {
    configuration: {},
    reportIds: ['report A', 'report B']
  },
  result: {
    'report A': exampleDurationReport,
    'report B': exampleDurationReport
  }
};

jest.mock('services', () => {
  return {
    reportConfig: {
      getLabelFor: () => 'foo',
      view: {foo: {data: 'foo', label: 'viewfoo'}},
      groupBy: {
        foo: {data: 'foo', label: 'groupbyfoo'}
      }
    }
  };
});

it('should adjust dates to units', () => {
  const formatedResult = formatResult(exampleDurationReport.data, exampleDurationReport.result);
  expect(formatedResult).toEqual({'2015-03-26': 3, '2015-03-25': 2});
});

it('should adjust groupby Start Date option to unit', () => {
  const specialExampleReport = {
    ...exampleDurationReport,
    data: {
      ...exampleDurationReport.data,
      groupBy: {
        type: 'startDate',
        value: {unit: 'month'}
      }
    }
  };
  const formatedResult = formatResult(specialExampleReport.data, specialExampleReport.result);
  expect(formatedResult).toEqual({'Mar 2015': 2});
});

it('should adjust groupby Variable Date option to unit', () => {
  const specialExampleReport = {
    ...exampleDurationReport,
    data: {
      ...exampleDurationReport.data,
      groupBy: {
        type: 'variable',
        value: {type: 'Date'}
      }
    }
  };
  const formatedResult = JSON.stringify(
    formatResult(specialExampleReport.data, specialExampleReport.result)
  );

  expect(formatedResult).not.toContain('2015-03-25T');
  expect(formatedResult).toContain('2015-03-25 ');
});

it('should sort time data descending for tables', () => {
  const formatedResult = formatResult(exampleDurationReport.data, exampleDurationReport.result);

  expect(Object.keys(formatedResult)[0]).toBe('2015-03-26');
});

it('should sort time data ascending for charts', () => {
  const report = {
    ...exampleDurationReport,
    data: {...exampleDurationReport.data, visualization: 'line'}
  };

  const formatedResult = formatResult(report.data, report.result);

  expect(Object.keys(formatedResult)[0]).toBe('2015-03-25');
});

it('should return correct combined table report data properties', () => {
  const tableProps = getCombinedTableProps(combinedReport.result, combinedReport);

  expect(tableProps).toEqual({
    labels: [['foo', 'foo'], ['foo', 'foo']],
    reportsNames: ['report A', 'report A'],
    data: [{'2015-03-25': 2, '2015-03-26': 3}, {'2015-03-25': 2, '2015-03-26': 3}],
    processInstanceCount: [100, 100]
  });
});

it('should return correct cominbed chart repot data properties for single report', () => {
  const exampleChartDurationReport = {
    ...exampleDurationReport,
    data: {
      ...exampleDurationReport.data,
      visualization: 'line'
    }
  };
  const combinedChartReport = {
    ...combinedReport,
    result: {
      'report A': exampleChartDurationReport,
      'report B': exampleChartDurationReport
    }
  };

  const chartProps = getCombinedChartProps(
    combinedChartReport.result,
    exampleChartDurationReport.data,
    combinedChartReport
  );

  expect(chartProps).toEqual({
    data: [{'2015-03-25': 2, '2015-03-26': 3}, {'2015-03-25': 2, '2015-03-26': 3}],
    reportsNames: ['report A', 'report A'],
    isDate: true,
    processInstanceCount: [100, 100]
  });
});

describe('automatic interval selection', () => {
  const autoData = {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      operation: 'foo'
    },
    groupBy: {
      type: 'startDate',
      value: {
        unit: 'automatic'
      }
    },
    visualization: 'table',
    configuration: {}
  };

  it('should use seconds when interval is less than hour', () => {
    const result = {
      '2017-12-27T14:21:56.000': 2,
      '2017-12-27T14:21:57.000': 3
    };

    const formatedResult = formatResult(autoData, result);

    expect(formatedResult).toEqual({'2017-12-27 14:21:57': 3, '2017-12-27 14:21:56': 2});
  });

  it('should use hours when interval is less than a day', () => {
    const result = {
      '2017-12-27T13:21:56.000': 2,
      '2017-12-27T14:25:57.000': 3
    };

    const formatedResult = formatResult(autoData, result);

    expect(formatedResult).toEqual({'2017-12-27 14:00:00': 3, '2017-12-27 13:00:00': 2});
  });

  it('should use day when interval is less than a month', () => {
    const result = {
      '2017-12-20T14:21:56.000': 2,
      '2017-12-27T14:25:57.000': 3
    };

    const formatedResult = formatResult(autoData, result);

    expect(formatedResult).toEqual({'2017-12-27': 3, '2017-12-20': 2});
  });

  it('should use month when interval is less than a year', () => {
    const result = {
      '2017-05-20T14:21:56.000': 2,
      '2017-12-27T14:25:57.000': 3
    };

    const formatedResult = formatResult(autoData, result);

    expect(formatedResult).toEqual({'Dec 2017': 3, 'May 2017': 2});
  });

  it('should use year when interval is greater than/equal a year', () => {
    const result = {
      '2015-05-20T14:21:56.000': 2,
      '2017-12-27T14:25:57.000': 3
    };

    const formatedResult = formatResult(autoData, result);

    expect(formatedResult).toEqual({'2017': 3, '2015': 2});
  });
});
