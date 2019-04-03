/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import AggregationType from './AggregationType';

it('should render nothing if the current result does is no duration', () => {
  const node = shallow(<AggregationType report={{result: {type: 'rawData'}}} />);

  expect(node).toMatchSnapshot();
});

it('should render an aggregation selection for duration reports', () => {
  const node = shallow(
    <AggregationType
      report={{result: {type: 'durationMap'}, data: {configuration: {aggregationType: 'median'}}}}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should not crash when no resultType is set (e.g. for combined reports)', () => {
  shallow(<AggregationType report={{result: {}}} />);
});
