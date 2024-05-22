/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
