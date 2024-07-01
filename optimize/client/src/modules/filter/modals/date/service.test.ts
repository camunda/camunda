/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {parseISO} from 'date-fns';

import {format, BACKEND_DATE_FORMAT} from 'dates';

import {convertStateToFilter, convertFilterToState} from './service';

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
    type: 'between',
    startDate: parseISO('2015-01-20T00:00:00'),
    endDate: parseISO('2019-05-11T00:00:00'),
  });

  expect(filter3).toEqual({
    type: 'fixed',
    start: format(parseISO('2015-01-20T00:00:00'), BACKEND_DATE_FORMAT),
    end: format(parseISO('2019-05-11T23:59:59.999'), BACKEND_DATE_FORMAT),
  });

  const filter4 = convertStateToFilter({
    type: 'after',
    startDate: parseISO('2015-01-20T00:00:00'),
    endDate: null,
  });

  expect(filter4).toEqual({
    type: 'fixed',
    start: format(parseISO('2015-01-20T00:00:00'), BACKEND_DATE_FORMAT),
    end: null,
  });

  const filter5 = convertStateToFilter({
    type: 'before',
    startDate: null,
    endDate: parseISO('2019-05-11T00:00:00'),
  });

  expect(filter5).toEqual({
    type: 'fixed',
    start: null,
    end: format(parseISO('2019-05-11T23:59:59.999'), BACKEND_DATE_FORMAT),
  });
});

it('should not crash if start or endDate is not set for fixed filter', () => {
  expect(convertStateToFilter({type: 'between'})).toEqual({
    type: 'fixed',
    start: null,
    end: null,
  });
});

it('should default to an empty date filter if no type is set', () => {
  expect(convertStateToFilter({type: ''})).toEqual({
    type: '',
    start: null,
    end: null,
  });
});

it('should not crash when parsing a fixed date filter without values', () => {
  expect(convertFilterToState({type: 'fixed', start: null, end: null})).toEqual({
    type: '',
    unit: '',
    startDate: null,
    endDate: null,
    customNum: '',
  });
});
