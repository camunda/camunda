import React from 'react';
import {shallow} from 'enzyme';

import TableConfig from './TableConfig';

it('should render ColumnSelection for raw data views', () => {
  const node = shallow(
    <TableConfig
      report={{
        combined: false,
        data: {view: {operation: 'rawData'}, groupBy: {type: 'none'}, configuration: {}}
      }}
    />
  );

  expect(node.find('ColumnSelection')).toBePresent();
});

it('should render relative abolute selection for count views', () => {
  const node = shallow(
    <TableConfig
      report={{
        combined: false,
        data: {view: {operation: 'count'}, groupBy: {type: 'startDate'}, configuration: {}}
      }}
    />
  );

  expect(node.find('RelativeAbsoluteSelection')).toBePresent();
});

it('should render GradientBarsSwitch for group by rules', () => {
  const node = shallow(
    <TableConfig
      report={{
        combined: false,
        data: {view: {operation: 'count'}, groupBy: {type: 'matchedRule'}, configuration: {}}
      }}
    />
  );

  expect(node.find('GradientBarsSwitch')).toBePresent();
});

it('should not display show instance count for combined reports', () => {
  const node = shallow(
    <TableConfig
      report={{combined: true, result: {test: {data: {view: {property: 'frequency'}}}}}}
    />
  );

  expect(node.find('ShowInstanceCount')).not.toBePresent();
});
