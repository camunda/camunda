import React from 'react';
import {shallow} from 'enzyme';

import Table from './Table';

it('should render ColumnSelection for raw data views', () => {
  const node = shallow(<Table report={{data: {view: {operation: 'rawData'}}}} />);

  expect(node.find('ColumnSelection')).toBePresent();
});

it('should render relative abolute selection for count views', () => {
  const node = shallow(<Table report={{data: {view: {operation: 'count'}}}} />);

  expect(node.find('RelativeAbsoluteSelection')).toBePresent();
});

it('should reset to defaults when the operation changes', () => {
  expect(
    Table.onUpdate(
      {report: {data: {view: {operation: 'prevOp'}}}},
      {report: {data: {view: {operation: 'newOp'}}}}
    )
  ).toEqual(Table.defaults);
});
