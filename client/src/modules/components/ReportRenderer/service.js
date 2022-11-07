/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {formatters} from 'services';

export function isEmpty(str) {
  return !str || 0 === str.length;
}

export const formatValue = (value, measure, precision) => {
  let formatter;

  if (typeof measure === 'object') {
    formatter = formatters.frequency;
  }

  switch (measure) {
    case 'frequency':
      formatter = formatters.frequency;
      break;
    case 'duration':
      formatter = formatters.duration;
      break;
    case 'percentage':
      formatter = formatters.percentage;
      break;
    default:
      formatter = (v) => v;
      break;
  }

  return formatter(value, precision);
};
