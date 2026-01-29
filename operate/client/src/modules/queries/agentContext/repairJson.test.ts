/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {safeParseJsonWithRepair} from './repairJson';

describe('safeParseJsonWithRepair', () => {
  it('parses strict JSON', () => {
    const {parsed, parseError} = safeParseJsonWithRepair('{"a":1}');
    expect(parseError).toBeNull();
    expect(parsed).toEqual({a: 1});
  });

  it('repairs newlines inside strings', () => {
    // Intentionally invalid JSON: raw newline inside string value.
    const invalid = '{"a":"hello\nworld"}';

    // Make it truly invalid by using an actual newline character.
    const withActualNewline = '{"a":"hello\n'.replace('\\n', '\n') + 'world"}';

    const {parsed, parseError} = safeParseJsonWithRepair(withActualNewline);
    expect(parseError).toBeNull();
    expect(parsed).toEqual({a: 'hello\nworld'});
  });
});
