/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import GoalResult from './GoalResult';

const goals = [
  {
    type: 'targetDuration',
    percentile: '25',
    value: '1',
    unit: 'weeks',
  },
  {
    type: 'slaDuration',
    percentile: '95',
    value: '5',
    unit: 'days',
  },
];

const results = [
  {
    type: 'targetDuration',
    value: 14686632633,
    successful: true,
  },
  {
    type: 'slaDuration',
    value: 14686636515,
    successful: false,
  },
];

it('should display NoDataNotice if results prop is empty or has null values', () => {
  const node = shallow(<GoalResult durationGoals={{goals, results}} />);

  expect(node.find('.goal')).toExist();
  expect(node.find('NoDataNotice')).not.toExist();

  node.setProps({
    durationGoals: {
      goals,
      results: [],
    },
  });

  expect(node.find('.goal')).not.toExist();
  expect(node.find('NoDataNotice')).toExist();

  node.setProps({
    durationGoals: {
      goals,
      results: [
        {
          type: 'targetDuration',
          value: null,
          successful: null,
        },
        {
          type: 'slaDuration',
          value: null,
          successful: null,
        },
      ],
    },
  });

  expect(node.find('.goal')).not.toExist();
  expect(node.find('NoDataNotice')).toExist();
});
