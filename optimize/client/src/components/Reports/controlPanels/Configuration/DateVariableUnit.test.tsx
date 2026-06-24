/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import DateVariableUnit from './DateVariableUnit';

const report = {
  data: {
    groupBy: {type: 'variable', value: {type: 'Date'}},
    configuration: {groupByDateVariableUnit: 'automatic', distributedBy: {}},
  },
};

const props = {
  configuration: report.data.configuration,
  groupBy: report.data.groupBy,
  onChange: jest.fn(),
};

it('should render a unit selection for Date variables', () => {
  const node = shallow(<DateVariableUnit {...props} />);

  const selectOptions = node.find('Option');

  expect(selectOptions.at(0).prop('label')).toEqual('automatic');
  expect(selectOptions.at(1).prop('label')).toEqual('hours');
  expect(selectOptions.at(2).prop('label')).toEqual('days');
  expect(selectOptions.at(3).prop('label')).toEqual('weeks');
  expect(selectOptions.at(4).prop('label')).toEqual('months');
  expect(selectOptions.at(5).prop('label')).toEqual('years');
});

it('should render nothing if the current variable is not Date', () => {
  const node = shallow(
    <DateVariableUnit {...props} groupBy={{type: 'variable', value: {type: 'Number'}}} />
  );

  expect(node.find('.DateVariableUnit')).not.toExist();
});

it('should render a unit selection for distributed by date variable', () => {
  const node = shallow(
    <DateVariableUnit
      {...props}
      distributedBy={{
        type: 'variable',
        value: {type: 'Date'},
      }}
      configuration={{
        groupByDateVariableUnit: 'automatic',
      }}
    />
  );

  expect(node.find('.DateVariableUnit')).toExist();
});

it('should reevaluate the report when changing the unit', () => {
  const spy = jest.fn();

  const node = shallow(<DateVariableUnit {...props} onChange={spy} />);

  node.find('Select').simulate('change', 'month');

  expect(spy).toHaveBeenCalledWith({groupByDateVariableUnit: {$set: 'month'}}, true);
});
