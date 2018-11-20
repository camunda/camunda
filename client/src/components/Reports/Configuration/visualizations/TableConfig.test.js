import React from 'react';
import {shallow} from 'enzyme';

import TableConfig from './TableConfig';

it('should render ColumnSelection for raw data views', () => {
  const node = shallow(<TableConfig report={{data: {view: {operation: 'rawData'}}}} />);

  expect(node.find('ColumnSelection')).toBePresent();
});

it('should render relative abolute selection for count views', () => {
  const node = shallow(<TableConfig report={{data: {view: {operation: 'count'}}}} />);

  expect(node.find('RelativeAbsoluteSelection')).toBePresent();
});

it('should reset to defaults when the operation changes', () => {
  expect(
    TableConfig.onUpdate(
      {report: {data: {view: {operation: 'prevOp'}}}},
      {report: {data: {view: {operation: 'newOp'}}}}
    )
  ).toEqual(TableConfig.defaults);
});

it('should reset to defaults when visualization type changes', () => {
  expect(
    TableConfig.onUpdate(
      {type: 'prev', report: {data: {view: {operation: 'test'}}}},
      {type: 'new', report: {data: {view: {operation: 'test'}}}}
    )
  ).toEqual(TableConfig.defaults);
});
