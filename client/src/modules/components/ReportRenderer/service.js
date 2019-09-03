/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatters} from 'services';
import {t} from 'translation';

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
  const formattedResult = formatResult(filteredResult, data);
  if (data.view.property.toLowerCase().includes('duration')) {
    if (formattedResult.type === 'durationNumber') {
      return {...formattedResult, data: formattedResult.data};
    }
    if (formattedResult.type === 'durationMap') {
      const newData = formattedResult.data.map(entry => {
        return {...entry, value: entry.value};
      });

      return {...formattedResult, data: newData};
    }
  }
  return formattedResult;
}

function filterResult(result, {groupBy: {type}, configuration: {hiddenNodes}}) {
  if (type === 'flowNodes') {
    return {
      ...result,
      data: result.data.filter(
        ({key}) => !(hiddenNodes.active ? hiddenNodes.keys : []).includes(key)
      )
    };
  }

  return result;
}

function formatResult(result, {groupBy: {type}}) {
  if (type === 'variable') {
    return {
      ...result,
      data: result.data.map(row =>
        row.key === 'missing' ? {...row, label: t('report.missingVariableValue')} : row
      )
    };
  }

  return result;
}
