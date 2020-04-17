/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import moment from 'moment';
import {numberParser} from 'services';

export function convertFilterToState(filter) {
  const {type, start, end} = filter;
  let state;

  if (type === 'fixed') {
    state = {type: 'fixed', startDate: moment(start), endDate: moment(end), pickerValid: true};
  } else {
    const {value, unit} = start;
    if (type === 'relative') {
      state = {
        type: 'custom',
        unit: unit,
        customNum: value,
      };
    } else if (value === 0) {
      const type = unit === 'days' ? 'today' : 'this';
      state = {type, unit};
    } else if (value === 1) {
      const type = unit === 'days' ? 'yesterday' : 'last';
      state = {type, unit};
    }
  }

  return state;
}

export function convertStateToFilter({type, unit, customNum, startDate, endDate}) {
  let filter = {type: 'rolling', end: null};
  switch (type) {
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
        unit: unit,
      };
      break;
    case 'fixed':
      filter = {
        type: 'fixed',
        start: startDate.startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
        end: endDate.endOf('day').format('YYYY-MM-DDTHH:mm:ss'),
      };
      break;
    case 'custom':
      filter = {
        type: 'relative',
        start: {value: customNum, unit},
        end: null,
      };
      break;
    default:
      return null;
  }
  return filter;
}

export function isValid({type, unit, customNum, valid}) {
  switch (type) {
    case 'today':
    case 'yesterday':
      return true;
    case 'this':
    case 'last':
      return unit;
    case 'fixed':
      return valid;
    case 'custom':
      return numberParser.isPostiveInt(customNum);
    default:
      return false;
  }
}
