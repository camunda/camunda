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

export function getFormatter(viewProperty) {
  if (typeof viewProperty === 'object') {
    // can only happen for variable reports
    return formatters.frequency;
  }

  switch (viewProperty) {
    case 'frequency':
      return formatters.frequency;
    case 'duration':
      return formatters.duration;
    case 'percentage':
      return formatters.percentage;
    default:
      return (v) => v;
  }
}
