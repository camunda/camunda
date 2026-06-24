/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export function isPositiveNumber(value?: string | number): boolean {
  return !!value && isFloatNumber(value) && +value > 0;
}

export function isFloatNumber(value?: string | number): boolean {
  return !!value && !isNaN(+value - parseFloat(value.toString()));
}

export function isNonNegativeNumber(value?: string | number): boolean {
  if (typeof value === 'number') {
    return value >= 0;
  } else if (typeof value === 'string') {
    return !!(value.trim() && !isNaN(+value.toString().trim()) && +value >= 0);
  } else {
    return false;
  }
}

export function isPositiveInt(value?: string | number): boolean {
  return !!value && isNonNegativeNumber(value) && Number.isInteger(+value) && +value > 0;
}
