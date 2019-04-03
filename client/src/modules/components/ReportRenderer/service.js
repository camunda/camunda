/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatters} from 'services';

export function isEmpty(str) {
  return !str || 0 === str.length;
}

export function getFormatter(viewProperty) {
  switch (viewProperty) {
    case 'frequency':
      return formatters.frequency;
    case 'duration':
    case 'idleDuration':
    case 'workDuration':
      return formatters.duration;
    default:
      return v => v;
  }
}

export function processResult({
  data: {
    view,
    configuration: {aggregationType}
  },
  result
}) {
  if (view.property.toLowerCase().includes('duration')) {
    if (result.type === 'durationNumber') {
      return {...result, data: result.data[aggregationType]};
    }
    if (result.type === 'durationMap') {
      const newData = Object.entries(result.data).reduce((data, [key, value]) => {
        data[key] = value[aggregationType];
        return data;
      }, {});
      return {...result, data: newData};
    }
  }
  return result;
}
