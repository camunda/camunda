import React from 'react';
import {mount} from 'enzyme';

import ReportView from './ReportView';
import {Number} from './views';

jest.mock('./views', () => {return {
  Number: props => <div>Number: {props.data}</div>,
  Json: props => <div>JSON: {JSON.stringify(props.data)}</div>
}});

it('should display a number if visualization is number', () => {
  const node = mount(<ReportView data={{
    visualization: 'number',
    result: 1234
  }} />);

  expect(node).toIncludeText('Number: 1234');
});

it('should display a json if visualization is json', () => {
  const node = mount(<ReportView data={{
    visualization: 'json',
    result: 1234
  }} />);

  expect(node).toIncludeText('JSON');
});

it('should provide an errorMessage property to the component', () => {
  const node = mount(<ReportView data={{
    visualization: 'number',
    result: 1234
  }} />);

  expect(node.find(Number)).toHaveProp('errorMessage');
});
