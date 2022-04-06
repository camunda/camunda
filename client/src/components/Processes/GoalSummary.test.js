/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Icon} from 'components';

import GoalSummary from './GoalSummary';

it('should not display the summary if no goals are defined', () => {
  const node = shallow(<GoalSummary goals={[]} />);

  expect(node.isEmptyRender()).toBe(true);
});

it('should display no data if all goals has no value', () => {
  const node = shallow(<GoalSummary goals={[{value: null}, {value: null}]} />);

  expect(node).toIncludeText('No Data');
});

it('should display single indicator if all goals succeeded or failed', () => {
  const node = shallow(
    <GoalSummary
      goals={[
        {type: 'targetDuration', value: 14594816267, successful: true},
        {type: 'slaDuration', value: 14594816267, successful: true},
      ]}
    />
  );

  expect(node.find(Icon).length).toBe(1);
  expect(node.find(Icon).prop('className')).toBe('success');
});

it('should display two indicators if one goal succeeds and the other fails', () => {
  const node = shallow(
    <GoalSummary
      goals={[
        {type: 'targetDuration', value: 14594816267, successful: true},
        {type: 'slaDuration', value: 14594816267, successful: false},
      ]}
    />
  );

  expect(node.find(Icon).length).toBe(2);
  expect(node.find(Icon).at(0).prop('className')).toBe('success');
  expect(node.find(Icon).at(1).prop('className')).toBe('error');
});
