/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {buildVariableEntry} from './processInstancesSearch';
import type {VariableCondition} from 'modules/stores/variableFilter';

describe('buildVariableEntry', () => {
  it('equals: should auto-quote a bare string', () => {
    expect(
      buildVariableEntry({
        name: 'status',
        operator: 'equals',
        value: 'active',
      }),
    ).toEqual({name: 'status', value: {$eq: '"active"'}});
  });

  it('equals: should accept an already-quoted JSON string and re-encode it', () => {
    expect(
      buildVariableEntry({
        name: 'status',
        operator: 'equals',
        value: '"active"',
      }),
    ).toEqual({name: 'status', value: {$eq: '"active"'}});
  });

  it('equals: should JSON-encode a number typed without quotes', () => {
    expect(
      buildVariableEntry({
        name: 'amount',
        operator: 'equals',
        value: '42',
      }),
    ).toEqual({name: 'amount', value: {$eq: '42'}});
  });

  it('equals: should JSON-encode a boolean', () => {
    expect(
      buildVariableEntry({
        name: 'flag',
        operator: 'equals',
        value: 'true',
      }),
    ).toEqual({name: 'flag', value: {$eq: 'true'}});
  });

  it('notEqual: should auto-quote a bare string', () => {
    expect(
      buildVariableEntry({
        name: 'status',
        operator: 'notEqual',
        value: 'inactive',
      }),
    ).toEqual({name: 'status', value: {$neq: '"inactive"'}});
  });

  it('notEqual: should JSON-encode a boolean', () => {
    expect(
      buildVariableEntry({
        name: 'flag',
        operator: 'notEqual',
        value: 'true',
      }),
    ).toEqual({name: 'flag', value: {$neq: 'true'}});
  });

  it('contains: should pass raw text through $like with wildcards', () => {
    expect(
      buildVariableEntry({
        name: 'desc',
        operator: 'contains',
        value: 'order',
      }),
    ).toEqual({name: 'desc', value: {$like: '*order*'}});
  });

  it.each([
    ['hello, "world', '*hello, "world*'],
    ['{json}', '*{json}*'],
    ['"quoted"', '*"quoted"*'],
    ['[1,2,3]', '*[1,2,3]*'],
    ['a, b', '*a, b*'],
  ])(
    'contains: should preserve structural characters in the substring (%j)',
    (input, expected) => {
      expect(
        buildVariableEntry({
          name: 'desc',
          operator: 'contains',
          value: input,
        }),
      ).toEqual({name: 'desc', value: {$like: expected}});
    },
  );

  it('oneOf: should accept a JSON array literal and JSON-stringify each value', () => {
    expect(
      buildVariableEntry({
        name: 'tag',
        operator: 'oneOf',
        value: '["gold","silver"]',
      }),
    ).toEqual({
      name: 'tag',
      value: {$in: ['"gold"', '"silver"']},
    });
  });

  it('oneOf: should split a comma list into a JSON-encoded $in array', () => {
    expect(
      buildVariableEntry({
        name: 'tag',
        operator: 'oneOf',
        value: 'gold, silver, bronze',
      }),
    ).toEqual({
      name: 'tag',
      value: {$in: ['"gold"', '"silver"', '"bronze"']},
    });
  });

  it('oneOf: should handle a JSON array of numbers', () => {
    expect(
      buildVariableEntry({
        name: 'priority',
        operator: 'oneOf',
        value: '[1,2,3]',
      }),
    ).toEqual({
      name: 'priority',
      value: {$in: ['1', '2', '3']},
    });
  });

  it('exists: should emit $exists true regardless of stored value', () => {
    expect(
      buildVariableEntry({
        name: 'x',
        operator: 'exists',
        value: '',
      }),
    ).toEqual({name: 'x', value: {$exists: true}});
  });

  it('doesNotExist: should emit $exists false', () => {
    expect(
      buildVariableEntry({
        name: 'x',
        operator: 'doesNotExist',
        value: '',
      }),
    ).toEqual({name: 'x', value: {$exists: false}});
  });

  it('multi-condition: should produce one entry per condition with mixed operators', () => {
    const conditions: VariableCondition[] = [
      {name: 'status', operator: 'equals', value: 'active'},
      {name: 'region', operator: 'contains', value: 'eu'},
      {name: 'priority', operator: 'exists', value: ''},
    ];

    expect(conditions.map(buildVariableEntry)).toEqual([
      {name: 'status', value: {$eq: '"active"'}},
      {name: 'region', value: {$like: '*eu*'}},
      {name: 'priority', value: {$exists: true}},
    ]);
  });

  it('should return null for an unparseable value (non-contains)', () => {
    expect(
      buildVariableEntry({
        name: 'broken',
        operator: 'equals',
        value: '"NEW',
      }),
    ).toBeNull();
  });
});
