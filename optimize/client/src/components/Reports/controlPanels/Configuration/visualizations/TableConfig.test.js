/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import TableConfig from './TableConfig';

it('should render ColumnSelection for raw data views', () => {
  const node = shallow(
    <TableConfig
      report={{
        data: {view: {properties: ['rawData']}, groupBy: {type: 'none'}, configuration: {}},
      }}
    />
  );

  expect(node.find('ColumnSelection')).toExist();
});

it('should render relative abolute selection for count views', () => {
  const node = shallow(
    <TableConfig
      report={{
        data: {view: {properties: ['frequency']}, groupBy: {type: 'startDate'}, configuration: {}},
      }}
    />
  );

  expect(node.find('RelativeAbsoluteSelection')).toExist();
});

it('should render GradientBarsSwitch for group by rules', () => {
  const node = shallow(
    <TableConfig
      report={{
        data: {
          view: {properties: ['frequency']},
          groupBy: {type: 'matchedRule'},
          configuration: {},
        },
      }}
    />
  );

  expect(node.find('GradientBarsSwitch')).toExist();
});

it('should disable the column selection component when automatic preview is off', () => {
  const node = shallow(
    <TableConfig
      report={{
        data: {
          configuration: {tableColumns: {columnOrder: ['test']}},
          groupBy: {type: 'matchedRule'},
          view: {properties: ['rawData']},
        },
      }}
      autoPreviewDisabled
    />
  );

  expect(node.find('ColumnSelection').prop('disabled')).toBe(true);
});
