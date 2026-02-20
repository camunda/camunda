/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {assertRequiredFields, buildUrl, jsonHeaders} from '../../../utils/http';
import {licenseRequiredFields} from '../../../utils/beans/requestBeans';

test.describe.parallel('License API Tests', () => {
  test('Get License', async ({request}) => {
    await test.step('Get License', async () => {
      const res = await request.get(buildUrl('/license'), {
        headers: jsonHeaders(),
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, licenseRequiredFields);
      expect(json.validLicense).toBeFalsy();
      expect(json.licenseType).toBe('unknown');
      expect(json.isCommercial).toBeFalsy();
    });
  });

  test('Get License Unauthorized', async ({request}) => {
    const res = await request.get(buildUrl('/license'), {headers: {}});

    expect(res.status()).toBe(200);
    const json = await res.json();
    assertRequiredFields(json, licenseRequiredFields);
    expect(json.validLicense).toBeFalsy();
    expect(json.licenseType).toBe('unknown');
    expect(json.isCommercial).toBeFalsy();
  });
});
