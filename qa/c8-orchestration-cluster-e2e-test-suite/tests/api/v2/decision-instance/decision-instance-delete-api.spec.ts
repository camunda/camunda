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
  assertStatusCode,
  assertNotFoundRequest,
  assertForbiddenRequest,
  encode,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  createMammalProcessInstanceAndDeployMammalDecision,
  createUser,
  DecisionInstance,
  grantUserResourceAuthorization,
} from '@requestHelpers';
import {cleanupUsers} from 'utils/usersCleanup';

test.describe.serial('Delete Decision Instances API Tests', () => {
  let decisionInstances: DecisionInstance[] = [];
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
    const result =
      await createMammalProcessInstanceAndDeployMammalDecision(request);
    decisionInstances = result.decisions;

    await test.step('Setup - Create test user with Resource Authorization and user for granting Authorization', async () => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
    });
  });

  test.afterAll(async ({request}) => {
    await test.step('Cleanup - Delete test users', async () => {
      await cleanupUsers(request, [
        userWithResourcesAuthorizationToSendRequest.username,
      ]);
    });
  });

  test('Delete Decision Instance - Success', async ({request}) => {
    const decisionInstanceToDelete =
        decisionInstances[decisionInstances.length - 1];
    const decisionEvaluationKeyToDelete =
      decisionInstanceToDelete.decisionEvaluationKey;

    await test.step('Delete Decision Instance', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(
            `/decision-instances/${decisionEvaluationKeyToDelete}/deletion`,
          ),
          {
            headers: jsonHeaders(),
          },
        );

        await assertStatusCode(res, 204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Verify Decision Instance is Deleted', async () => {
      const decisionEvaluationInstanceKeyToGet = decisionInstanceToDelete.decisionEvaluationInstanceKey;
      await expect(async () => {
        const res = await request.get(
          buildUrl(`/decision-instances/${decisionEvaluationInstanceKeyToGet}`),
          {
            headers: jsonHeaders(),
          },
        );

        await assertNotFoundRequest(
          res,
          `Decision Instance with id '${decisionEvaluationInstanceKeyToGet}' not found`,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Delete Decision Instance - Unauthorized', async ({request}) => {
    const decisionInstanceToDelete = decisionInstances[1];
    const decisionEvaluationKeyToDelete =
      decisionInstanceToDelete.decisionEvaluationKey;

    const res = await request.post(
      buildUrl(`/decision-instances/${decisionEvaluationKeyToDelete}/deletion`),
      {
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Delete Decision Instance - Not Found', async ({request}) => {
    const notExistingDecisionEvaluationKeyToDelete = '9999999999999';

    const res = await request.post(
      buildUrl(
        `/decision-instances/${notExistingDecisionEvaluationKeyToDelete}/deletion`,
      ),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Decision Instance with key '${notExistingDecisionEvaluationKeyToDelete}' not found`,
    );
  });

  test('Delete Decision Instance - Forbidden', async ({request}) => {
    await test.step('Attempt to Delete Decision Instance with insufficient permissions', async () => {
      const decisionInstanceToDelete = decisionInstances[0];
      const decisionEvaluationKeyToDelete =
        decisionInstanceToDelete.decisionEvaluationKey;
      const token = encode(
        `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
      );

      const res = await request.post(
        buildUrl(
          `/decision-instances/${decisionEvaluationKeyToDelete}/deletion`,
        ),
        {
          headers: jsonHeaders(token), // overrides default demo:demo
        },
      );
      await assertForbiddenRequest(
        res,
        "operation 'DELETE_DECISION_INSTANCE' on resource 'DECISION_DEFINITION'",
      );
    });
  });
});
