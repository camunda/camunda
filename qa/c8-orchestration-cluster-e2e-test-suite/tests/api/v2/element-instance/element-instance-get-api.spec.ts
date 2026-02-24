/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {createInstances, deploy} from '../../../../utils/zeebeClient';
import {searchActiveElementInstance} from '@requestHelpers';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {validateResponseShape} from '../../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {EXPECTED_ELEMENT_INSTANCE_GET_SUCCESS} from '../../../../utils/beans/element-instance-requestBeans';

test.describe.parallel('Get Element Instance API', () => {
  const state: Record<string, string> = {};
  test.beforeAll(async () => {
    await deploy(['./resources/element_instance_get_update_tests.bpmn']);
    await createInstances('element_instance_get_update_tests', 1, 1).then(
      (instances) => {
        state.processInstanceKey = instances[0].processInstanceKey;
      },
    );
  });

  test('Get Element Instance - Success', async ({request}) => {
    await test.step('Find element instance key of active element', async () => {
      state.elementInstanceKey = await searchActiveElementInstance(
        request,
        state.processInstanceKey,
      );
    });

    await test.step('Get element instance', async () => {
      await expect(async () => {
        const res = await request.get(
          buildUrl(`/element-instances/${state.elementInstanceKey}`),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        validateResponseShape(
          {
            path: `/element-instances/{elementInstanceKey}`,
            method: 'GET',
            status: '200',
          },
          json,
        );

        expect(json).toMatchObject(
          EXPECTED_ELEMENT_INSTANCE_GET_SUCCESS(
            state.processInstanceKey,
            state.elementInstanceKey,
          ),
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Get Element Instance - Unauthorized', async ({request}) => {
    const res = await request.get(
      buildUrl(`/element-instances/${state.elementInstanceKey}`),
      {
        // No auth headers
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Element Instance - Not Found', async ({request}) => {
    const res = await request.get(buildUrl(`/element-instances/999999999999`), {
      headers: jsonHeaders(),
    });
    await assertNotFoundRequest(
      res,
      "Element Instance with key '999999999999' not found",
    );
  });

  test('Get Element Instance - Invalid Key Format', async ({request}) => {
    const res = await request.get(buildUrl(`/element-instances/invalidKey`), {
      headers: jsonHeaders(),
    });
    await assertBadRequest(
      res,
      "Failed to convert 'elementInstanceKey' with value: 'invalidKey'",
    );
  });
});
