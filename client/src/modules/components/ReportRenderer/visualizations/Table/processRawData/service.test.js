/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {sortColumns, isVisibleColumn} from './service';

const head = [
  'processInstanceId',
  'prop2',
  {type: 'variables', id: 'variable:var1', label: 'Var: var1'},
  {type: 'variables', id: 'variable:var2', label: 'Var: var2'},
];
const body = [
  ['foo', 'bar', '12', ''],
  ['xyz', 'abc', '', 'true'],
];

it('should apply column order', () => {
  const columnOrder = ['prop2', 'processInstanceId', 'variable:var1', 'variable:var2'];

  expect(sortColumns(head, body, columnOrder)).toEqual({
    sortedHead: [
      'prop2',
      'processInstanceId',
      {type: 'variables', id: 'variable:var1', label: 'Var: var1'},
      {type: 'variables', id: 'variable:var2', label: 'Var: var2'},
    ],
    sortedBody: [
      ['bar', 'foo', '12', ''],
      ['abc', 'xyz', '', 'true'],
    ],
  });
});

it('should return the original head and body if no sorting is applied', () => {
  const columnOrder = [];

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  expect(sortedHead).toBe(head);
  expect(sortedBody).toBe(body);
});

it('should append columns without specified column order', () => {
  const columnOrder = ['processInstanceId', 'variable:var1'];

  expect(sortColumns(head, body, columnOrder)).toEqual({
    sortedBody: [
      ['foo', '12', 'bar', ''],
      ['xyz', '', 'abc', 'true'],
    ],
    sortedHead: [
      'processInstanceId',
      {id: 'variable:var1', label: 'Var: var1', type: 'variables'},
      'prop2',
      {id: 'variable:var2', label: 'Var: var2', type: 'variables'},
    ],
  });
});

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
