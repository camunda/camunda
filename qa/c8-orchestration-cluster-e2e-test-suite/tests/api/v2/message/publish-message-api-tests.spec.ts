/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  buildUrl,
  jsonHeaders,
  assertRequiredFields,
  assertEqualsForKeys,
  assertUnauthorizedRequest,
  assertInvalidArgument,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {PUBLISH_NEW_MESSAGE} from '../../../../utils/beans/requestBeans';

test.describe.parallel('Publish Message API Tests', () => {
  test('Publish Message', async ({request}) => {
    const requestBody = PUBLISH_NEW_MESSAGE();
    const res = await request.post(buildUrl('/messages/publication'), {
      headers: jsonHeaders(),
      data: requestBody,
    });

    await validateResponse(
      {
        path: '/messages/publication',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const json = await res.json();
    assertEqualsForKeys(json, {tenantId: '<default>'}, ['tenantId']);
  });

  test('Publish Message Unauthorized', async ({request}) => {
    const requestBody = PUBLISH_NEW_MESSAGE();

    const res = await request.post(buildUrl('/messages/publication'), {
      headers: {},
      data: requestBody,
    });
    await assertUnauthorizedRequest(res);
  });

  test('Publish Message Bad Request', async ({request}) => {
    const res = await request.post(buildUrl('/messages/publication'), {
      headers: jsonHeaders(),
      data: {correlationKey: 'correlationKey'},
    });
    await assertInvalidArgument(res, 400, 'No name provided.');
  });

  test('Publish Message Invalid Tenant', async ({request}) => {
    const updatedBody = {
      ...PUBLISH_NEW_MESSAGE(),
      tenantId: 'invaliTenant',
    };
    const res = await request.post(buildUrl('/messages/publication'), {
      headers: jsonHeaders(),
      data: updatedBody,
    });
    await assertInvalidArgument(
      res,
      400,
      'Expected to handle request Publish Message with tenant identifier',
    );
  });
});
