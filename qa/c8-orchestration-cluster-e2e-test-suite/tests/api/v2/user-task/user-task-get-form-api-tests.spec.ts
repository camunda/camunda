/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import test, {expect} from '@playwright/test';
import {
  cancelProcessInstance,
  createInstances,
  deploy,
} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertRequiredFields,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponseShape} from '../../../../json-body-assertions';
import {findUserTask} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Get User Task Form Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async () => {
    await deploy([
      './resources/user_task_form.form',
      './resources/user_task_with_form.bpmn',
      './resources/usertask_to_be_assigned.bpmn',
    ]);
  });

  test.beforeEach(async () => {
    const processInstance = await createInstances('user_registration', 1, 1, {
      testset1: 'something',
      testset2: 'something else',
      zip: 123,
    });
    state['processInstanceKey'] = processInstance[0].processInstanceKey;
  });

  test.afterEach(async () => {
    await cancelProcessInstance(state['processInstanceKey'] as string);
  });

  test('Get user task form - success', async ({request}) => {
    const expectedSchema = `{
  \"type\": \"default\",
  \"id\": \"user_task_form\",
  \"components\": [
    {
      \"key\": \"name\",
      \"label\": \"Name\",
      \"type\": \"textfield\",
      \"validate\": {
        \"required\": true
      }
    },
    {
      \"key\": \"address\",
      \"label\": \"Address\",
      \"type\": \"textfield\",
      \"validate\": {
        \"required\": true
      }
    },
    {
      \"key\": \"age\",
      \"label\": \"Age\",
      \"type\": \"textfield\"
    },
    {
      \"key\": \"button1\",
      \"label\": \"Save\",
      \"type\": \"button\"
    }
  ]
}
`;

    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    await expect(async () => {
      const res = await request.get(
        buildUrl(`/user-tasks/${userTaskKey}/form`),
        {
          headers: jsonHeaders(),
        },
      );
      const json = await res.json();
      validateResponseShape(
        {
          path: '/user-tasks/{userTaskKey}/form',
          method: 'GET',
          status: '200',
        },
        json,
      );
      expect(json.formId).toBe('user_task_form');
      expect(json.version).toBe(1);
      expect(json.schema).toBe(expectedSchema);
    }).toPass(defaultAssertionOptions);
  });

  test('Get user task form - success - task with no form', async ({
    request,
  }) => {
    const processInstance = await createInstances(
      'usertask_to_be_assigned',
      1,
      1,
      {},
    );
    const userTaskKey = await findUserTask(
      request,
      processInstance[0].processInstanceKey,
      'CREATED',
    );

    await expect(async () => {
      const res = await request.get(
        buildUrl(`/user-tasks/${userTaskKey}/form`),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res, 204);
      await cancelProcessInstance(
        processInstance[0].processInstanceKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get user task form - unauthorized', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.get(buildUrl(`/user-tasks/${userTaskKey}/form`), {
      // No auth headers
      headers: {
        'Content-Type': 'application/json',
      },
    });
    await assertUnauthorizedRequest(res);
  });

  test('Get user task form - bad request - invalid user task key', async ({
    request,
  }) => {
    const invalidUserTaskKey = 'invalidKey';
    const res = await request.get(
      buildUrl(`/user-tasks/${invalidUserTaskKey}/form`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertBadRequest(
      res,
      `Failed to convert 'userTaskKey' with value: '${invalidUserTaskKey}'`,
    );
  });

  test('Get user task form - not found - non existing user task', async ({
    request,
  }) => {
    const nonExistingUserTaskKey = '2251799813711183';
    await expect(async () => {
      const res = await request.get(
        buildUrl(`/user-tasks/${nonExistingUserTaskKey}/form`),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res, 404);
      const json = await res.json();
      assertRequiredFields(json, ['detail', 'title']);
      expect(json.title).toBe('NOT_FOUND');
      expect(json.detail).toContain(
        `User Task with key '${nonExistingUserTaskKey}' not found`,
      );
    }).toPass(defaultAssertionOptions);
  });
});
