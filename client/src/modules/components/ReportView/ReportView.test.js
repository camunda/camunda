import React from 'react';
import {mount} from 'enzyme';

import ReportView from './ReportView';
import {Number} from './views';

jest.mock('./views', () => {
  return {
    Number: props => <div>Number: {props.data}</div>,
    Table: props => <div> Table: {JSON.stringify(props.data)}</div>,
    Chart: props => <div> Chart: {JSON.stringify(props.data)}</div>
  };
});

jest.mock('components', () => {
  return {
    ErrorBoundary: props => <div>{props.children}</div>,
    LoadingIndicator: props => (
      <div className="sk-circle" {...props}>
        Loading...
      </div>
    )
  };
});

jest.mock('services', () => {
  return {
    getFlowNodeNames: jest.fn().mockReturnValue({
      a: 'foo',
      b: 'bar'
    })
  };
});

jest.mock('./service', () => {
  return {
    formatResult: (data, result) => result,
    getTableProps: (reportType, result, data, processInstanceCount) => ({
      data: {a: 1, b: 2},
      labels: ['a', 'b'],
      processInstanceCount
    })
  };
});

jest.mock('./ReportBlankSlate', () => {
  return props => {
    return <div className="message">{props.message}</div>;
  };
});

it('should display a number if visualization is number', () => {
  const report = {
    reportType: 'single',
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

  const node = mount(<ReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node).toIncludeText('Number: 1234');
});

it('should provide an errorMessage property to the component', () => {
  const report = {
    reportType: 'single',
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

  const node = mount(<ReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node.find(Number)).toHaveProp('errorMessage');
});

it('should instruct to add a process definition key if not available', () => {
  const report = {
    reportType: 'single',
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

  const node = mount(<ReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node.find('.message')).toIncludeText('Process definition');
});

it('should instruct to add a process definition version if not available', () => {
  const report = {
    reportType: 'single',
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

  const node = mount(<ReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node.find('.message')).toIncludeText('Process definition');
});

it('should instruct to add view option if not available', () => {
  const report = {
    reportType: 'single',
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

  const node = mount(<ReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node.find('.message')).toIncludeText('View');
});

it('should instruct to add group by option if not available', () => {
  const report = {
    reportType: 'single',
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

  const node = mount(<ReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node.find('.message')).toIncludeText('Group by');
});

it('should instruct to add visualization option if not available', () => {
  const report = {
    reportType: 'single',
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

  const node = mount(<ReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node.find('.message')).toIncludeText('Visualize as');
});

it('should not add instruction for group by if operation is raw data', () => {
  const report = {
    reportType: 'single',
    data: {
      processDefinitionKey: 'aKey',
      processDefinitionVersion: '1',
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

  const node = mount(<ReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node).not.toIncludeText('Please choose an option for');
});

const exampleDurationReport = {
  reportType: 'single',
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
  const node = mount(<ReportView report={exampleDurationReport} applyAddons={spy} />);
  node.setState({
    loaded: true
  });

  expect(spy).toHaveBeenCalled();
});

it('should return flownode Id if name is null when calling applyFlowNodeNames', async () => {
  const node = mount(<ReportView report={exampleDurationReport} />);
  node.setState({
    loaded: true
  });
  await node.instance().loadFlowNodeNames('aKey', 1);
  expect(node.instance().applyFlowNodeNames({a: 25, b: 35, c: 25})).toEqual({
    foo: 25,
    bar: 35,
    c: 25
  });
});

it('should instruct to select one or more reports if no reports are selected for combined reports', () => {
  const report = {
    reportType: 'combined',
    data: {
      configuration: {},
      reports: []
    }
  };

  const node = mount(<ReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node.find('.message')).toIncludeText('one or more reports');
});

describe('combined Report View', () => {
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
    reportType: 'combined',
    data: {
      configuration: {},
      reports: ['report A']
    },
    result: {
      'report A': reportA
    }
  };

  it('should invok getConfig function when only one combined report is selected', () => {
    const node = mount(<ReportView report={CombinedReport} />);
    const spy = jest.spyOn(node.instance(), 'getConfig');
    node.setState({
      loaded: true
    });
    expect(spy).toHaveBeenCalled();
  });

  it('should convert results of a combined number report to a correctly formatted barchart data', () => {
    const NumberReportA = {
      ...reportA,
      result: 100
    };

    const NumberReportB = {
      ...reportA,
      name: 'report B',
      result: 200
    };

    const node = mount(<ReportView report={CombinedReport} />);

    const barData = node.instance().getCombinedNumberData({
      NumberReportA: NumberReportA,
      NumberReportB: NumberReportB
    });

    expect(barData).toEqual([{'report A': 100}, {'report B': 200}]);
  });
});
