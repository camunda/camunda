/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {buildVariableEntry, parseOneOfValues} from './processInstancesSearch';
import type {VariableCondition} from 'modules/stores/variableFilter';

const makeCondition = (
  overrides: Partial<VariableCondition> = {},
): VariableCondition =>
  ({
    name: 'status',
    operator: 'equals',
    value: '"active"',
    ...overrides,
  }) as VariableCondition;

describe('buildVariableEntry', () => {
  it('should build equals entry as plain string value', () => {
    expect(
      buildVariableEntry(
        makeCondition({operator: 'equals', value: '"active"'}),
      ),
    ).toEqual({
      name: 'status',
      value: '"active"',
    });
  });

  it('should build notEqual entry as {$neq}', () => {
    expect(
      buildVariableEntry(
        makeCondition({operator: 'notEqual', value: '"inactive"'}),
      ),
    ).toEqual({
      name: 'status',
      value: {$neq: '"inactive"'},
    });
  });

  it('should build contains entry as {$like: "*value*"}', () => {
    expect(
      buildVariableEntry(
        makeCondition({operator: 'contains', value: 'active'}),
      ),
    ).toEqual({
      name: 'status',
      value: {$like: '*active*'},
    });
  });

  it('should build oneOf entry as {$in: [...]} from JSON array', () => {
    expect(
      buildVariableEntry(
        makeCondition({operator: 'oneOf', value: '["active","pending"]'}),
      ),
    ).toEqual({
      name: 'status',
      value: {$in: ['"active"', '"pending"']},
    });
  });

  it('should build exists entry as {$exists: true}', () => {
    expect(
      buildVariableEntry(makeCondition({operator: 'exists', value: ''})),
    ).toEqual({
      name: 'status',
      value: {$exists: true},
    });
  });

  it('should build doesNotExist entry as {$exists: false}', () => {
    expect(
      buildVariableEntry(makeCondition({operator: 'doesNotExist', value: ''})),
    ).toEqual({
      name: 'status',
      value: {$exists: false},
    });
  });
});

describe('parseOneOfValues', () => {
  it('should parse a JSON array of strings and JSON-stringify each value', () => {
    expect(parseOneOfValues('["a","b","c"]')).toEqual(['"a"', '"b"', '"c"']);
  });

  it('should fall back to comma-split for non-JSON input', () => {
    expect(parseOneOfValues('a, b, c')).toEqual(['a', 'b', 'c']);
  });

  it('should filter out empty entries from comma-split', () => {
    expect(parseOneOfValues('a,,b')).toEqual(['a', 'b']);
  });

  it('should handle a JSON array of numbers by converting to strings', () => {
    expect(parseOneOfValues('[1,2,3]')).toEqual(['1', '2', '3']);
  });
});

describe('buildVariableEntry — multi-condition', () => {
  it('should produce one entry per condition with mixed operators', () => {
    const conditions: VariableCondition[] = [
      {name: 'status', operator: 'equals', value: '"active"'},
      {name: 'region', operator: 'contains', value: 'eu'},
      {name: 'priority', operator: 'exists', value: ''},
    ];

    expect(conditions.map(buildVariableEntry)).toEqual([
      {name: 'status', value: '"active"'},
      {name: 'region', value: {$like: '*eu*'}},
      {name: 'priority', value: {$exists: true}},
    ]);
  });

  it('should produce byte-equivalent output to legacy path for single equals', () => {
    const condition: VariableCondition = {
      name: 'myVar',
      operator: 'equals',
      value: '"hello"',
    };

    const mvfResult = buildVariableEntry(condition);

    const legacyResult = {
      name: 'myVar',
      value: '"hello"',
    };

    expect(mvfResult).toEqual(legacyResult);
  });
});
