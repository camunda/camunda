/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import moment from 'moment';

export function convertFilterToState(filter) {
  const {type, start, end} = filter;
  let state;

  if (type === 'fixed') {
    state = {dateType: 'fixed', startDate: moment(start), endDate: moment(end)};
  } else {
    const {value, unit} = start;
    if (type === 'relative') {
      state = {
        dateType: 'custom',
        unit: unit,
        customNum: value
      };
    } else if (value === 0) {
      const dateType = unit === 'days' ? 'today' : 'this';
      state = {dateType, unit};
    } else if (value === 1) {
      const dateType = unit === 'days' ? 'yesterday' : 'last';
      state = {dateType, unit};
    }
  }

  return state;
}

export function convertStateToFilter({dateType, unit, customNum, startDate, endDate}) {
  let filter = {type: 'rolling', end: null};
  switch (dateType) {
    case 'today':
      filter.start = {value: 0, unit: 'days'};
      break;
    case 'yesterday':
      filter.start = {value: 1, unit: 'days'};
      break;
    case 'this':
      filter.start = {value: 0, unit};
      break;
    case 'last':
      filter.start = {
        value: 1,
        unit: unit
      };
      break;
    case 'fixed':
      filter = {
        type: 'fixed',
        start: startDate.startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
        end: endDate.endOf('day').format('YYYY-MM-DDTHH:mm:ss')
      };
      break;
    case 'custom':
      filter = {
        type: 'relative',
        start: {value: customNum, unit},
        end: null
      };
      break;
    default:
      return null;
  }
  return filter;
}
