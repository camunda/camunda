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

export function processResult({data, result}) {
  const filteredResult = filterResult(result, data);
  if (data.view.property.toLowerCase().includes('duration')) {
    if (filteredResult.type === 'durationNumber') {
      return {...filteredResult, data: filteredResult.data};
    }
    if (filteredResult.type === 'durationMap') {
      const newData = filteredResult.data.map(entry => {
        return {...entry, value: entry.value};
      });

      return {...filteredResult, data: newData};
    }
  }
  return filteredResult;
}

function filterResult(result, {groupBy: {type}, configuration: {hiddenNodes}}) {
  if (type === 'flowNodes') {
    return {...result, data: result.data.filter(({key}) => !(hiddenNodes || []).includes(key))};
  }

  return result;
}
