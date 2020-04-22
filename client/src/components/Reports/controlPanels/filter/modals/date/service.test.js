/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {convertStateToFilter, convertFilterToState} from './service';
import moment from 'moment';

it('create correct filter from state object', () => {
  const filter1 = convertStateToFilter({
    type: 'today',
    unit: 'days',
  });

  expect(filter1.start).toEqual({
    value: 0,
    unit: 'days',
  });

  const filter2 = convertStateToFilter({
    type: 'this',
    unit: 'weeks',
  });

  expect(filter2.start).toEqual({
    value: 0,
    unit: 'weeks',
  });

  const filter3 = convertStateToFilter({
    type: 'fixed',
    startDate: moment('2015-01-20T00:00:00'),
    endDate: moment('2019-05-11T00:00:00'),
  });

  expect(filter3).toEqual({
    type: 'fixed',
    start: '2015-01-20T00:00:00',
    end: '2019-05-11T23:59:59',
  });
});

it('should not crash if start or endDate is not set for fixed filter', () => {
  expect(convertStateToFilter({type: 'fixed'})).toEqual({
    type: 'fixed',
    start: undefined,
    end: undefined,
  });
});

it('should default to an empty fixed date filter if no type is set', () => {
  expect(convertStateToFilter({type: ''})).toEqual({
    type: 'fixed',
    start: null,
    end: null,
  });
});

it('should not crash when parsing a fixed date filter without values', () => {
  expect(convertFilterToState({type: 'fixed', start: null, end: null})).toEqual({
    type: 'fixed',
    startDate: null,
    endDate: null,
    valid: null,
  });
});
