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
  resultType,
  result
}) {
  if (view.property.toLowerCase().includes('duration')) {
    if (resultType === 'durationNumber') {
      return result[aggregationType];
    }
    if (resultType === 'durationMap') {
      return Object.entries(result).reduce((result, [key, value]) => {
        result[key] = value[aggregationType];
        return result;
      }, {});
    }
  }
  return result;
}
