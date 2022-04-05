/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {isValidDate} from './isValidDate';

describe('isValidDate', () => {
  it('should throw error when providing Date object', () => {
    expect(isValidDate('2019-01-01')).toBe(true);
  });

  it('should throw error when providing Date object', () => {
    expect(isValidDate('2019-99-99')).toBe(false);
  });

  it('should throw error when providing Date object', () => {
    expect(() => isValidDate(new Date())).toThrow();
  });
});
