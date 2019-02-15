import React from 'react';
import {shallow} from 'enzyme';

import ProcessReportRenderer from './ProcessReportRenderer';
import {Number, Table} from './visualizations';

import {getFlowNodeNames} from 'services';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    getFlowNodeNames: jest.fn().mockReturnValue({
      a: 'foo',
      b: 'bar'
    })
  };
});

jest.mock('./service', () => {
  return {
    isEmpty: str => !str,
    getFormatter: view => v => v
  };
});

const report = {
  combined: false,
  reportType: 'process',
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      operation: 'foo'
    },
    groupBy: {
      type: 'bar'
    },
    visualization: 'number',
    configuration: {}
  },
  result: 1234
};

it('should call getFlowNodeNames on mount', () => {
  shallow(<ProcessReportRenderer report={report} type="process" />);

  expect(getFlowNodeNames).toHaveBeenCalled();
});

it('should display a number if visualization is number', () => {
  const node = shallow(<ProcessReportRenderer report={report} />);
  node.setState({
    loaded: true
  });

  expect(node.find(Number)).toBePresent();
  expect(node.find(Number).prop('report')).toBe(report);
});

it('should provide an errorMessage property to the component', () => {
  const node = shallow(<ProcessReportRenderer report={report} errorMessage={'test'} />);
  node.setState({
    loaded: true
  });
  expect(node.find(Number)).toHaveProp('errorMessage');
});

it('should instruct to add a process definition key if not available', () => {
  const newReport = {
    ...report,
    data: {
      ...report.data,
      processDefinitionKey: ''
    }
  };

  const node = shallow(<ProcessReportRenderer report={newReport} type="process" />);
  node.setState({
    loaded: true
  });
  expect(node.find('ReportBlankSlate').prop('errorMessage')).toContain('Process Definition');
});

it('should instruct to add a process definition version if not available', () => {
  const newReport = {
    ...report,
    data: {
      ...report.data,
      processDefinitionVersion: ''
    }
  };

  const node = shallow(<ProcessReportRenderer report={newReport} type="process" />);
  node.setState({
    loaded: true
  });
  expect(node.find('ReportBlankSlate').prop('errorMessage')).toContain('Process Definition');
});

it('should instruct to add view option if not available', () => {
  const newReport = {
    ...report,
    data: {
      ...report.data,
      view: null
    }
  };
  const node = shallow(<ProcessReportRenderer report={newReport} />);
  node.setState({
    loaded: true
  });
  expect(node.find('ReportBlankSlate').prop('errorMessage')).toContain('View');
});

it('should instruct to add group by option if not available', () => {
  const newReport = {
    ...report,
    data: {
      ...report.data,
      groupBy: null
    }
  };

  const node = shallow(<ProcessReportRenderer report={newReport} />);
  node.setState({
    loaded: true
  });
  expect(node.find('ReportBlankSlate').prop('errorMessage')).toContain('Group by');
});

it('should instruct to add visualization option if not available', () => {
  const newReport = {
    ...report,
    data: {
      ...report.data,
      visualization: null
    }
  };

  const node = shallow(<ProcessReportRenderer report={newReport} />);
  node.setState({
    loaded: true
  });
  expect(node.find('ReportBlankSlate').prop('errorMessage')).toContain('Visualize as');
});

it('should not add instruction for group by if operation is raw data', () => {
  const newReport = {
    ...report,
    data: {
      ...report.data,
      view: {
        operation: 'rawData'
      }
    }
  };

  const node = shallow(<ProcessReportRenderer report={newReport} />);
  node.setState({
    loaded: true
  });

  expect(node.find('ReportBlankSlate')).not.toBePresent();
});

const exampleDurationReport = {
  combined: false,
  reportType: 'process',
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
  result: {
    '2015-03-25T12:00:00Z': 2,
    '2015-03-26T12:00:00Z': 3
  }
};

it('should include the instance count if indicated in the config', () => {
  const report = {
    combined: false,
    reportType: 'process',
    data: {
      processDefinitionKey: 'aKey',
      processDefinitionVersion: '1',
      configuration: {showInstanceCount: true},
      view: {
        operation: 'rawData'
      },
      groupBy: {
        type: ''
      },
      visualization: 'table'
    },
    processInstanceCount: 723,
    result: []
  };

  const node = shallow(<ProcessReportRenderer report={report} />);
  node.setState({
    loaded: true
  });

  expect(node.find('.additionalInfo')).toBePresent();
  expect(node.find('.additionalInfo').text()).toContain('723');
});

it('should pass the report to the visualization component', () => {
  const node = shallow(<ProcessReportRenderer report={exampleDurationReport} type="process" />);
  node.setState({
    loaded: true
  });

  expect(node.find(Table)).toHaveProp('report', exampleDurationReport);
});
