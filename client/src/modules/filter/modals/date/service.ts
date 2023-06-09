/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {parseISO, startOfDay, endOfDay} from 'date-fns';

import {format, BACKEND_DATE_FORMAT} from 'dates';
import {numberParser} from 'services';
import {FilterState, Filter, BetweenFilterState, BeforeFilterState, AfterFilterState} from 'types';

export function convertFilterToState(filter: Partial<Filter>): FilterState {
  const {type, start, end, ...commonProps} = filter;
  let state: FilterState = {
    type: '',
    unit: '',
    startDate: null,
    endDate: null,
    customNum: '',
  };

  if (type === 'fixed') {
    if (!start && !end) {
      state = {...state, type: ''};
    } else {
      state = {
        ...state,
        ...getFixedType(start, end),
        valid: true,
      };
    }
  } else {
    const {value = '', unit = ''} = start && typeof start === 'object' ? start : {};
    if (type === 'rolling') {
      state = {
        ...state,
        type: 'custom',
        unit,
        customNum: value?.toString(),
      };
    } else if (value === 0) {
      const type = unit === 'days' ? 'today' : 'this';
      state = {...state, type, unit} as FilterState;
    } else if (value === 1) {
      const type = unit === 'days' ? 'yesterday' : 'last';
      state = {...state, type, unit} as FilterState;
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
}: Partial<FilterState>): Filter {
  let filter: Filter = {type: 'relative', end: null, start: null};
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
        unit,
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
}: Partial<FilterState>): boolean {
  if (applyTo?.length === 0) {
    return false;
  }
  switch (type) {
    case 'today':
    case 'yesterday':
      return true;
    case 'this':
    case 'last':
      return !!unit;
    case 'between':
    case 'before':
    case 'after':
      return !!valid;
    case 'custom':
      return customNum ? numberParser.isPositiveInt(customNum) : false;
    default:
      return !!(includeUndefined || excludeUndefined);
  }
}

function getFixedType(
  start?: string | null,
  end?: string | null
): Partial<BetweenFilterState | BeforeFilterState | AfterFilterState> {
  if (start && end) {
    return {
      type: 'between',
      startDate: parseISO(start),
      endDate: parseISO(end),
    };
  } else if (start) {
    return {
      type: 'after',
      startDate: parseISO(start),
      endDate: null,
    };
  } else {
    return {
      type: 'before',
      startDate: null,
      endDate: parseISO(end!),
    };
  }
}
