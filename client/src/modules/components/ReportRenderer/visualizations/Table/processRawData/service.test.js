/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {sortColumns} from './service';

const head = ['processInstanceId', 'prop2', {label: 'Variables', columns: ['var1', 'var2']}];
const body = [['foo', 'bar', '12', ''], ['xyz', 'abc', '', 'true']];

it('should apply column order', () => {
  const columnOrder = {
    instanceProps: ['prop2', 'processInstanceId'],
    variables: ['var1', 'var2']
  };

  expect(sortColumns(head, body, columnOrder)).toEqual({
    sortedHead: ['prop2', 'processInstanceId', {label: 'Variables', columns: ['var1', 'var2']}],
    sortedBody: [['bar', 'foo', '12', ''], ['abc', 'xyz', '', 'true']]
  });
});

it('should return the original head and body if no sorting is applied', () => {
  const columnOrder = {
    instanceProps: [],
    variables: [],
    inputVariables: [],
    outputVariables: []
  };

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  expect(sortedHead).toBe(head);
  expect(sortedBody).toBe(body);
});

it('should prepend columns without specified column position', () => {
  const columnOrder = {instanceProps: ['processInstanceId'], variables: ['var1']};

  expect(sortColumns(head, body, columnOrder)).toEqual({
    sortedHead: ['prop2', 'processInstanceId', {label: 'Variables', columns: ['var2', 'var1']}],
    sortedBody: [['bar', 'foo', '', '12'], ['abc', 'xyz', 'true', '']]
  });
});
