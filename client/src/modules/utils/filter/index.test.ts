/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deleteSearchParams} from './index';

describe('deleteSearchParams', () => {
  it('should delete search params', () => {
    const locationMock = {
      hash: '',
      pathname: '/processes',
      search: 'test=1&test2=2&test3=3',
      state: null,
      key: '',
    } as const;

    expect(deleteSearchParams(locationMock, ['test2', 'test3']).search).toBe(
      'test=1'
    );
    expect(deleteSearchParams(locationMock, ['test2']).search).toBe(
      'test=1&test3=3'
    );
    expect(deleteSearchParams(locationMock, ['test4']).search).toBe(
      'test=1&test2=2&test3=3'
    );
  });
});
