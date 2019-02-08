import React from 'react';
import {shallow} from 'enzyme';

import TableConfig from './TableConfig';

it('should render ColumnSelection for raw data views', () => {
  const node = shallow(
    <TableConfig
      report={{combined: false, data: {view: {operation: 'rawData'}, configuration: {}}}}
    />
  );

  expect(node.find('ColumnSelection')).toBePresent();
});

it('should render relative abolute selection for count views', () => {
  const node = shallow(
    <TableConfig
      report={{combined: false, data: {view: {operation: 'count'}, configuration: {}}}}
    />
  );

  expect(node.find('RelativeAbsoluteSelection')).toBePresent();
});

it('should not display show instance count for combined reports', () => {
  const node = shallow(
    <TableConfig
      report={{combined: true, result: {test: {data: {view: {property: 'frequency'}}}}}}
    />
  );

  expect(node.find('ShowInstanceCount')).not.toBePresent();
});
