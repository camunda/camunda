/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isVisibleColumn} from './service';

it('should check if column is enabled based on included values', () => {
  const isVisible = isVisibleColumn('test', {
    excludedColumns: ['test'],
    includedColumns: ['test'],
    includeNewVariables: false,
  });

  expect(isVisible).toBe(true);
});

it('should check if column is enabled based on excluded values', () => {
  const isVisible = isVisibleColumn('test', {
    excludedColumns: ['test'],
    includedColumns: ['test'],
    includeNewVariables: true,
  });

  expect(isVisible).toBe(false);
});
