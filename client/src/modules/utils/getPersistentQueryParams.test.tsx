/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getPersistentQueryParams} from './getPersistentQueryParams';

describe('getPersistentQueryParams', () => {
  it('should get persistent query params', () => {
    expect(getPersistentQueryParams('?test=123&test2=456')).toBe('');

    expect(
      getPersistentQueryParams(
        '?test=123&test2=456&gseUrl=https://www.testUrl.com'
      )
    ).toBe('gseUrl=https%3A%2F%2Fwww.testUrl.com');
  });
});
