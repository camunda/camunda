import {getTableProps, formatResult, getChartProps} from './service';

const exampleDurationReport = {
  name: 'report A',
  reportType: 'single',
  processInstanceCount: 100,
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      operation: 'foo'
    },
    groupBy: {
      type: 'startDate',
      unit: 'day'
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
  reportType: 'combined',
  data: {
    configuration: {},
    reports: ['report A', 'report B']
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

it('should return correct single table repot data proberties', () => {
  const tableProps = getTableProps(
    exampleDurationReport.reportType,
    exampleDurationReport.result,
    exampleDurationReport.data,
    exampleDurationReport.processInstanceCount
  );

  expect(tableProps).toEqual({
    data: {'2015-03-26': 3, '2015-03-25': 2},
    labels: ['foo', 'foo'],
    processInstanceCount: 100
  });
});

it('should return correct cominbed table repot data proberties', () => {
  const tableProps = getTableProps(
    combinedReport.reportType,
    combinedReport.result,
    combinedReport.data,
    combinedReport.processInstanceCount
  );

  expect(tableProps).toEqual({
    labels: [['foo', 'foo'], ['foo', 'foo']],
    reportsNames: ['report A', 'report A'],
    data: [{'2015-03-25': 2, '2015-03-26': 3}, {'2015-03-25': 2, '2015-03-26': 3}],
    processInstanceCount: [100, 100]
  });
});

it('should return correct single chart repot data proberties', () => {
  const chartProps = getChartProps(
    exampleDurationReport.reportType,
    exampleDurationReport.result,
    exampleDurationReport.data,
    exampleDurationReport.processInstanceCount
  );

  expect(chartProps).toEqual({
    data: {'2015-03-26': 3, '2015-03-25': 2},
    processInstanceCount: 100
  });
});

it('should return correct cominbed chart repot data proberties for single report', () => {
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

  const chartProps = getChartProps(
    combinedChartReport.reportType,
    combinedChartReport.result,
    exampleChartDurationReport.data,
    combinedChartReport.processInstanceCount
  );

  expect(chartProps).toEqual({
    data: [{'2015-03-25': 2, '2015-03-26': 3}, {'2015-03-25': 2, '2015-03-26': 3}],
    reportsNames: ['report A', 'report A'],
    isDate: true,
    processInstanceCount: [100, 100]
  });
});
