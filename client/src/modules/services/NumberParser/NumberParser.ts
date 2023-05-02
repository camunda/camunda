/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

export function isPositiveNumber(value: string | number): boolean {
  return isFloatNumber(value) && +value > 0;
}

export function isFloatNumber(value: string | number): boolean {
  return !isNaN(+value - parseFloat(value.toString()));
}

export function isNonNegativeNumber(value: string | number): boolean {
  if (typeof value === 'number') {
    return value >= 0;
  } else if (typeof value === 'string') {
    return !!(value.trim() && !isNaN(+value.toString().trim()) && +value >= 0);
  } else {
    return false;
  }
}

export function isPositiveInt(value: string | number): boolean {
  return isNonNegativeNumber(value) && Number.isInteger(+value) && +value > 0;
}
