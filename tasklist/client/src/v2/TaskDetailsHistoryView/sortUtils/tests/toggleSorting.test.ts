/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {toggleSorting} from '../index';

describe('toggleSorting', () => {
  it('should add sort param with desc order when none exists', () => {
    expect(toggleSorting('', 'timestamp', undefined)).toBe(
      'sort=timestamp%2Bdesc',
    );
  });

  it('should add sort param when other params exist', () => {
    expect(toggleSorting('?foo=bar', 'timestamp', undefined)).toBe(
      'foo=bar&sort=timestamp%2Bdesc',
    );
  });

  it('should toggle asc to desc', () => {
    expect(toggleSorting('?sort=timestamp+asc', 'timestamp', 'asc')).toBe(
      'sort=timestamp%2Bdesc',
    );
  });

  it('should toggle desc to asc', () => {
    expect(toggleSorting('?sort=timestamp+desc', 'timestamp', 'desc')).toBe(
      'sort=timestamp%2Basc',
    );
  });

  it('should change sort column and reset to desc', () => {
    expect(toggleSorting('?sort=timestamp+asc', 'actorId', undefined)).toBe(
      'sort=actorId%2Bdesc',
    );
  });

  it('should preserve other params when toggling', () => {
    expect(
      toggleSorting('?foo=bar&sort=timestamp+asc', 'timestamp', 'asc'),
    ).toBe('foo=bar&sort=timestamp%2Bdesc');
  });
});
