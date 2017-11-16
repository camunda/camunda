import React from 'react';

import FilterList from './FilterList';

import {mount} from 'enzyme';

jest.mock('components', () => {return {
  Button: props => <button {...props}>{props.children}</button>
}});

it('should render an unordered list', () => {
  const node = mount(<FilterList data={[]} />);

  expect(node.find('ul')).toBePresent();
});

it('should display a single filter entry for two related start dates', () => {
  const startDate = '2017-11-16T00:00:00';
  const endDate = '2017-11-26T23:59:59';
  const data = [{
    type: 'date',
    data: {
      operator: '>=',
      value: startDate
    }
  }, {
    type: 'date',
    data: {
      operator: '<=',
      value: endDate
    }
  }];

  const node = mount(<FilterList data={data} />);

  expect(node.find('li').length).toBe(1);
});

it('should display "and" between filter entries', () => {
  const data = ['Filter 1', 'Filter 2'];

  const node = mount(<FilterList data={data} />);

  expect(node).toIncludeText('and');
});

it('should remove both date filter parts for a date filter entry', () => {
  const startDate = '2017-11-16T00:00:00';
  const endDate = '2017-11-26T23:59:59';
  const data = [{
    type: 'date',
    data: {
      operator: '>=',
      value: startDate
    }
  }, {
    type: 'date',
    data: {
      operator: '<=',
      value: endDate
    }
  }];
  const spy = jest.fn();

  const node = mount(<FilterList data={data} deleteFilter={spy} />);

  node.find('button').simulate('click');

  expect(spy.mock.calls[0].length).toBe(2);
  expect(spy.mock.calls[0][0]).toBe(data[0]);
  expect(spy.mock.calls[0][1]).toBe(data[1]);
});
