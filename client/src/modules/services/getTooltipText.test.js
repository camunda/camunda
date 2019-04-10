/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import getTooltipText from './getTooltipText';

it('should return only absolute value for duration reports', () => {
  const result = getTooltipText(5, () => 5, 5, true, true, true);
  expect(result).toBe(5);
});

it('should return both absolute and relative if both are enabled for frequency reports', () => {
  const result = getTooltipText(5, () => 5, 5, true, true, false);
  expect(result).toBe('5\u00A0(100%)');
});

it('should return relative if only alwaysShowRelative is enabled', () => {
  const result = getTooltipText(5, () => 5, 5, false, true, false);
  expect(result).toBe('100%');
});
