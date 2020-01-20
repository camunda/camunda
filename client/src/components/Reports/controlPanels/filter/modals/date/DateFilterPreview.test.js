/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import DateFilterPreview from './DateFilterPreview';
import {shallow} from 'enzyme';

it('should create Today/Yesterday preview', () => {
  const filter = {
    type: 'rolling',
    start: {
      value: 0,
      unit: 'days'
    }
  };

  const node = shallow(<DateFilterPreview filterType="startDate" filter={filter} />);

  expect(node).toMatchSnapshot();
});

it('should create correct last... with custom preview', () => {
  const filter = {
    type: 'relative',
    start: {
      value: 5,
      unit: 'months'
    }
  };

  const node = shallow(<DateFilterPreview filterType="endDate" filter={filter} />);

  expect(node).toMatchSnapshot();
});

it('should create correct fixed date preview', () => {
  const filter = {
    type: 'fixed',
    start: '2015-01-20T00:00:00',
    end: '2019-05-11T00:00:00'
  };

  const node = shallow(<DateFilterPreview filterType="startDate" filter={filter} />);

  expect(node).toMatchSnapshot();
});
