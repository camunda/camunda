/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {unraw} from './unraw';

describe('unraw', () => {
  it('decodes raw strings', () => {
    expect(unraw('\\b')).toEqual('\b');
    expect(unraw('\\f')).toEqual('\f');
    expect(unraw('\\n')).toEqual('\n');
    expect(unraw('\\r')).toEqual('\r');
    expect(unraw('\\t')).toEqual('\t');
    expect(unraw('\\v')).toEqual('\v');
    expect(unraw('\\0')).toEqual('\0');
    expect(unraw('\\x20')).toEqual(' ');
    expect(unraw('\\u0020')).toEqual(' ');
    expect(unraw('\\u{0020}')).toEqual(' ');
    expect(unraw('\\\\')).toEqual('\\');
    expect(unraw('\\a')).toEqual('a');
    expect(unraw('\\uD83C\\uDF55')).toEqual('🍕');
  });
});
