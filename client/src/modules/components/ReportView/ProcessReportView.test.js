import React from 'react';
import {shallow} from 'enzyme';

import ProcessReportView from './ProcessReportView';
import {Number, Table} from './views';

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
    formatReportResult: (data, result) => result
  };
});

it('should display a number if visualization is number', () => {
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

  const node = shallow(<ProcessReportView report={report} />);
  node.setState({
    loaded: true
  });

  expect(node.find(Number)).toBePresent();
  expect(node.find(Number).prop('result')).toBe(1234);
});

it('should provide an errorMessage property to the component', () => {
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

  const node = shallow(<ProcessReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node.find(Number)).toHaveProp('errorMessage');
});

it('should instruct to add a process definition key if not available', () => {
  const report = {
    combined: false,
    reportType: 'process',
    data: {
      processDefinitionKey: '',
      processDefinitionVersion: '1',
      view: {
        operation: 'foo'
      },
      groupBy: {
        type: 'bar'
      },
      visualization: 'number'
    },
    result: 1234
  };

  const node = shallow(<ProcessReportView report={report} type="process" />);
  node.setState({
    loaded: true
  });
  expect(node.find('ReportBlankSlate').prop('message')).toContain('Process Definition');
});

it('should instruct to add a process definition version if not available', () => {
  const report = {
    combined: false,
    reportType: 'process',
    data: {
      processDefinitionKey: 'aKey',
      processDefinitionVersion: '',
      view: {
        operation: 'foo'
      },
      groupBy: {
        type: 'bar'
      },
      visualization: 'number'
    },
    result: 1234
  };

  const node = shallow(<ProcessReportView report={report} type="process" />);
  node.setState({
    loaded: true
  });
  expect(node.find('ReportBlankSlate').prop('message')).toContain('Process Definition');
});

it('should instruct to add view option if not available', () => {
  const report = {
    combined: false,
    reportType: 'process',
    data: {
      processDefinitionKey: 'aKey',
      processDefinitionVersion: '1',
      view: null,
      groupBy: {
        type: 'bar'
      },
      visualization: 'number'
    },
    result: 1234
  };

  const node = shallow(<ProcessReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node.find('ReportBlankSlate').prop('message')).toContain('View');
});

it('should instruct to add group by option if not available', () => {
  const report = {
    combined: false,
    reportType: 'process',
    data: {
      processDefinitionKey: 'aKey',
      processDefinitionVersion: '1',
      view: {
        operation: 'foo'
      },
      groupBy: null,
      visualization: 'number'
    },
    result: 1234
  };

  const node = shallow(<ProcessReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node.find('ReportBlankSlate').prop('message')).toContain('Group by');
});

it('should instruct to add visualization option if not available', () => {
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
      visualization: null
    },
    result: 1234
  };

  const node = shallow(<ProcessReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node.find('ReportBlankSlate').prop('message')).toContain('Visualize as');
});

it('should not add instruction for group by if operation is raw data', () => {
  const report = {
    combined: false,
    reportType: 'process',
    data: {
      processDefinitionKey: 'aKey',
      processDefinitionVersion: '1',
      configuration: {},
      view: {
        operation: 'rawData'
      },
      groupBy: {
        type: ''
      },
      visualization: 'table'
    },
    result: 1234
  };

  const node = shallow(<ProcessReportView report={report} />);
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

it('should call the applyAddons function if provided', () => {
  const spy = jest.fn();
  const node = shallow(<ProcessReportView report={exampleDurationReport} applyAddons={spy} />);
  node.setState({
    loaded: true
  });

  expect(spy).toHaveBeenCalled();
});

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

  const node = shallow(<ProcessReportView report={report} />);
  node.setState({
    loaded: true
  });

  expect(node.find('.additionalInfo')).toBePresent();
  expect(node.find('.additionalInfo').text()).toContain('723');
});

it('should pass on custom props if indicated by the visualization', () => {
  const node = shallow(
    <ProcessReportView
      report={exampleDurationReport}
      customProps={{table: {someInfo: 'very important'}}}
    />
  );
  node.setState({
    loaded: true
  });

  expect(node.find(Table)).toHaveProp('someInfo', 'very important');
});

it('should pass the report Type to the visualization component', () => {
  const node = shallow(<ProcessReportView report={exampleDurationReport} type="process" />);
  node.setState({
    loaded: true
  });

  expect(node.find(Table)).toHaveProp('reportType', 'process');
});
