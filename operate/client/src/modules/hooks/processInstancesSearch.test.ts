/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

vi.mock('modules/feature-flags', async () => {
  const actual = await vi.importActual<typeof import('modules/feature-flags')>(
    'modules/feature-flags',
  );
  return {...actual, IS_VARIABLE_FILTER_V2_ENABLED: false};
});

import {
  buildVariableEntry,
  buildSmartVariableEntry,
  parseOneOfValues,
} from './processInstancesSearch';
import type {VariableCondition} from 'modules/stores/variableFilter';

const mockCondition = {
  name: 'status',
  operator: 'equals',
  value: '"active"',
} satisfies VariableCondition;

describe('buildVariableEntry', () => {
  it('should build equals entry as plain string value', () => {
    expect(buildVariableEntry(mockCondition)).toEqual({
      name: 'status',
      value: '"active"',
    });
  });

  it('should build notEqual entry as {$neq}', () => {
    expect(
      buildVariableEntry({
        ...mockCondition,
        operator: 'notEqual',
        value: '"inactive"',
      }),
    ).toEqual({
      name: 'status',
      value: {$neq: '"inactive"'},
    });
  });

  it('should build contains entry as {$like: "*value*"}', () => {
    expect(
      buildVariableEntry({
        ...mockCondition,
        operator: 'contains',
        value: 'active',
      }),
    ).toEqual({
      name: 'status',
      value: {$like: '*active*'},
    });
  });

  it('should build oneOf entry as {$in: [...]} from JSON array', () => {
    expect(
      buildVariableEntry({
        ...mockCondition,
        operator: 'oneOf',
        value: '["active","pending"]',
      }),
    ).toEqual({
      name: 'status',
      value: {$in: ['"active"', '"pending"']},
    });
  });

  it('should build exists entry as {$exists: true}', () => {
    expect(
      buildVariableEntry({
        ...mockCondition,
        operator: 'exists',
        value: '',
      }),
    ).toEqual({
      name: 'status',
      value: {$exists: true},
    });
  });

  it('should build doesNotExist entry as {$exists: false}', () => {
    expect(
      buildVariableEntry({
        ...mockCondition,
        operator: 'doesNotExist',
        value: '',
      }),
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

describe('buildSmartVariableEntry', () => {
  it('should JSON-encode a number typed without quotes', () => {
    expect(
      buildSmartVariableEntry({
        name: 'amount',
        operator: 'equals',
        value: '42',
      }),
    ).toEqual({name: 'amount', value: {$eq: '42'}});
  });

  it('should auto-quote a bare string for equals', () => {
    expect(
      buildSmartVariableEntry({
        name: 'status',
        operator: 'equals',
        value: 'active',
      }),
    ).toEqual({name: 'status', value: {$eq: '"active"'}});
  });

  it('should JSON-encode a boolean for notEqual', () => {
    expect(
      buildSmartVariableEntry({
        name: 'flag',
        operator: 'notEqual',
        value: 'true',
      }),
    ).toEqual({name: 'flag', value: {$neq: 'true'}});
  });

  it('should pass raw text through $like for contains', () => {
    expect(
      buildSmartVariableEntry({
        name: 'desc',
        operator: 'contains',
        value: 'order',
      }),
    ).toEqual({name: 'desc', value: {$like: '*order*'}});
  });

  it('should split a comma list into a JSON-encoded $in array', () => {
    expect(
      buildSmartVariableEntry({
        name: 'tag',
        operator: 'oneOf',
        value: 'gold, silver, bronze',
      }),
    ).toEqual({
      name: 'tag',
      value: {$in: ['"gold"', '"silver"', '"bronze"']},
    });
  });

  it('should accept a JSON array literal for oneOf', () => {
    expect(
      buildSmartVariableEntry({
        name: 'tag',
        operator: 'oneOf',
        value: '["gold","silver"]',
      }),
    ).toEqual({
      name: 'tag',
      value: {$in: ['"gold"', '"silver"']},
    });
  });

  it('should emit $exists true for exists regardless of value', () => {
    expect(
      buildSmartVariableEntry({
        name: 'x',
        operator: 'exists',
        value: '',
      }),
    ).toEqual({name: 'x', value: {$exists: true}});
  });

  it('should emit $exists false for doesNotExist', () => {
    expect(
      buildSmartVariableEntry({
        name: 'x',
        operator: 'doesNotExist',
        value: '',
      }),
    ).toEqual({name: 'x', value: {$exists: false}});
  });
});
