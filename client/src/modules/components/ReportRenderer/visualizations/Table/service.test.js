import {getFormattedLabels, getBodyRows, getCombinedTableProps} from './service';

jest.mock('request', () => ({
  get: jest.fn()
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    reportConfig: {
      process: {
        getLabelFor: () => 'foo',
        options: {
          view: {foo: {data: 'foo', label: 'viewfoo'}},
          groupBy: {
            foo: {data: 'foo', label: 'groupbyfoo'}
          }
        }
      }
    }
  };
});

it('should return correctly formatted body rows', () => {
  expect(
    getBodyRows([{a: 1, b: 2}, {a: '', b: 0}], ['a', 'b'], v => v, false, [100, 100], true)
  ).toEqual([['a', 1, ''], ['b', 2, 0]]);
});

it('should hide absolute values when sepcified from body rows', () => {
  expect(
    getBodyRows([{a: 1, b: 2}, {a: '', b: 1}], ['a', 'b'], v => v, false, [100, 100], false)
  ).toEqual([['a'], ['b']]);
});

it('should return correct table label structure', () => {
  expect(
    getFormattedLabels([['key', 'value'], ['key', 'value']], ['Report A', 'Report B'], false, true)
  ).toEqual([{label: 'Report A', columns: ['value']}, {label: 'Report B', columns: ['value']}]);
});

it('should hide absolute values when specified from labels', () => {
  expect(
    getFormattedLabels([['key', 'value'], ['key', 'value']], ['Report A', 'Report B'], false, false)
  ).toEqual([{columns: [], label: 'Report A'}, {columns: [], label: 'Report B'}]);
});

it('should return correct combined table report data properties', () => {
  const report = {
    name: 'report A',
    combined: false,
    processInstanceCount: 100,
    data: {
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
      'report A': report,
      'report B': report
    }
  };
  const tableProps = getCombinedTableProps(combinedReport.result, combinedReport.data.reportIds);

  expect(tableProps).toEqual({
    combinedResult: [{'2015-03-25': 2, '2015-03-26': 3}, {'2015-03-25': 2, '2015-03-26': 3}],
    labels: [['foo', 'foo'], ['foo', 'foo']],
    processInstanceCount: [100, 100],
    reportsNames: ['report A', 'report A']
  });
});
