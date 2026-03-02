/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {buildUrl, jsonHeaders, assertStatusCode} from '../../../utils/http';
import {validateResponse} from '../../../json-body-assertions';

test.describe.parallel('License API Tests', () => {
  test('Get License', async ({request}) => {
    await test.step('Get License', async () => {
      const res = await request.get(buildUrl('/license'), {
        headers: jsonHeaders(),
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/license',
          method: 'GET',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.validLicense).toBeFalsy();
      expect(json.licenseType).toBe('unknown');
      expect(json.isCommercial).toBeFalsy();
    });
  });

  test('Get License Unauthorized', async ({request}) => {
    const res = await request.get(buildUrl('/license'), {headers: {}});

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/license',
        method: 'GET',
        status: '200',
      },
      res,
    );
    const json = await res.json();
    expect(json.validLicense).toBeFalsy();
    expect(json.licenseType).toBe('unknown');
    expect(json.isCommercial).toBeFalsy();
  });
});
