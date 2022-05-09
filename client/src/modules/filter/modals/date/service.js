/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {parseISO, startOfDay, endOfDay} from 'date-fns';

import {format, BACKEND_DATE_FORMAT} from 'dates';
import {numberParser} from 'services';

export function convertFilterToState(filter) {
  const {type, start, end, ...commonProps} = filter;
  let state = {};

  if (type === 'fixed') {
    if (!start && !end) {
      state = {type: ''};
    } else {
      state = {
        type: getFixedType(start, end),
        startDate: start ? parseISO(start) : null,
        endDate: end ? parseISO(end) : null,
        valid: true,
      };
    }
  } else {
    const {value, unit} = start || {};
    if (type === 'rolling') {
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

  return {...state, ...commonProps};
}

export function convertStateToFilter({
  type,
  unit,
  customNum,
  startDate,
  endDate,
  valid,
  ...commonProps
}) {
  let filter = {type: 'relative', end: null};
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
    case 'between':
    case 'before':
    case 'after':
      filter = {
        type: 'fixed',
        start: startDate ? format(startOfDay(startDate), BACKEND_DATE_FORMAT) : null,
        end: endDate ? format(endOfDay(endDate), BACKEND_DATE_FORMAT) : null,
      };
      break;
    case 'custom':
      filter = {
        type: 'rolling',
        start: {value: customNum, unit},
        end: null,
      };
      break;
    default:
      filter = {
        type: '',
        start: null,
        end: null,
      };
  }
  return {...filter, ...commonProps};
}

export function isValid({
  type,
  unit,
  customNum,
  valid,
  includeUndefined,
  excludeUndefined,
  applyTo,
}) {
  if (applyTo?.length === 0) {
    return false;
  }
  switch (type) {
    case 'today':
    case 'yesterday':
      return true;
    case 'this':
    case 'last':
      return unit;
    case 'between':
    case 'before':
    case 'after':
      return valid;
    case 'custom':
      return numberParser.isPositiveInt(customNum);
    default:
      return includeUndefined || excludeUndefined;
  }
}

function getFixedType(start, end) {
  if (start && end) {
    return 'between';
  } else if (start) {
    return 'after';
  } else {
    return 'before';
  }
}
