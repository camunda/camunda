/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {Select} from 'components';

import DistributedBy from './DistributedBy';

it('should change the visualization if it is incompatible with the new configuration', () => {
  const spy = jest.fn();
  const node = shallow(
    <DistributedBy
      report={{data: {visualization: 'line', groupBy: {type: 'assignee'}, configuration: {}}}}
      onChange={spy}
    />
  );

  node.find(Select).prop('onChange')('userTask');

  expect(spy).toHaveBeenCalledWith(
    {
      configuration: {distributedBy: {$set: 'userTask'}},
      visualization: {$set: 'bar'}
    },
    true
  );
});
