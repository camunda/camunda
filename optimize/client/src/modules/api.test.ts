/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getFullURL} from './api';

beforeAll(() => {
  Object.defineProperty(window, 'location', {
    value: {
      origin: 'http://localhost:3000',
    },
    writable: true,
  });
});

describe('getFullURL', () => {
  it('should return the same absolute URL when passed an absolute URL', () => {
    const absoluteURL = 'https://example.com/path/to/resource';
    const result = getFullURL(absoluteURL);
    expect(result).toBe(absoluteURL);
  });

  it('should return the full URL when passed a relative URL that starts with /', () => {
    const relativeURL = '/path/to/resource';
    const result = getFullURL(relativeURL);
    expect(result).toBe('http://localhost:3000/path/to/resource');
  });

  it('should return the full URL when passed a relative URL that does not start with /', () => {
    const relativeURL = 'path/to/resource';
    const result = getFullURL(relativeURL);
    expect(result).toBe('http://localhost:3000/path/to/resource');
  });
});
