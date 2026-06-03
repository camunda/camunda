/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  serializeConditions,
  parseConditionsJson,
  findConditionRanges,
  apiVariablesJsonSchema,
} from './conditionsJsonCodec';
import type {DraftCondition} from './constants';

describe('conditionsJsonCodec', () => {
  it('should serialize equals as plain string value', () => {
    const conditions: DraftCondition[] = [
      {name: 'amount', operator: 'equals', value: '42'},
    ];
    const json = serializeConditions(conditions);
    expect(JSON.parse(json)).toEqual([{name: 'amount', value: '42'}]);
  });

  it('should serialize notEqual as $neq', () => {
    const conditions: DraftCondition[] = [
      {name: 'count', operator: 'notEqual', value: '0'},
    ];
    const json = serializeConditions(conditions);
    expect(JSON.parse(json)).toEqual([{name: 'count', value: {$neq: '0'}}]);
  });

  it('should serialize contains as $like with wildcards', () => {
    const conditions: DraftCondition[] = [
      {name: 'desc', operator: 'contains', value: 'active'},
    ];
    const json = serializeConditions(conditions);
    expect(JSON.parse(json)).toEqual([
      {name: 'desc', value: {$like: '*active*'}},
    ]);
  });

  it('should serialize oneOf as $in array', () => {
    const conditions: DraftCondition[] = [
      {name: 'tags', operator: 'oneOf', value: '["a","b"]'},
    ];
    const json = serializeConditions(conditions);
    expect(JSON.parse(json)).toEqual([
      {name: 'tags', value: {$in: ['a', 'b']}},
    ]);
  });

  it('should serialize exists as $exists true', () => {
    const conditions: DraftCondition[] = [
      {name: 'flag', operator: 'exists', value: ''},
    ];
    const json = serializeConditions(conditions);
    expect(JSON.parse(json)).toEqual([{name: 'flag', value: {$exists: true}}]);
  });

  it('should serialize doesNotExist as $exists false', () => {
    const conditions: DraftCondition[] = [
      {name: 'flag', operator: 'doesNotExist', value: ''},
    ];
    const json = serializeConditions(conditions);
    expect(JSON.parse(json)).toEqual([{name: 'flag', value: {$exists: false}}]);
  });

  it('should serialize multiple conditions', () => {
    const conditions: DraftCondition[] = [
      {name: 'status', operator: 'equals', value: '"active"'},
      {name: 'count', operator: 'notEqual', value: '0'},
      {name: 'flag', operator: 'exists', value: ''},
    ];
    const json = serializeConditions(conditions);
    expect(JSON.parse(json)).toEqual([
      {name: 'status', value: '"active"'},
      {name: 'count', value: {$neq: '0'}},
      {name: 'flag', value: {$exists: true}},
    ]);
  });

  it('should serialize empty array', () => {
    expect(serializeConditions([])).toBe('[]');
  });

  it('should drop fully-empty placeholder rows when serializing', () => {
    const conditions: DraftCondition[] = [
      {name: '', operator: 'equals', value: ''},
    ];
    expect(serializeConditions(conditions)).toBe('[]');
  });

  it('should keep rows where user typed only the name', () => {
    const conditions: DraftCondition[] = [
      {name: 'amount', operator: 'equals', value: ''},
    ];
    const json = serializeConditions(conditions);
    expect(JSON.parse(json)).toEqual([{name: 'amount', value: ''}]);
  });

  it('should keep rows where user typed only the value', () => {
    const conditions: DraftCondition[] = [
      {name: '', operator: 'equals', value: '42'},
    ];
    const json = serializeConditions(conditions);
    expect(JSON.parse(json)).toEqual([{name: '', value: '42'}]);
  });

  it('should mix real conditions with dropped placeholders', () => {
    const conditions: DraftCondition[] = [
      {name: 'amount', operator: 'equals', value: '42'},
      {name: '', operator: 'equals', value: ''},
      {name: 'status', operator: 'exists', value: ''},
    ];
    const json = serializeConditions(conditions);
    expect(JSON.parse(json)).toEqual([
      {name: 'amount', value: '42'},
      {name: 'status', value: {$exists: true}},
    ]);
  });

  it('should parse plain string value as equals', () => {
    const text = JSON.stringify([{name: 'amount', value: '42'}]);
    const result = parseConditionsJson(text);
    expect(result).toEqual({
      ok: true,
      conditions: [{name: 'amount', operator: 'equals', value: '42'}],
    });
  });

  it('should parse explicit $eq as equals', () => {
    const text = JSON.stringify([{name: 'amount', value: {$eq: '42'}}]);
    const result = parseConditionsJson(text);
    expect(result).toEqual({
      ok: true,
      conditions: [{name: 'amount', operator: 'equals', value: '42'}],
    });
  });

  it('should parse $neq as notEqual', () => {
    const text = JSON.stringify([{name: 'count', value: {$neq: '0'}}]);
    const result = parseConditionsJson(text);
    expect(result).toEqual({
      ok: true,
      conditions: [{name: 'count', operator: 'notEqual', value: '0'}],
    });
  });

  it('should parse $like as contains, stripping wildcards', () => {
    const text = JSON.stringify([{name: 'desc', value: {$like: '*active*'}}]);
    const result = parseConditionsJson(text);
    expect(result).toEqual({
      ok: true,
      conditions: [{name: 'desc', operator: 'contains', value: 'active'}],
    });
  });

  it('should parse $in as oneOf', () => {
    const text = JSON.stringify([{name: 'tags', value: {$in: ['a', 'b']}}]);
    const result = parseConditionsJson(text);
    expect(result).toEqual({
      ok: true,
      conditions: [{name: 'tags', operator: 'oneOf', value: '["a","b"]'}],
    });
  });

  it('should parse $exists true as exists', () => {
    const text = JSON.stringify([{name: 'flag', value: {$exists: true}}]);
    const result = parseConditionsJson(text);
    expect(result).toEqual({
      ok: true,
      conditions: [{name: 'flag', operator: 'exists', value: ''}],
    });
  });

  it('should parse $exists false as doesNotExist', () => {
    const text = JSON.stringify([{name: 'flag', value: {$exists: false}}]);
    const result = parseConditionsJson(text);
    expect(result).toEqual({
      ok: true,
      conditions: [{name: 'flag', operator: 'doesNotExist', value: ''}],
    });
  });

  it('should parse empty array', () => {
    expect(parseConditionsJson('[]')).toEqual({ok: true, conditions: []});
  });

  it('should parse empty/whitespace text as empty array', () => {
    expect(parseConditionsJson('')).toEqual({ok: true, conditions: []});
    expect(parseConditionsJson('  ')).toEqual({ok: true, conditions: []});
  });

  it('should reject invalid JSON syntax', () => {
    const result = parseConditionsJson('{not json');
    expect(result).toEqual({
      ok: false,
      error: 'Invalid JSON syntax',
      kind: 'syntax',
    });
  });

  it('should reject non-array root', () => {
    const result = parseConditionsJson('{"name": "x"}');
    expect(result.ok).toBe(false);
    expect((result as {error: string}).error).toContain('expected array');
  });

  it('should reject missing name', () => {
    const text = JSON.stringify([{value: '42'}]);
    const result = parseConditionsJson(text);
    expect(result.ok).toBe(false);
  });

  it('should reject empty name', () => {
    const text = JSON.stringify([{name: '', value: '42'}]);
    const result = parseConditionsJson(text);
    expect(result.ok).toBe(false);
    expect((result as {error: string}).error).toContain(
      'Variable name is required',
    );
  });

  it('should reject extra keys on entry', () => {
    const text = JSON.stringify([{name: 'v', value: '42', extra: true}]);
    const result = parseConditionsJson(text);
    expect(result.ok).toBe(false);
    expect((result as {error: string}).error).toContain('Unrecognized key');
  });

  it('should reject unsupported operator', () => {
    const text = JSON.stringify([{name: 'v', value: {$gt: '42'}}]);
    const result = parseConditionsJson(text);
    expect(result.ok).toBe(false);
    expect((result as {error: string}).error).toContain(
      "Unsupported operator '$gt'",
    );
  });

  it('should reject multiple operators in one entry', () => {
    const text = JSON.stringify([
      {name: 'v', value: {$neq: '0', $like: '*x*'}},
    ]);
    const result = parseConditionsJson(text);
    expect(result.ok).toBe(false);
    expect((result as {error: string}).error).toContain('Multiple operators');
  });

  it('should reject empty operator object', () => {
    const text = JSON.stringify([{name: 'v', value: {}}]);
    const result = parseConditionsJson(text);
    expect(result.ok).toBe(false);
    expect((result as {error: string}).error).toContain('No operator');
  });

  it('should reject $exists with non-boolean', () => {
    const text = JSON.stringify([{name: 'v', value: {$exists: 'true'}}]);
    const result = parseConditionsJson(text);
    expect(result.ok).toBe(false);
    expect((result as {error: string}).error).toContain('Condition #1');
    expect((result as {error: string}).error).toContain('value');
  });

  it('should reject $in with non-string elements', () => {
    const text = JSON.stringify([{name: 'v', value: {$in: [1, 2]}}]);
    const result = parseConditionsJson(text);
    expect(result.ok).toBe(false);
  });

  it('should reject $like with non-string', () => {
    const text = JSON.stringify([{name: 'v', value: {$like: 42}}]);
    const result = parseConditionsJson(text);
    expect(result.ok).toBe(false);
  });

  it('should include condition number in error messages', () => {
    const text = JSON.stringify([
      {name: 'ok', value: '1'},
      {name: '', value: '2'},
    ]);
    const result = parseConditionsJson(text);
    expect(result.ok).toBe(false);
    expect((result as {error: string}).error).toContain('Condition #2');
  });

  it('should round-trip all operator types losslessly', () => {
    const original: DraftCondition[] = [
      {name: 'amount', operator: 'equals', value: '42'},
      {name: 'count', operator: 'notEqual', value: '0'},
      {name: 'desc', operator: 'contains', value: 'active'},
      {name: 'tags', operator: 'oneOf', value: '["a","b"]'},
      {name: 'flag', operator: 'exists', value: ''},
      {name: 'gone', operator: 'doesNotExist', value: ''},
    ];
    const serialized = serializeConditions(original);
    const parsed = parseConditionsJson(serialized);
    expect(parsed).toEqual({ok: true, conditions: original});
  });

  it('should round-trip empty conditions', () => {
    const serialized = serializeConditions([]);
    const parsed = parseConditionsJson(serialized);
    expect(parsed).toEqual({ok: true, conditions: []});
  });

  it('should find ranges for serialized conditions', () => {
    const json = serializeConditions([
      {name: 'a', operator: 'equals', value: '1'},
      {name: 'b', operator: 'exists', value: ''},
    ]);
    const ranges = findConditionRanges(json);
    expect(ranges).toHaveLength(2);
    expect(ranges[0]!.startLine).toBe(2);
    expect(ranges[1]!.startLine).toBeGreaterThan(ranges[0]!.endLine);
  });

  it('should return empty array for empty JSON array', () => {
    expect(findConditionRanges('[]')).toEqual([]);
  });

  it('should handle single-line JSON', () => {
    const json = '[{"name":"a","value":"1"}]';
    const ranges = findConditionRanges(json);
    expect(ranges).toHaveLength(1);
    expect(ranges[0]!.startLine).toBe(1);
    expect(ranges[0]!.endLine).toBe(1);
  });

  it('should not be confused by braces inside string values', () => {
    const json = JSON.stringify(
      [{name: 'data', value: '{"key": "val"}'}],
      null,
      2,
    );
    const ranges = findConditionRanges(json);
    expect(ranges).toHaveLength(1);
  });

  it('should handle escaped backslash before quote in findConditionRanges', () => {
    const json = JSON.stringify([{name: 'path', value: 'C:\\\\'}], null, 2);
    const ranges = findConditionRanges(json);
    expect(ranges).toHaveLength(1);
  });

  it('should reject array passed as value', () => {
    const text = JSON.stringify([{name: 'v', value: [1, 2, 3]}]);
    const result = parseConditionsJson(text);
    expect(result.ok).toBe(false);
  });

  describe('apiVariablesJsonSchema', () => {
    it('should expose a JSON Schema describing the variables array', () => {
      expect(apiVariablesJsonSchema).toMatchObject({
        type: 'array',
        items: expect.objectContaining({
          type: 'object',
          required: expect.arrayContaining(['name', 'value']),
        }),
      });
    });

    it('should mark name with minLength 1', () => {
      const schema = apiVariablesJsonSchema as {
        items?: {properties?: {name?: {minLength?: number}}};
      };
      expect(schema.items?.properties?.name?.minLength).toBe(1);
    });

    it('should disallow additional properties on entries', () => {
      const schema = apiVariablesJsonSchema as {
        items?: {additionalProperties?: boolean};
      };
      expect(schema.items?.additionalProperties).toBe(false);
    });
  });
});
