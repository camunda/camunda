/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import NodeStatus from './NodeStatus';

it('should render nothing if the report is not grouped by flow nodes', () => {
  const node = shallow(<NodeStatus report={{data: {groupBy: {type: 'none'}}}} />);

  expect(node).toEqual({});
});

it('should render a status selection for flow node reports', () => {
  const node = shallow(
    <NodeStatus
      report={{
        data: {groupBy: {type: 'flowNodes'}, configuration: {flowNodeExecutionState: 'completed'}},
      }}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should render a status selection reports grouped by assignee', () => {
  const node = shallow(
    <NodeStatus
      report={{
        data: {groupBy: {type: 'assignee'}, configuration: {flowNodeExecutionState: 'completed'}},
      }}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should not render node status selection for incident reports', () => {
  const node = shallow(
    <NodeStatus
      report={{
        data: {
          view: {entity: 'incident'},
          groupBy: {type: 'flowNodes'},
        },
      }}
    />
  );

  expect(node.find('.NodeStatus')).not.toExist();
});

it('should not crash when no groupBy is set (e.g. for combined reports)', () => {
  shallow(<NodeStatus report={{data: {}}} />);
});

it('should reevaluate the report when changing execution status', () => {
  const spy = jest.fn();

  const node = shallow(
    <NodeStatus
      report={{
        data: {groupBy: {type: 'flowNodes'}, configuration: {flowNodeExecutionState: 'completed'}},
      }}
      onChange={spy}
    />
  );

  node.find('Select').simulate('change', 'running');

  expect(spy).toHaveBeenCalledWith({flowNodeExecutionState: {$set: 'running'}}, true);
});
