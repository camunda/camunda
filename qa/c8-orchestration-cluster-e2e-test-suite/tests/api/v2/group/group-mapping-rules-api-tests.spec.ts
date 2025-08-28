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
  buildUrl,
  assertRequiredFields,
  assertEqualsForKeys,
  paginatedResponseFields,
  assertNotFoundRequest,
  assertUnauthorizedRequest,
  assertConflictRequest,
} from '../../../../utils/http';
import {
  CREATE_MAPPING_EXPECTED_BODY_USING_GROUP,
  mappingRuleRequiredFields,
} from '../../../../utils/beans/requestBeans';
import {
  assignMappingToGroup,
  createGroupAndStoreResponseFields,
  createMappingRule,
  mappingRuleFromState,
} from '../../../../utils/requestHelpers';
import {defaultAssertionOptions} from '../../../../utils/constants';

test.describe.parallel('Group Mapping Rules API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createGroupAndStoreResponseFields(request, 3, state);
    await assignMappingToGroup(request, 1, state['groupId2'] as string, state);
    await assignMappingToGroup(request, 1, state['groupId3'] as string, state);
  });

  test('Assign Mapping Rule To Group', async ({request}) => {
    const mappingRuleBody = await createMappingRule(request);
    const p = {
      groupId: state['groupId1'] as string,
      mappingRuleId: mappingRuleBody.mappingRuleId as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/groups/{groupId}/mapping-rules/{mappingRuleId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Already Added Mapping Rule To Group Conflict', async ({
    request,
  }) => {
    const stateParams: Record<string, string> = {
      groupId: state['groupId2'] as string,
      mappingRuleId: mappingRuleFromState('groupId2', state) as string,
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
    const expectedBody: Record<string, string> =
      CREATE_MAPPING_EXPECTED_BODY_USING_GROUP(groupId, state);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/groups/{groupId}/mapping-rules/search', {groupId: groupId}),
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
        expectedBody,
        mappingRuleRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Mapping Rules For Group Unauthorized', async ({request}) => {
    const p = {groupId: state['groupId1'] as string};
    const res = await request.post(
      buildUrl('/groups/{groupId}/mapping-rules/search', p),
      {
        headers: {},
        data: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Mapping Rules For Group Not Found', async ({request}) => {
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

  test('Unassign Mapping Rule From Group', async ({request}) => {
    await test.step('Unassign Mapping Rule', async () => {
      const p = {
        groupId: state['groupId3'] as string,
        mappingRuleId: mappingRuleFromState('groupId3', state) as string,
      };

      await expect(async () => {
        const res = await request.delete(
          buildUrl('/groups/{groupId}/mapping-rules/{mappingRuleId}', p),
          {
            headers: jsonHeaders(),
          },
        );
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Mapping Rules After Deletion', async () => {
      const p = {groupId: state['groupId3'] as string};

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
  });

  test('Unassign Mapping Rule From Group Unauthorized', async ({request}) => {
    const p = {
      groupId: state['groupId2'] as string,
      mappingRuleId: mappingRuleFromState('groupId2', state) as string,
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
      mappingRuleId: mappingRuleFromState('groupId2', state) as string,
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
  });
});
