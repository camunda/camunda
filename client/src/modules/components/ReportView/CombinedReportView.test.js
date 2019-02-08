import React from 'react';
import {shallow} from 'enzyme';

import CombinedReportView from './CombinedReportView';
import {Chart, Table} from './views';

jest.mock('./service', () => {
  return {
    isEmpty: str => !str,
    getConfig: props => props
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
  const node = shallow(<CombinedReportView report={CombinedReport} errorMessage="test" />);

  expect(node.find(Table)).toHaveProp('errorMessage');
});

it('should instruct to select one or more reports if no reports are selected for combined reports', () => {
  const report = {
    combined: true,
    data: {
      configuration: {},
      reports: []
    }
  };

  const node = shallow(<CombinedReportView report={report} />);

  expect(node.find('ReportBlankSlate').prop('errorMessage')).toContain('one or more reports');
});

it('should pass the report to the visualization component', () => {
  const node = shallow(<CombinedReportView report={CombinedReport} />);

  expect(node.find(Table)).toHaveProp('report', CombinedReport);
});

xit('should convert results of a combined number report to a correctly formatted barchart data', () => {
  const NumberReportA = {
    ...reportA,
    data: {
      ...reportA.data,
      visualization: 'number'
    },
    result: 100
  };

  const NumberReportB = {
    ...reportA,
    name: 'report B',
    result: 200
  };

  const CombinedReport = {
    combined: true,
    reportType: 'process',
    data: {
      configuration: {},
      reports: ['NumberReportA', 'NumberReportB'],
      visualization: 'number'
    },
    result: {
      NumberReportA: NumberReportA,
      NumberReportB: NumberReportB
    }
  };

  const node = shallow(<CombinedReportView report={CombinedReport} />);
  expect(node.find(Chart)).toHaveProp('data', [{'report A': 100}, {'report B': 200}]);
});
