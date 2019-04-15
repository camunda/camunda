/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatters} from 'services';

export function getRelativeValue(data, total) {
  if (data === null) {
    return '';
  }
  return Math.round((data / total) * 1000) / 10 + '%';
}

export function uniteResults(results, allKeys) {
  const unitedResults = [];
  results.forEach(result => {
    const resultObj = formatters.objectifyResult(result);
    const newResult = [];
    allKeys.forEach(key => {
      if (typeof resultObj[key] === 'undefined') {
        newResult.push({key, value: null});
      } else {
        newResult.push({key, value: resultObj[key]});
      }
    });
    unitedResults.push(newResult);
  });

  return unitedResults;
}
