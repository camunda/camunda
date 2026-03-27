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
  assertUnauthorizedRequest,
  assertBadRequest,
  assertStatusCode,
  assertNotFoundRequest,
  assertInvalidArgument,
  encode,
  assertForbiddenRequest,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  createMammalProcessInstanceAndDeployMammalDecision,
  createUser,
  DecisionInstance,
  grantUserResourceAuthorization,
} from '@requestHelpers';
import {validateResponse} from '../../../../json-body-assertions';
import {cleanupUsers} from 'utils/usersCleanup';

test.describe.parallel('Delete Decision Instances Batch API Tests', () => {
  let decisionInstances1: DecisionInstance[] = [];
  let decisionInstances2: DecisionInstance[] = [];
  let userWithResourcesAuthorizationToSendRequest: {
    username: string;
    name: string;
    email: string;
    password: string;
  } = {} as {
    username: string;
    name: string;
    email: string;
    password: string;
  };

  test.beforeAll(async ({request}) => {
    const result1 =
      await createMammalProcessInstanceAndDeployMammalDecision(request);
    decisionInstances1 = result1.decisions;

    const result2 =
      await createMammalProcessInstanceAndDeployMammalDecision(request);
    decisionInstances2 = result2.decisions;

    await test.step('Setup - Create test user with Resource Authorization', async () => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
    });
  });

  test.afterAll(async ({request}) => {
    await test.step('Cleanup', async () => {
      await cleanupUsers(request, [
        userWithResourcesAuthorizationToSendRequest.username,
      ]);
    });
  });

  test('Delete Decision Instance With Batch', async ({request}) => {
    const decisionEvaluationKeyToDelete1 =
      decisionInstances1[0].decisionEvaluationKey;
    const decisionEvaluationInstanceKeyToGet1 = decisionInstances1[0].decisionEvaluationInstanceKey;

    const decisionEvaluationKeyToDelete2 =
      decisionInstances2[0].decisionEvaluationKey;
    const decisionEvaluationInstanceKeyToGet2 = decisionInstances2[0].decisionEvaluationInstanceKey;
    console.log(`Decision Evaluation Key to Delete 1: ${decisionEvaluationKeyToDelete1}`);
    console.log(`Decision Evaluation Key to Delete 2: ${decisionEvaluationKeyToDelete2}`);

    await test.step('Delete Decision Instance', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(`/decision-instances/deletion`),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                decisionEvaluationInstanceKey: {
                  $in: [
                    decisionEvaluationKeyToDelete1,
                    decisionEvaluationKeyToDelete2,
                  ],
                },
              },
            },
          },
        );

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/decision-instances/deletion',
            method: 'POST',
            status: '200',
          },
          res,
        );
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Verify Decision Instance is Deleted', async () => {
      await expect(async () => {
        const res1 = await request.get(
          buildUrl(`/decision-instances/${decisionEvaluationInstanceKeyToGet1}`),
          {
            headers: jsonHeaders(),
          },
        );

        await assertNotFoundRequest(
          res1,
          `Decision Instance with id '${decisionEvaluationInstanceKeyToGet1}' not found`,
        );
      }).toPass(defaultAssertionOptions);
    });
    
    await test.step('Verify Decision Instance is Deleted', async () => {
      await expect(async () => {
        const res2 = await request.get(
          buildUrl(`/decision-instances/${decisionEvaluationInstanceKeyToGet2}`),
          {
            headers: jsonHeaders(),
          },
        );

        await assertNotFoundRequest(
          res2,
          `Decision Instance with id '${decisionEvaluationInstanceKeyToGet2}' not found`,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Create a Batch Operation to Delete Decision Instances With No Filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/decision-instances/deletion'), {
      headers: jsonHeaders(),
      data: {
        // No filter or decisionEvaluationInstanceKeys provided
      },
    });
    await assertInvalidArgument(res, 400, 'No filter provided.');
  });

  test('Create a Batch Operation to Delete Decision Instances - With Invalid Filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/decision-instances/deletion'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          invalidField: 'invalidValue',
        },
      },
    });
    await assertBadRequest(
      res,
      'Request property [filter.invalidField] cannot be parsed',
    );
  });

  test('Create a Batch Operation to Delete Decision Instances With Non-Existing Keys - 200 but batch operation is empty', async ({
    request,
  }) => {
    let createdBatchOperationKey: string;

    await test.step('Create Batch Operation with Non-Existing Keys', async () => {
      const nonExistingKey1 = '1234567890';
      const nonExistingKey2 = '0987654321';
      const res = await request.post(buildUrl('/decision-instances/deletion'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            decisionEvaluationInstanceKey: {
              $in: [nonExistingKey1, nonExistingKey2],
            },
          },
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/decision-instances/deletion',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.batchOperationType).toBe('DELETE_DECISION_INSTANCE');
      createdBatchOperationKey = json.batchOperationKey;
    });

    await test.step('Verify No Operations are done in Batch Operation', async () => {
      await expect(async () => {
        const res = await request.get(
          buildUrl(`/batch-operations/${createdBatchOperationKey}`),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/batch-operations/{batchOperationKey}',
            method: 'GET',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.batchOperationKey).toBe(createdBatchOperationKey);
        expect(json.batchOperationType).toBe('DELETE_DECISION_INSTANCE');
        expect(json.operationsTotalCount).toBe(0);
        expect(json.operationsFailedCount).toBe(0);
        expect(json.operationsCompletedCount).toBe(0);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Create a Batch Operation to Delete Decision Instances - Unauthorized', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/decision-instances/deletion'), {
      headers: {},
      data: {
        filter: {
          decisionEvaluationInstanceKey: {
            $in: ['1234567890'], // Non-existing key just to trigger auth before validation
          },
        },
      },
    });
    await assertUnauthorizedRequest(res);
  });

  test('Create a Batch Operation to Delete Decision Instances - Forbidden', async ({
    request,
  }) => {
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );

    await test.step('Attempt to Create Batch Operation without proper permissions', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/decision-instances/deletion'),
          {
            headers: jsonHeaders(token), // overrides default demo:demo
            data: {
              filter: {
                decisionEvaluationInstanceKey: {
                  $in: ['1234567890'], // Non-existing key just to trigger auth before validation
                },
              },
            },
          },
        );
        await assertForbiddenRequest(
          res,
          'CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE',
        );
      }).toPass(defaultAssertionOptions);
    });
  });
});
