/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {cancelProcessInstance} from '../../../../utils/zeebeClient';
import {
  buildUrl,
  jsonHeaders,
  assertBadRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  WAIT_STATES_SEARCH_ENDPOINT,
  createProcessInstanceWaitingOnJob,
} from '@requestHelpers';

test.describe.parallel('Wait State Search Validation', () => {
  const processInstanceKeys: string[] = [];

  test.afterAll(async () => {
    for (const key of processInstanceKeys) {
      await cancelProcessInstance(key);
    }
  });

  test('finds a wait state by processInstanceKey with sort and pagination applied', async ({
    request,
  }) => {
    const instance = await createProcessInstanceWaitingOnJob();
    processInstanceKeys.push(instance.processInstanceKey);

    await expect(async () => {
      const res = await request.post(buildUrl(WAIT_STATES_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {processInstanceKey: instance.processInstanceKey},
          sort: [{field: 'elementId'}],
          page: {limit: 10},
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: WAIT_STATES_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.page.totalItems).toBe(1);
      expect(body.items).toHaveLength(1);
      expect(body.items[0].processInstanceKey).toBe(
        instance.processInstanceKey,
      );
      expect(body.items[0].details.waitStateType).toBe('JOB');
    }).toPass(defaultAssertionOptions);
  });

  test('rejects an unknown filter field with 400', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(WAIT_STATES_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {invalidField: 'someValue'},
        },
      });
      await assertBadRequest(
        res,
        'Request property [filter.invalidField] cannot be parsed',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('returns an empty result, not an error, for a well-formed filter matching nothing', async ({
    request,
  }) => {
    const res = await request.post(buildUrl(WAIT_STATES_SEARCH_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        filter: {processInstanceKey: '9999999999999999'},
      },
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: WAIT_STATES_SEARCH_ENDPOINT,
        method: 'POST',
        status: '200',
      },
      res,
    );
    const body = await res.json();
    expect(body.page.totalItems).toBe(0);
    expect(body.items).toHaveLength(0);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('rejects an unauthenticated request with 401', async ({request}) => {
    const res = await request.post(buildUrl(WAIT_STATES_SEARCH_ENDPOINT), {
      headers: {},
      data: {},
    });
    await assertUnauthorizedRequest(res);
  });
});
