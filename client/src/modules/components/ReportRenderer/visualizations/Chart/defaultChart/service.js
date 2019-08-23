/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatters} from 'services';

const {convertToMilliseconds} = formatters;

export function getFormattedTargetValue({unit, value}) {
  if (!unit) {
    return value;
  }
  return convertToMilliseconds(value, unit);
}
