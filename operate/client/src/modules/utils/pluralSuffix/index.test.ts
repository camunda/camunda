/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import pluralSuffix from './index';

describe('pluralSuffix', () => {
  it('should append suffix when count === 0', () => {
    const result = pluralSuffix(0, 'foo');

    expect(result).toEqual('0 foos');
  });

  it('should append suffix when count > 1', () => {
    const result = pluralSuffix(2, 'foo');

    expect(result).toEqual('2 foos');
  });

  it('should not append suffix when count === 1', () => {
    const result = pluralSuffix(1, 'foo');

    expect(result).toEqual('1 foo');
  });

  it('should append suffix when count < -1', () => {
    const result = pluralSuffix(-2, 'foo');

    expect(result).toEqual('-2 foos');
  });

  it('should not append suffix when count === -1', () => {
    const result = pluralSuffix(-1, 'foo');

    expect(result).toEqual('-1 foo');
  });
});
