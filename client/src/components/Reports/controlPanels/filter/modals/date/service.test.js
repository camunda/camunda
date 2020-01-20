/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {convertStateToFilter} from './service';
import moment from 'moment';

it('create correct filter from state object', () => {
  const filter1 = convertStateToFilter({
    dateType: 'today',
    unit: 'days'
  });

  expect(filter1.start).toEqual({
    value: 0,
    unit: 'days'
  });

  const filter2 = convertStateToFilter({
    dateType: 'this',
    unit: 'weeks'
  });

  expect(filter2.start).toEqual({
    value: 0,
    unit: 'weeks'
  });

  const filter3 = convertStateToFilter({
    dateType: 'fixed',
    startDate: moment('2015-01-20T00:00:00'),
    endDate: moment('2019-05-11T00:00:00')
  });

  expect(filter3).toEqual({
    type: 'fixed',
    start: '2015-01-20T00:00:00',
    end: '2019-05-11T23:59:59'
  });
});
