import React from 'react';
import {shallow} from 'enzyme';

import CombinedReportRenderer from './CombinedReportRenderer';
import {Chart, Table} from './visualizations';

import {processResult} from './service';

jest.mock('./service', () => {
  return {
    isEmpty: str => !str,
    getFormatter: view => v => v,
    processResult: jest.fn().mockImplementation(({result}) => result)
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    formatters: {formatReportResult: (data, result) => result}
  };
});

const reportA = {
  name: 'report A',
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      operation: 'foo'
    },
    groupBy: {
      type: 'processInstance',
      unit: 'day'
    },
    visualization: 'table',
    configuration: {}
  },
  processInstanceCount: 100,
  result: {
    '2015-03-25T12:00:00Z': 2
  }
};

const CombinedReport = {
  combined: true,
  reportType: 'process',
  data: {
    configuration: {},
    reports: ['report A'],
    visualization: 'table'
  },
  result: {
    'report A': reportA
  }
};

it('should provide an errorMessage property to the component', () => {
  const node = shallow(<CombinedReportRenderer report={CombinedReport} errorMessage="test" />);

  expect(node.find(Table)).toHaveProp('errorMessage');
});

it('should render a chart if visualization is number', () => {
  const node = shallow(
    <CombinedReportRenderer
      report={{
        ...CombinedReport,
        result: {
          'report A': {
            ...reportA,
            data: {
              ...reportA.data,
              visualization: 'number'
            }
          }
        }
      }}
    />
  );

  expect(node.find(Chart)).toBePresent();
});

it('should instruct to select one or more reports if no reports are selected for combined reports', () => {
  const report = {
    combined: true,
    data: {
      configuration: {},
      reports: []
    }
  };

  const node = shallow(<CombinedReportRenderer report={report} />);

  expect(node.find('ReportBlankSlate').prop('errorMessage')).toContain('one or more reports');
});

it('should pass the report to the visualization component', () => {
  const node = shallow(<CombinedReportRenderer report={CombinedReport} />);

  expect(node.find(Table)).toHaveProp('report', CombinedReport);
});

it('should process the result of every report it combined', () => {
  processResult.mockClear();

  const report = {...CombinedReport};
  report.result = {
    a: reportA,
    b: 'reportB',
    c: 'reportC'
  };

  shallow(<CombinedReportRenderer report={report} />);

  expect(processResult).toHaveBeenCalledTimes(3);
  expect(processResult).toHaveBeenCalledWith(reportA);
  expect(processResult).toHaveBeenCalledWith('reportB');
  expect(processResult).toHaveBeenCalledWith('reportC');
});
