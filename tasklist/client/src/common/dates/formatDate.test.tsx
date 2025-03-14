/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatDate} from './formatDate';

describe('formatDate', () => {
  it('should format date correctly', () => {
    const formattedDate = formatDate('2020-06-02T15:29:12.766');

    expect(formattedDate).toEqual('02 Jun 2020 - 3:29 PM');
  });

  it('should return empty string and log error on invalid date string', () => {
    const formattedDate = formatDate('invalid date');

    expect(formattedDate).toEqual('');
  });

  it('should hide time', () => {
    const formattedDate = formatDate('2020-06-02T15:29:12.766', false);

    expect(formattedDate).toEqual('02 Jun 2020');
  });
});
