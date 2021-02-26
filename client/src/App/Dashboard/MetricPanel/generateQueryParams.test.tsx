/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {generateQueryParams} from './generateQueryParams';

describe('generateQueryParams', () => {
  it('should generate query params', () => {
    expect(
      generateQueryParams({
        filter: {active: true, incidents: true},
        hasFinishedInstances: true,
      })
    ).toEqual(
      '?filter={"active":true,"incidents":true,"completed":true,"canceled":true}'
    );

    expect(generateQueryParams({filter: {incidents: true}})).toEqual(
      '?filter={"incidents":true}'
    );

    expect(generateQueryParams({filter: {active: true}})).toEqual(
      '?filter={"active":true}'
    );
  });
});
