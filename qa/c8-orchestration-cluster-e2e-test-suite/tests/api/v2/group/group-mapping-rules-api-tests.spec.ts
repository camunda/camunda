/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  jsonHeaders,
  extractAndStoreIds,
  buildUrl,
  assertRequiredFields,
  assertEqualsForKeys,
  paginatedResponseFields,
} from '../../../../utils/http';
import {
<<<<<<< HEAD
  CREATE_NEW_GROUP,
  groupRequiredFields,
  mappingRuleRequiredFields,
} from '../../../../utils/beans/request-beans';
import {sleep} from '../../../../utils/sleep';
import {CREATE_NEW_MAPPING_RULE} from '../../../../utils/beans/request-beans';
=======
  MAPPING_RULE_EXPECTED_BODY_USING_STATE,
  mappingRuleRequiredFields,
} from '../../../../utils/beans/requestBeans';
import {
  assignMappingToGroup,
  createGroupAndStoreResponseFields,
  createMappingRule,
  mappingRuleIdFromState,
} from '../../../../utils/requestHelpers';
import {defaultAssertionOptions} from '../../../../utils/constants';
>>>>>>> 4fa4510d (test: v2 role endpoints implemented)

test.describe('Group Mapping Rules API Tests', () => {
  const state: Record<string, unknown> = {};

  test('CRUD', async ({request}) => {
    await test.step('Create Group', async () => {
      const requestBody = CREATE_NEW_GROUP();
      const res = await request.post(buildUrl('/groups'), {
        headers: jsonHeaders(),
        data: requestBody,
      });
      expect(res.status()).toBe(201);
      const json = await res.json();
      assertRequiredFields(json, groupRequiredFields);
      assertEqualsForKeys(json, requestBody, groupRequiredFields);
      await extractAndStoreIds(res, state);
    });

    await test.step('Create Mapping Rule', async () => {
      const body = CREATE_NEW_MAPPING_RULE();
      const res = await request.post(buildUrl('/mapping-rules'), {
        headers: jsonHeaders(),
        data: body,
      });
      expect(res.status()).toBe(201);
      await extractAndStoreIds(res, state);
    });

    await test.step('Assign Mapping Rule To Group', async () => {
      const p = {
        groupId: state['groupId'] as string,
        mappingRuleId: state['mappingRuleId'] as string,
      };
      const res = await request.put(
        buildUrl('/groups/{groupId}/mapping-rules/{mappingRuleId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    });

<<<<<<< HEAD
    await test.step('Search Mapping Rules For Group', async () => {
      await sleep(10000);
      const p = {groupId: state['groupId'] as string};
      const expectedBody: Record<string, string> = {
        claimName: state['claimName'] as string,
        claimValue: state['claimValue'] as string,
        name: state['name'] as string,
        mappingRuleId: state['mappingRuleId'] as string,
      };

=======
  test('Assign Already Added Mapping Rule To Group Conflict', async ({
    request,
  }) => {
    const stateParams: Record<string, string> = {
      groupId: state['groupId2'] as string,
      mappingRuleId: mappingRuleIdFromState('groupId2', state) as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl(
          '/groups/{groupId}/mapping-rules/{mappingRuleId}',
          stateParams,
        ),
        {
          headers: jsonHeaders(),
        },
      );

      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Mapping Rules For Group', async ({request}) => {
    const groupId: string = state['groupId2'] as string;

    await expect(async () => {
>>>>>>> 4fa4510d (test: v2 role endpoints implemented)
      const res = await request.post(
        buildUrl('/groups/{groupId}/mapping-rules/search', p),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(1);
      assertRequiredFields(json.items[0], mappingRuleRequiredFields);
      assertEqualsForKeys(
        json.items[0],
        MAPPING_RULE_EXPECTED_BODY_USING_STATE('groupId2', state),
        mappingRuleRequiredFields,
      );
    });

    await test.step('Search Mapping Rules For Group Unauthorized', async () => {
      const p = {groupId: state['groupId'] as string};
      const res = await request.post(
        buildUrl('/groups/{groupId}/mapping-rules/search', p),
        {
          headers: {},
          data: {},
        },
      );
      expect(res.status()).toBe(401);
    });

    await test.step('Search Mapping Rules For Group Not Found', async () => {
      const p = {groupId: 'invalidGroupId'};
      const res = await request.post(
        buildUrl('/groups/{groupId}/mapping-rules/search', p),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(0);
      expect(json.items.length).toBe(0);
    });

    await test.step('Unassign Mapping Rule From Group', async () => {
      const p = {
<<<<<<< HEAD
        groupId: state['groupId'] as string,
        mappingRuleId: state['mappingRuleId'] as string,
=======
        groupId: state['groupId3'] as string,
        mappingRuleId: mappingRuleIdFromState('groupId3', state) as string,
>>>>>>> 4fa4510d (test: v2 role endpoints implemented)
      };
      const res = await request.delete(
        buildUrl('/groups/{groupId}/mapping-rules/{mappingRuleId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    });

    await test.step('Search Mapping Rules After Deletion', async () => {
      await sleep(10000);
      const p = {groupId: state['groupId'] as string};

      const res = await request.post(
        buildUrl('/groups/{groupId}/mapping-rules/search', p),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(0);
      expect(json.items.length).toBe(0);
    });

<<<<<<< HEAD
    await test.step('Unassign Mapping Rule From Group Unauthorized', async () => {
      const p = {
        groupId: state['groupId'] as string,
        mappingRuleId: state['mappingRuleId'] as string,
      };
      const res = await request.delete(
        buildUrl('/groups/{groupId}/mapping-rules/{mappingRuleId}', p),
        {
          headers: {},
        },
      );
      expect(res.status()).toBe(401);
    });

    await test.step('Unassign Mapping Rule From Group Not Found', async () => {
      const p = {
        groupId: state['groupId'] as string,
        mappingRuleId: state['mappingRuleId'] as string,
      };
      const res = await request.delete(
        buildUrl('/groups/{groupId}/mapping-rules/{mappingRuleId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(404);
    });
=======
  test('Unassign Mapping Rule From Group Unauthorized', async ({request}) => {
    const p = {
      groupId: state['groupId2'] as string,
      mappingRuleId: mappingRuleIdFromState('groupId2', state) as string,
    };
    const res = await request.delete(
      buildUrl('/groups/{groupId}/mapping-rules/{mappingRuleId}', p),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign Mapping Rule From Group Not Found', async ({request}) => {
    const p = {
      groupId: 'invalidGroupId',
      mappingRuleId: mappingRuleIdFromState('groupId2', state) as string,
    };
    const res = await request.delete(
      buildUrl('/groups/{groupId}/mapping-rules/{mappingRuleId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
>>>>>>> 4fa4510d (test: v2 role endpoints implemented)
  });
});
