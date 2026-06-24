/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import EmptyState from './EmptyState';
import {OptimizeDashboard} from 'icons';

it('should render properly', () => {
  const node = shallow(
    <EmptyState
      title="some title"
      description="here is a description"
      icon={<OptimizeDashboard />}
    />
  );

  expect(node.find('.title')).toHaveText('some title');
  expect(node.find('.description')).toHaveText('here is a description');
  expect(node.find('svg')).toBeDefined();
});

it('should render actions', () => {
  const spy = jest.fn();
  const node = shallow(
    <EmptyState
      title="test"
      description="test description"
      actions={<button onClick={spy}>Click Me</button>}
    />
  );

  node.find('button').simulate('click');

  expect(spy).toHaveBeenCalled();
});
