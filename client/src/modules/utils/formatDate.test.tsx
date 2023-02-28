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

    expect(formattedDate).toEqual('02 Jun 2020 - 03:29 PM');
  });

  it('should return empty string and log error on invalid date string', () => {
    const formattedDate = formatDate('invalid date');

    expect(formattedDate).toEqual('');
  });
});
