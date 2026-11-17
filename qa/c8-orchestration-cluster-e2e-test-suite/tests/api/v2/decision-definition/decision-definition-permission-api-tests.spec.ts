/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {randomUUID} from 'node:crypto';
import {APIRequestContext, expect, test} from '@playwright/test';
import {deployWithSubstitutions} from '../../../../utils/zeebeClient';
import {
  assertForbiddenRequest,
  assertStatusCode,
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {
  createComponentAuthorization,
  createUser,
  grantUserResourceAuthorization,
} from '@requestHelpers';
import {DecisionDeployment} from '@camunda8/sdk/dist/c8/lib/C8Dto';

const DECISION_DEFINITION_SEARCH_ENDPOINT = '/decision-definitions/search';
const DECISION_DEFINITION_EVALUATION_ENDPOINT =
  '/decision-definitions/evaluation';
const READ_FORBIDDEN_DETAIL =
  "Unauthorized to perform operation 'READ_DECISION_DEFINITION' on resource 'DECISION_DEFINITION'";
const EVALUATE_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'CREATE_DECISION_INSTANCE' on resource 'DECISION_DEFINITION'";
const EVALUATION_VARIABLES = {
  userStatus: 'Regular',
  CalculateEngagementScore: 40,
};

type TestUser = {
  username: string;
  name: string;
  email: string;
  password: string;
};

async function deployUniqueDecision(
  decisionId: string,
): Promise<DecisionDeployment> {
  const response = await deployWithSubstitutions(
    './resources/simpleDecisionTable1.dmn',
    {
      Definitions_1lja2g1: `drd-${decisionId}`,
      Decision_f6ej9i5: decisionId,
      SingleTableDecision: decisionId,
    },
  );
  expect(response.decisions).toHaveLength(1);
  return response.decisions[0];
}

async function searchByDecisionDefinitionId(
  request: APIRequestContext,
  decisionDefinitionId: string,
  token?: string,
) {
  const res = await request.post(
    buildUrl(DECISION_DEFINITION_SEARCH_ENDPOINT),
    {
      headers: jsonHeaders(token),
      data: {filter: {decisionDefinitionId}},
    },
  );
  await assertStatusCode(res, 200);
  return res.json();
}

test.describe.serial('Decision Definition Permission API - Forbidden', () => {
  const suffix = randomUUID().slice(0, 8);
  // The partially authorized user may read this decision but not evaluate it.
  const readableDecisionId = `dd-perm-readable-${suffix}`;
  // No restricted user has any access to this decision.
  const restrictedDecisionId = `dd-perm-restricted-${suffix}`;

  let readableDecision: DecisionDeployment;
  let restrictedDecision: DecisionDeployment;

  let userWithoutPermissions: TestUser;
  let userWithoutPermissionsToken: string;
  let userWithReadPermission: TestUser;
  let userWithReadPermissionToken: string;

  test.beforeAll(async ({request}) => {
    readableDecision = await deployUniqueDecision(readableDecisionId);
    restrictedDecision = await deployUniqueDecision(restrictedDecisionId);

    userWithoutPermissions = await createUser(request);
    await grantUserResourceAuthorization(request, userWithoutPermissions);
    userWithoutPermissionsToken = encode(
      `${userWithoutPermissions.username}:${userWithoutPermissions.password}`,
    );

    userWithReadPermission = await createUser(request);
    await grantUserResourceAuthorization(request, userWithReadPermission);
    await createComponentAuthorization(request, {
      ownerId: userWithReadPermission.username,
      ownerType: 'USER',
      resourceId: readableDecisionId,
      resourceType: 'DECISION_DEFINITION',
      permissionTypes: ['READ_DECISION_DEFINITION'],
    });
    userWithReadPermissionToken = encode(
      `${userWithReadPermission.username}:${userWithReadPermission.password}`,
    );
  });

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, [
      userWithoutPermissions.username,
      userWithReadPermission.username,
    ]);
  });

  test('Search decision definitions - filtered out without READ_DECISION_DEFINITION permission', async ({
    request,
  }) => {
    // given
    const decisionDefinitionId = readableDecisionId;

    await expect(async () => {
      const adminBody = await searchByDecisionDefinitionId(
        request,
        decisionDefinitionId,
      );
      expect(adminBody.page.totalItems).toBe(1);

      // when
      const body = await searchByDecisionDefinitionId(
        request,
        decisionDefinitionId,
        userWithoutPermissionsToken,
      );

      // then
      // Search requires READ_DECISION_DEFINITION on DECISION_DEFINITION; without
      // it the decision definition is filtered from the result instead of rejected.
      expect(body.items).toHaveLength(0);
      expect(body.page.totalItems).toBe(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search decision definitions - only readable decision definitions returned', async ({
    request,
  }) => {
    await expect(async () => {
      // given
      const adminBody = await searchByDecisionDefinitionId(
        request,
        restrictedDecisionId,
      );
      expect(adminBody.page.totalItems).toBe(1);

      // when
      const readableBody = await searchByDecisionDefinitionId(
        request,
        readableDecisionId,
        userWithReadPermissionToken,
      );
      const restrictedBody = await searchByDecisionDefinitionId(
        request,
        restrictedDecisionId,
        userWithReadPermissionToken,
      );

      // then
      // READ_DECISION_DEFINITION is granted on the readable decision only, so the
      // restricted decision is filtered out.
      expect(readableBody.page.totalItems).toBe(1);
      expect(readableBody.items[0].decisionDefinitionKey).toBe(
        readableDecision.decisionDefinitionKey,
      );
      expect(restrictedBody.items).toHaveLength(0);
      expect(restrictedBody.page.totalItems).toBe(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Get decision definition - forbidden without READ_DECISION_DEFINITION permission', async ({
    request,
  }) => {
    // given
    const decisionDefinitionKey = readableDecision.decisionDefinitionKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl('/decision-definitions/{decisionDefinitionKey}', {
          decisionDefinitionKey,
        }),
        {headers: jsonHeaders(userWithoutPermissionsToken)},
      );

      // then
      // Get by key requires READ_DECISION_DEFINITION on DECISION_DEFINITION and
      // rejects with 403 when it is missing.
      await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Get decision definition - allowed with READ_DECISION_DEFINITION on that decision', async ({
    request,
  }) => {
    // given
    const decisionDefinitionKey = readableDecision.decisionDefinitionKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl('/decision-definitions/{decisionDefinitionKey}', {
          decisionDefinitionKey,
        }),
        {headers: jsonHeaders(userWithReadPermissionToken)},
      );

      // then
      // READ_DECISION_DEFINITION granted on this decision allows reading it.
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.decisionDefinitionId).toBe(readableDecisionId);
    }).toPass(defaultAssertionOptions);
  });

  test('Get decision definition - forbidden when READ_DECISION_DEFINITION is granted on another decision only', async ({
    request,
  }) => {
    // given
    const decisionDefinitionKey = restrictedDecision.decisionDefinitionKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl('/decision-definitions/{decisionDefinitionKey}', {
          decisionDefinitionKey,
        }),
        {headers: jsonHeaders(userWithReadPermissionToken)},
      );

      // then
      // The permission is checked against the decision definition id, so read
      // access to the readable decision does not extend to the restricted one.
      await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Get decision definition XML - forbidden without READ_DECISION_DEFINITION permission', async ({
    request,
  }) => {
    // given
    const decisionDefinitionKey = readableDecision.decisionDefinitionKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl('/decision-definitions/{decisionDefinitionKey}/xml', {
          decisionDefinitionKey,
        }),
        {headers: jsonHeaders(userWithoutPermissionsToken)},
      );

      // then
      // The XML is resolved through the decision definition, which requires
      // READ_DECISION_DEFINITION on DECISION_DEFINITION and rejects with 403.
      await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Evaluate decision definition by id - forbidden without CREATE_DECISION_INSTANCE permission', async ({
    request,
  }) => {
    // given
    const decisionDefinitionId = readableDecisionId;

    await expect(async () => {
      // when
      const res = await request.post(
        buildUrl(DECISION_DEFINITION_EVALUATION_ENDPOINT),
        {
          headers: jsonHeaders(userWithoutPermissionsToken),
          data: {decisionDefinitionId, variables: EVALUATION_VARIABLES},
        },
      );

      // then
      // Evaluation requires CREATE_DECISION_INSTANCE on DECISION_DEFINITION and
      // is rejected by the engine with 403 when it is missing.
      await assertForbiddenRequest(res, EVALUATE_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Evaluate decision definition by key - forbidden without CREATE_DECISION_INSTANCE permission', async ({
    request,
  }) => {
    // given
    const decisionDefinitionKey = readableDecision.decisionDefinitionKey;

    await expect(async () => {
      // when
      const res = await request.post(
        buildUrl(DECISION_DEFINITION_EVALUATION_ENDPOINT),
        {
          headers: jsonHeaders(userWithoutPermissionsToken),
          data: {decisionDefinitionKey, variables: EVALUATION_VARIABLES},
        },
      );

      // then
      // Addressing the decision by key is subject to the same
      // CREATE_DECISION_INSTANCE check as addressing it by id.
      await assertForbiddenRequest(res, EVALUATE_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Evaluate decision definition - forbidden with READ_DECISION_DEFINITION only', async ({
    request,
  }) => {
    // given
    const decisionDefinitionId = readableDecisionId;

    await expect(async () => {
      // when
      const res = await request.post(
        buildUrl(DECISION_DEFINITION_EVALUATION_ENDPOINT),
        {
          headers: jsonHeaders(userWithReadPermissionToken),
          data: {decisionDefinitionId, variables: EVALUATION_VARIABLES},
        },
      );

      // then
      // Read access to a decision definition does not grant
      // CREATE_DECISION_INSTANCE, so evaluation is still rejected with 403.
      await assertForbiddenRequest(res, EVALUATE_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });
});
