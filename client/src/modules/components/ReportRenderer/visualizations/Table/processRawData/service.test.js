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
  const columnOrder = {
    instanceProps: ['prop2', 'processInstanceId'],
    variables: ['variable:var1', 'variable:var2'],
    inputVariables: [],
    outputVariables: [],
  };

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
  const columnOrder = {
    instanceProps: [],
    variables: [],
    inputVariables: [],
    outputVariables: [],
  };

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  expect(sortedHead).toBe(head);
  expect(sortedBody).toBe(body);
});

it('should prepend columns without specified column position', () => {
  const columnOrder = {
    instanceProps: ['processInstanceId'],
    variables: ['variable:var1'],
    inputVariables: [],
    outputVariables: [],
  };

  expect(sortColumns(head, body, columnOrder)).toEqual({
    sortedBody: [
      ['bar', 'foo', '', '12'],
      ['abc', 'xyz', 'true', ''],
    ],
    sortedHead: [
      'prop2',
      'processInstanceId',
      {id: 'variable:var2', label: 'Var: var2', type: 'variables'},
      {id: 'variable:var1', label: 'Var: var1', type: 'variables'},
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
