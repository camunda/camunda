import React from 'react';
import {mount} from 'enzyme';

import ReportView from './ReportView';
import {Number} from './views';

jest.mock('./views', () => {return {
  Number: props => <div>Number: {props.data}</div>,
  Json: props => <div>JSON: {JSON.stringify(props.data)}</div>
}});

jest.mock('components', () => {return {
  ErrorBoundary: (props) => <div>{props.children}</div>
}});

jest.mock('services', () => {return {
  mapper: {
    objectToLabel: (...props) => 'foo',
    objectToKey: (...props) => 'foo',
    keyToLabel: (...props) => 'foo',
    getOptions: (...props) => [],
    keyToObject: (...props) => 'foo',
  }
}});

it('should display a number if visualization is number', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: 'bar'
      },
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report} />);

  expect(node).toIncludeText('Number: 1234');
});

it('should display a json if visualization is json', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: 'bar'
      },
      visualization: 'json'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);

  expect(node).toIncludeText('JSON');
});

it('should provide an errorMessage property to the component', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: 'bar'
      },
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);

  expect(node.find(Number)).toHaveProp('errorMessage');
});

it('should instruct to add a process definition id if not available', () => {
  const report = {
    data: {
      processDefinitionId: '',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: 'bar'
      },
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);

  expect(node).toIncludeText('Please choose an option for \'Process definition\'!');
});

it('should instruct to add view option if not available', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: ''
      },
      groupBy : {
        type: 'bar'
      },
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);

  expect(node).toIncludeText('Please choose an option for \'View\'!');
});

it('should instruct to add group by option if not available', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: ''
      },
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);

  expect(node).toIncludeText('Please choose an option for \'Group by\'!');
});

it('should instruct to add visualization option if not available', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: 'bar'
      },
      visualization: ''
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);

  expect(node).toIncludeText('Please choose an option for \'Visualize as\'!');
});

it('should not add instruction for group by if operation is raw data', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'rawData'
      },
      groupBy : {
        type: ''
      },
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);

  expect(node).not.toIncludeText('Please choose an option for');
});
