/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getSortParams} from '../index';

describe('getSortParams', () => {
  it('should return null for empty search', () => {
    expect(getSortParams('')).toBeNull();
  });

  it('should return null when no sort param exists', () => {
    expect(getSortParams('?foo=bar')).toBeNull();
  });

  it('should parse valid sort param with desc order', () => {
    expect(getSortParams('?sort=timestamp%2Bdesc')).toEqual({
      sortBy: 'timestamp',
      sortOrder: 'desc',
    });
  });

  it('should parse valid sort param with asc order', () => {
    expect(getSortParams('?sort=actorId%2Basc')).toEqual({
      sortBy: 'actorId',
      sortOrder: 'asc',
    });
  });

  it('should return null for invalid format without order', () => {
    expect(getSortParams('?sort=timestamp')).toBeNull();
  });

  it('should return null for invalid format with wrong order', () => {
    expect(getSortParams('?sort=timestamp+invalid')).toBeNull();
  });

  it('should return null for empty sort value', () => {
    expect(getSortParams('?sort=')).toBeNull();
  });

  it('should handle search string with other params', () => {
    expect(getSortParams('?foo=bar&sort=operationType%2Basc&baz=qux')).toEqual({
      sortBy: 'operationType',
      sortOrder: 'asc',
    });
  });
});
