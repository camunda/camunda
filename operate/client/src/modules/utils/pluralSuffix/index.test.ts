/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
