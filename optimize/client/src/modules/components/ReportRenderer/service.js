/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatters} from 'services';

export function isEmpty(str) {
  return !str || 0 === str.length;
}

export function getFormatter(measure) {
  if (typeof measure === 'object') {
    // can only happen for variable reports
    return formatters.frequency;
  }

  switch (measure) {
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

export const formatValue = (value, measure, precision) => {
  return getFormatter(measure)(value, precision);
};
