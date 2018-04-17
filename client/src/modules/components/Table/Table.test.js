import React from 'react';
import {mount} from 'enzyme';

import Table from './Table';
import ReactTable from 'react-table';

jest.mock('react-router-dom', () => {
  return {
    Link: ({children}) => {
      return <a>{children}</a>;
    }
  };
});

jest.mock('react-table', () => ({children}) =>
  children({rowMinWidth: 500}, () => (
    <div>
      <div className="rt-thead">
        <div className="rt-tr" />
      </div>
      <div className="rt-tbody" />
    </div>
  ))
);

jest.mock('components', () => {
  return {
    Button: props => <button {...props}>{props.children}</button>
  };
});

function generateData(amount) {
  const arr = [];
  for (let i = 0; i < amount; i++) {
    arr.push(['' + i]);
  }
  return arr;
}

it('should render without crashing', () => {
  mount(<Table {...{head: [], body: [], foot: []}} />);
});

it('shoud correctly format header', () => {
  const result = Table.formatColumns(['x', 'y', 'z']);

  expect(result).toEqual([
    {Header: 'x', accessor: 'x', minWidth: 100},
    {Header: 'y', accessor: 'y', minWidth: 100},
    {Header: 'z', accessor: 'z', minWidth: 100}
  ]);
});

it('should correctly format multi-level header', () => {
  const result = Table.formatColumns(['x', {label: 'a', columns: ['i', 'j']}]);

  expect(result).toEqual([
    {Header: 'x', accessor: 'x', minWidth: 100},
    {
      Header: 'a',
      columns: [
        {Header: 'i', accessor: 'ai', minWidth: 100},
        {Header: 'j', accessor: 'aj', minWidth: 100}
      ]
    }
  ]);
});

it('shoud correctly format body', () => {
  const result = Table.formatData(['Header 1', 'Header 2', 'Header 3'], [['a', 'b', 'c']]);

  expect(result).toEqual([{header_1: 'a', header_2: 'b', header_3: 'c'}]);
});

it('should show pagination if data contains more than 50 rows', () => {
  const node = mount(<Table {...{head: ['a'], body: generateData(51), foot: []}} />);

  expect(node.find(ReactTable)).toHaveProp('showPagination', true);
});

it('should not show pagination if data contains less than or equal to 50 rows', () => {
  const node = mount(<Table {...{head: ['a'], body: generateData(50), foot: []}} />);

  expect(node.find(ReactTable)).toHaveProp('showPagination', false);
});
