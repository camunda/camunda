/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import DateVariableUnit from './DateVariableUnit';

const report = {
  data: {
    groupBy: {type: 'variable', value: {type: 'Date'}},
    configuration: {groupByDateVariableUnit: 'automatic', distributedBy: {}},
  },
};

it('should render a unit selection for Date variables', () => {
  const node = shallow(<DateVariableUnit report={report} />);

  expect(node).toMatchSnapshot();
});

it('should render nothing if the current variable is not Date', () => {
  const node = shallow(
    <DateVariableUnit
      report={{
        data: {
          ...report.data,
          groupBy: {type: 'variable', value: {type: 'Number'}},
        },
      }}
    />
  );

  expect(node.find('.DateVariableUnit')).not.toExist();
});

it('should render a unit selection for distributed by date variable', () => {
  const node = shallow(
    <DateVariableUnit
      report={{
        data: {
          distributedBy: {
            type: 'variable',
            value: {type: 'Date'},
          },
          configuration: {
            groupByDateVariableUnit: 'automatic',
          },
        },
      }}
    />
  );

  expect(node.find('.DateVariableUnit')).toExist();
});

it('should reevaluate the report when changing the unit', () => {
  const spy = jest.fn();

  const node = shallow(<DateVariableUnit report={report} onChange={spy} />);

  node.find('Select').simulate('change', 'month');

  expect(spy).toHaveBeenCalledWith({groupByDateVariableUnit: {$set: 'month'}}, true);
});
