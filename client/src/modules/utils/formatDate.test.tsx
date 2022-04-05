/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {formatDate} from './formatDate';

describe('formatDate', () => {
  it('should format date correctly', () => {
    const formattedDate = formatDate('2020-06-02T15:29:12.766');

    expect(formattedDate).toEqual('2020-06-02 15:29:12');
  });

  it('should return empty string and log error on invalid date string', () => {
    // mock error function
    const originalConsoleError = global.console.error;
    global.console.error = jest.fn();

    // when
    const formattedDate = formatDate('invalid date');

    // then
    expect(formattedDate).toEqual('');
    expect(global.console.error).toHaveBeenCalled();

    // restore original error function
    global.console.error = originalConsoleError;
  });
});
