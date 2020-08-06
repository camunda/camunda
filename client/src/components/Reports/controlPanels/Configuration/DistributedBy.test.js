/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {Select} from 'components';

import DistributedBy from './DistributedBy';

const data = {
  visualization: 'heat',
  view: {entity: 'userTask'},
  configuration: {},
  groupBy: {type: 'userTasks'},
};

it('should match snapshot', () => {
  const node = shallow(<DistributedBy report={{data}} />);

  expect(node).toMatchSnapshot();
});

it('should change the visualization if it is incompatible with the new configuration', () => {
  const spy = jest.fn();
  const node = shallow(
    <DistributedBy
      report={{
        data: {
          ...data,
          groupBy: {type: 'assignee'},
        },
      }}
      onChange={spy}
    />
  );

  expect(node.find({value: 'userTask'})).toExist();

  node.find(Select).prop('onChange')('userTask');

  expect(spy).toHaveBeenCalledWith(
    {
      configuration: {distributedBy: {$set: 'userTask'}},
      visualization: {$set: 'bar'},
    },
    true
  );
});

it('should offer the correct options based on the group by type', () => {
  const spy = jest.fn();
  let node = shallow(
    <DistributedBy
      report={{
        data: {
          ...data,
          groupBy: {type: 'userTasks'},
        },
      }}
      onChange={spy}
    />
  );

  expect(node.find({value: 'assignee'})).toExist();
  expect(node.find({value: 'candidateGroup'})).toExist();
  expect(node.find({value: 'userTask'})).not.toExist();

  node.setProps({
    report: {
      data: {
        ...data,
        groupBy: {type: 'startDate'},
      },
    },
  });

  expect(node.find({value: 'assignee'})).toExist();
  expect(node.find({value: 'candidateGroup'})).toExist();
  expect(node.find({value: 'userTask'})).toExist();

  node.setProps({
    report: {
      data: {
        ...data,
        groupBy: {type: 'assignee'},
      },
    },
  });

  expect(node.find({value: 'assignee'})).not.toExist();
  expect(node.find({value: 'candidateGroup'})).not.toExist();
  expect(node.find({value: 'userTask'})).toExist();
});
