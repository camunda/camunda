/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAssigneeName} from './getAssigneeName';

describe('getAssigneeName', () => {
  it('should get username', () => {
    expect(getAssigneeName('demouser')).toBe('demouser');
  });
  it('should return double dash if null', () => {
    expect(getAssigneeName(null)).toBe('--');
    expect(getAssigneeName('')).toBe('--');
  });
});
