/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@playwright/test';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {
  cancelProcessInstance,
  createInstances,
  deploy,
} from '../../../../utils/zeebeClient';
import {resolveAdHocSubProcessInstanceKey} from '@requestHelpers';

const state: Record<string, unknown> = {};

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Element Instance Ad-hoc Activities API', () => {
  test.beforeAll(async ({request}) => {
    await test.step('Deploy BPMN with ad-hoc subprocess', async () => {
      await deploy(['./resources/ad_hoc_sub_process_api_test.bpmn']);
    });

    await test.step('Create process instances and resolve ad hoc subprocess instance keys', async () => {
      // Two separate instances so that success tests do not interfere with each other
      const instances = await createInstances('AdHocSubProcess_API_Test', 1, 2);

      const simpleProcessInstanceKey = instances[0]
        .processInstanceKey as string;
      const cancelProcessInstanceKey = instances[1]
        .processInstanceKey as string;

      state.processInstanceKeysToCleanup = [
        simpleProcessInstanceKey,
        cancelProcessInstanceKey,
      ];

      state.adHocSubProcessInstanceKeySimple =
        await resolveAdHocSubProcessInstanceKey(
          request,
          simpleProcessInstanceKey,
        );

      state.adHocSubProcessInstanceKeyCancel =
        await resolveAdHocSubProcessInstanceKey(
          request,
          cancelProcessInstanceKey,
        );
    });
  });

  test.afterAll(async () => {
    const keys = (state.processInstanceKeysToCleanup || []) as string[];
    for (const key of keys) {
      try {
        await cancelProcessInstance(key);
      } catch (error) {
        const message = (error as Error).message || '';
        if (!message.includes('NOT_FOUND') && !message.includes('404')) {
          throw error;
        }
      }
    }
  });

  test('Activate AdHoc Activities SucceedsWithValidElements', async ({
    request,
  }) => {
    const adHocKey = state.adHocSubProcessInstanceKeySimple as string;

    const body = {
      elements: [
        {
          elementId: 'Activity_A',
        },
      ],
    };

    const res = await test.step('Call activation endpoint', async () => {
      return await request.post(
        buildUrl(
          '/element-instances/ad-hoc-activities/{adHocSubProcessInstanceKey}/activation',
          {adHocSubProcessInstanceKey: adHocKey},
        ),
        {
          headers: jsonHeaders(),
          data: body,
        },
      );
    });

    await test.step('Assert successful response', async () => {
      await assertStatusCode(res, 204);
    });
  });

  test('Activate AdHoc Activities SucceedsWithVariablesAndCancel', async ({
    request,
  }) => {
    const adHocKey = state.adHocSubProcessInstanceKeyCancel as string;

    const body = {
      elements: [
        {
          elementId: 'Activity_B',
          variables: {
            orderId: '123',
          },
        },
      ],
      cancelRemainingInstances: true,
    };

    const res = await test.step('Call activation endpoint', async () => {
      return await request.post(
        buildUrl(
          '/element-instances/ad-hoc-activities/{adHocSubProcessInstanceKey}/activation',
          {adHocSubProcessInstanceKey: adHocKey},
        ),
        {
          headers: jsonHeaders(),
          data: body,
        },
      );
    });

    await test.step('Assert successful response', async () => {
      await assertStatusCode(res, 204);
    });
  });

  test('Activate AdHoc Activities FailsWithMissingElements', async ({
    request,
  }) => {
    const adHocKey = state.adHocSubProcessInstanceKey as string;

    const invalidBody = {
      cancelRemainingInstances: false,
    };

    const res =
      await test.step('Call activation endpoint with invalid body', async () => {
        return await request.post(
          buildUrl(
            '/element-instances/ad-hoc-activities/{adHocSubProcessInstanceKey}/activation',
            {adHocSubProcessInstanceKey: adHocKey},
          ),
          {
            headers: jsonHeaders(),
            data: invalidBody,
          },
        );
      });

    await test.step('Assert bad request error', async () => {
      await assertBadRequest(res, /elements/i, 'INVALID_ARGUMENT');
    });
  });

  test('Activate AdHoc Activities ReturnsUnauthorizedWithoutAuth', async ({
    request,
  }) => {
    const adHocKey = state.adHocSubProcessInstanceKey as string;

    const body = {
      elements: [
        {
          elementId: 'Activity_A',
        },
      ],
    };

    const res =
      await test.step('Call activation endpoint without authorization header', async () => {
        return await request.post(
          buildUrl(
            '/element-instances/ad-hoc-activities/{adHocSubProcessInstanceKey}/activation',
            {adHocSubProcessInstanceKey: adHocKey},
          ),
          {
            headers: {
              'Content-Type': 'application/json',
            },
            data: body,
          },
        );
      });

    await test.step('Assert unauthorized error', async () => {
      await assertUnauthorizedRequest(res);
    });
  });

  test('Activate AdHoc Activities ReturnsNotFoundForRandomKey', async ({
    request,
  }) => {
    const unknownKey = '2251799813685249';

    const body = {
      elements: [
        {
          elementId: 'Activity_A',
        },
      ],
    };

    const res =
      await test.step('Call activation endpoint with unknown key', async () => {
        return await request.post(
          buildUrl(
            '/element-instances/ad-hoc-activities/{adHocSubProcessInstanceKey}/activation',
            {adHocSubProcessInstanceKey: unknownKey},
          ),
          {
            headers: jsonHeaders(),
            data: body,
          },
        );
      });

    await test.step('Assert not found error', async () => {
      await assertNotFoundRequest(res, 'ad-hoc sub-process');
    });
  });
});
