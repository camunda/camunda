/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import UserTaskDurationTime from './UserTaskDurationTime';

it('should render nothing if the current result does is no duration or no user task', () => {
  const node = shallow(
    <UserTaskDurationTime report={{data: {view: {entity: 'flowNodes', property: 'duration'}}}} />
  );

  expect(node).toMatchSnapshot();

  node.setProps({report: {data: {view: {entity: 'userTask', property: 'frequency'}}}});

  expect(node).toMatchSnapshot();
});

it('should render an duration type selection for user task duration reports', () => {
  const node = shallow(
    <UserTaskDurationTime
      report={{
        data: {
          configuration: {userTaskDurationTime: 'total'},
          view: {entity: 'userTask', property: 'duration'},
        },
      }}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should reevaluate the report when changing the duration type', () => {
  const spy = jest.fn();

  const node = shallow(
    <UserTaskDurationTime
      report={{
        data: {
          configuration: {userTaskDurationTime: 'total'},
          view: {entity: 'userTask', property: 'duration'},
        },
      }}
      onChange={spy}
    />
  );

  node.find('Select').simulate('change', 'work');

  expect(spy).toHaveBeenCalledWith({configuration: {userTaskDurationTime: {$set: 'work'}}}, true);
});
