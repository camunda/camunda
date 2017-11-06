import React from 'react';
import {mount} from 'enzyme';

import ReportView from './ReportView';

jest.mock('./views', () => {return {
  Number: props => <div>Number: {props.data}</div>,
  Json: props => <div>JSON: {JSON.stringify(props.data)}</div>
}});

it('should display a number if visualization is number', () => {
  const node = mount(<ReportView data={{
    visualization: 'number',
    result: {number: 1234}
  }} />);

  expect(node).toIncludeText('Number: 1234');
});

it('should display a json if visualization is json', () => {
  const node = mount(<ReportView data={{
    visualization: 'json',
    result: {number: 1234}
  }} />);

  expect(node).toIncludeText('JSON');
});
