import React from 'react';
import {mount} from 'enzyme';

import ReportView from './ReportView';
import {Number} from './views';

jest.mock('./views', () => {return {
  Number: props => <div>Number: {props.data}</div>,
  Json: props => <div>JSON: {JSON.stringify(props.data)}</div>
}});

it('should display a number if visualization is number', () => {
  const report = {
    data: {
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
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);

  expect(node.find(Number)).toHaveProp('errorMessage');
});
