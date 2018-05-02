import React from 'react';
import {mount} from 'enzyme';

import ReportView from './ReportView';
import {Number, Table, Chart} from './views';

jest.mock('./views', () => {
  return {
    Number: props => <div>Number: {props.data}</div>,
    Table: props => <div> Table: {JSON.stringify(props.data)}</div>,
    Chart: props => <div> Chart: {JSON.stringify(props.data)}</div>
  };
});

jest.mock('components', () => {
  return {
    ErrorBoundary: props => <div>{props.children}</div>
  };
});

jest.mock('services', () => {
  return {
    reportLabelMap: {
      objectToLabel: () => 'foo',
      objectToKey: () => 'foo',
      keyToLabel: () => 'foo',
      getOptions: () => [],
      keyToObject: () => 'foo'
    },
    getFlowNodeNames: jest.fn().mockReturnValue({
      a: 'foo',
      b: 'bar'
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
    data: {
      processDefinitionKey: 'aKey',
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
  expect(node).toIncludeText('Number: 1234');
});

it('should provide an errorMessage property to the component', () => {
  const report = {
    data: {
      processDefinitionKey: 'aKey',
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
  expect(node.find(Number)).toHaveProp('errorMessage');
});

it('should instruct to add a process definition key if not available', () => {
  const report = {
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
    data: {
      processDefinitionKey: 'aKey',
      processDefinitionVersion: '1',
      view: {
        operation: ''
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
  expect(node.find('.message')).toIncludeText('View');
});

it('should instruct to add group by option if not available', () => {
  const report = {
    data: {
      processDefinitionKey: 'aKey',
      processDefinitionVersion: '1',
      view: {
        operation: 'foo'
      },
      groupBy: {
        type: ''
      },
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
    data: {
      processDefinitionKey: 'aKey',
      processDefinitionVersion: '1',
      view: {
        operation: 'foo'
      },
      groupBy: {
        type: 'bar'
      },
      visualization: ''
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
    data: {
      processDefinitionKey: 'aKey',
      processDefinitionVersion: '1',
      view: {
        operation: 'rawData'
      },
      groupBy: {
        type: ''
      },
      visualization: 'number'
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

it('should adjust date shown in table to unit', () => {
  const node = mount(<ReportView report={exampleDurationReport} />);
  node.setState({
    loaded: true
  });
  expect(node.find(Table)).not.toIncludeText('2015-03-25T12:00:00Z');
  expect(node.find(Table)).toIncludeText('2015-03-25');
});

it('should sort time data descending for tables', () => {
  const node = mount(<ReportView report={exampleDurationReport} />);
  node.setState({
    loaded: true
  });

  expect(node.find(Table)).toIncludeText('{"2015-03-26":3,"2015-03-25":2}');
});

it('should sort time data ascending for charts', () => {
  const report = {
    ...exampleDurationReport,
    data: {...exampleDurationReport.data, visualization: 'line'}
  };
  const node = mount(<ReportView report={report} />);
  node.setState({
    loaded: true
  });

  expect(node.find(Chart)).toIncludeText('{"2015-03-25":2,"2015-03-26":3}');
});

it('should call the applyAddons function if provided', () => {
  const spy = jest.fn();
  const node = mount(<ReportView report={exampleDurationReport} applyAddons={spy} />);
  node.setState({
    loaded: true
  });

  expect(spy).toHaveBeenCalled();
});
