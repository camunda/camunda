/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getCollection} from './collectionService';

describe('getCollection', () => {
  it('should return collection id for path starting with /collection/', () => {
    expect(getCollection('/collection/someId')).toBe('someId');
  });

  it('should return null for path not starting with /collection/', () => {
    expect(getCollection('/something/someId')).toBe(null);
  });
});
