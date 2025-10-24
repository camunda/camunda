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
  assertUnauthorizedRequest,
  assertNotFoundRequest,
  assertConflictRequest,
  assertPaginatedRequest,
  assertRequiredFields,
  assertEqualsForKeys,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  assignRolesToMappingRules,
  createMappingRule,
  createRole,
  createRoleAndStoreResponseFields,
  mappingRuleIdFromState,
  mappingRuleNameFromState,
} from '@requestHelpers';
import {
  MAPPING_RULE_EXPECTED_BODY_USING_STATE,
  mappingRuleRequiredFields,
} from '../../../../utils/beans/requestBeans';
import {cleanupRoles} from '../../../../utils/rolesCleanup';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Role Mapping Rules API Tests', () => {
  const state: Record<string, unknown> = {};
  const createdRoleIds: string[] = [];

  test.beforeAll(async ({request}) => {
    await createRoleAndStoreResponseFields(request, 3, state);
    await assignRolesToMappingRules(
      request,
      2,
      state['roleId2'] as string,
      state,
    );
    await assignRolesToMappingRules(
      request,
      1,
      state['roleId3'] as string,
      state,
    );

    createdRoleIds.push(
      ...Object.entries(state)
        .filter(([key]) => key.startsWith('roleId'))
        .map(([, value]) => value as string),
    );
  });

  test.afterAll(async ({request}) => {
    await cleanupRoles(request, createdRoleIds);
  });

  test('Assign Role To Mapping Rule', async ({request}) => {
    const body = await createMappingRule(request);
    const p = {
      roleId: state['roleId1'] as string,
      mappingRuleId: body.mappingRuleId as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/mapping-rules/{mappingRuleId}', p),
        {headers: jsonHeaders()},
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Role To Mapping Rule Non Existent Mapping Rule Not Found', async ({
    request,
  }) => {
    const p = {
      roleId: state['roleId1'] as string,
      mappingRuleId: 'invalidMappingRuleId',
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/mapping-rules/{mappingRuleId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign Role To Mapping Rule Non Existent Role Not Found', async ({
    request,
  }) => {
    const p = {
      roleId: 'invalidRoleId',
      mappingRuleId: mappingRuleIdFromState('roleId1', state) as string,
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/mapping-rules/{mappingRuleId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign Role To Mapping Rule Unauthorized', async ({request}) => {
    const p = {
      roleId: state['roleId1'] as string,
      mappingRuleId: mappingRuleIdFromState('roleId1', state) as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/mapping-rules/{mappingRuleId}', p),
        {headers: {}},
      );
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Already Added Mapping Rule To Role Conflict', async ({
    request,
  }) => {
    const p = {
      roleId: state['roleId1'] as string,
      mappingRuleId: mappingRuleIdFromState('roleId2', state) as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/mapping-rules/{mappingRuleId}', p),
        {headers: jsonHeaders()},
      );
      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Unassign Role From Mapping Rule', async ({request}) => {
    const p = {
      roleId: state['roleId3'] as string,
      mappingRuleId: mappingRuleIdFromState('roleId3', state) as string,
    };
    await test.step('Unassign Role From Mapping Rule', async () => {
      await expect(async () => {
        const res = await request.delete(
          buildUrl('/roles/{roleId}/mapping-rules/{mappingRuleId}', p),
          {headers: jsonHeaders()},
        );
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Roles For Mapping Rule After Deletion', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/roles/{roleId}/mapping-rules/search', p),
          {headers: jsonHeaders(), data: {}},
        );
        await assertPaginatedRequest(res, {
          totalItemsEqualTo: 0,
          itemsLengthEqualTo: 0,
        });
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Unassign Role From Mapping Rule Unauthorized', async ({request}) => {
    const p = {
      roleId: state['roleId2'] as string,
      mappingRuleId: mappingRuleIdFromState('roleId2', state) as string,
    };
    const res = await request.delete(
      buildUrl('/roles/{roleId}/mapping-rules/{mappingRuleId}', p),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign Role From Mapping Rule Non Existent Mapping Rule Not Found', async ({
    request,
  }) => {
    const p = {
      roleId: state['roleId2'] as string,
      mappingRuleId: 'invalidMappingRuleId',
    };

    const res = await request.delete(
      buildUrl('/roles/{roleId}/mapping-rules/{mappingRuleId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Unassign Role From Mapping Rule Non Existent Role Not Found', async ({
    request,
  }) => {
    const p = {
      roleId: 'invalidRoleId',
      mappingRuleId: mappingRuleIdFromState('roleId2', state) as string,
    };

    const res = await request.delete(
      buildUrl('/roles/{roleId}/mapping-rules/{mappingRuleId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Search Role Mapping Rules', async ({request}) => {
    const mappingRule1 = mappingRuleIdFromState('roleId2', state, 1) as string;
    const mappingRule2 = mappingRuleIdFromState('roleId2', state, 2) as string;
    const p = {
      roleId: state['roleId2'] as string,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/roles/{roleId}/mapping-rules/search', p),
        {headers: jsonHeaders(), data: {}},
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 2,
        totalItemsEqualTo: 2,
      });
      const json = await res.json();
      const matchingItem1 = json.items.find(
        (it: {mappingRuleId: string}) => it.mappingRuleId === mappingRule1,
      );

      expect(matchingItem1).toBeDefined();
      assertRequiredFields(matchingItem1, mappingRuleRequiredFields);
      assertEqualsForKeys(
        matchingItem1,
        MAPPING_RULE_EXPECTED_BODY_USING_STATE('roleId2', state, 1),
        mappingRuleRequiredFields,
      );

      const matchingItem2 = json.items.find(
        (it: {mappingRuleId: string}) => it.mappingRuleId === mappingRule2,
      );
      expect(matchingItem2).toBeDefined();
      assertRequiredFields(matchingItem2, mappingRuleRequiredFields);
      assertEqualsForKeys(
        matchingItem2,
        MAPPING_RULE_EXPECTED_BODY_USING_STATE('roleId2', state, 2),
        mappingRuleRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Role Mapping Rules Filter By mappingRuleId', async ({
    request,
  }) => {
    const p = {roleId: state.roleId2 as string};
    const filterBody = {
      filter: {
        mappingRuleId: mappingRuleIdFromState('roleId2', state) as string,
      },
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/roles/{roleId}/mapping-rules/search', p),
        {headers: jsonHeaders(), data: filterBody},
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      const item = json.items[0];
      assertRequiredFields(item, mappingRuleRequiredFields);
      assertEqualsForKeys(
        item,
        MAPPING_RULE_EXPECTED_BODY_USING_STATE('roleId2', state, 1),
        mappingRuleRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Role Mapping Rules Filter By name', async ({request}) => {
    const p = {roleId: state.roleId2 as string};
    const filterBody = {
      filter: {
        name: mappingRuleNameFromState('roleId2', state, 2) as string,
      },
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/roles/{roleId}/mapping-rules/search', p),
        {headers: jsonHeaders(), data: filterBody},
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      const item = json.items[0];
      assertRequiredFields(item, mappingRuleRequiredFields);
      assertEqualsForKeys(
        item,
        MAPPING_RULE_EXPECTED_BODY_USING_STATE('roleId2', state, 2),
        mappingRuleRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Role Mapping Rules Filter By multiple fields', async ({
    request,
  }) => {
    const p = {roleId: state.roleId2 as string};
    const filterBody = {
      filter: {
        name: mappingRuleNameFromState('roleId2', state, 2) as string,
        mappingRuleId: mappingRuleIdFromState('roleId2', state, 2) as string,
      },
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/roles/{roleId}/mapping-rules/search', p),
        {headers: jsonHeaders(), data: filterBody},
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      const item = json.items[0];
      assertRequiredFields(item, mappingRuleRequiredFields);
      assertEqualsForKeys(
        item,
        MAPPING_RULE_EXPECTED_BODY_USING_STATE('roleId2', state, 2),
        mappingRuleRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Role Mapping Rules Filter Non Matching Returns Empty', async ({
    request,
  }) => {
    const p = {roleId: state.roleId2 as string};
    const body = {
      filter: {
        mappingRuleId: 'non-existent-mapping-rule',
      },
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/roles/{roleId}/mapping-rules/search', p),
        {headers: jsonHeaders(), data: body},
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 0,
        totalItemsEqualTo: 0,
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Role Mapping Rules Role With No Assignments Returns Empty', async ({
    request,
  }) => {
    const lonelyRole = await createRole(request);
    createdRoleIds.push(lonelyRole.roleId as string);
    const p = {roleId: lonelyRole.roleId as string};

    await expect(async () => {
      const res = await request.post(
        buildUrl('/roles/{roleId}/mapping-rules/search', p),
        {headers: jsonHeaders(), data: {}},
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 0,
        totalItemsEqualTo: 0,
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Role Mapping Rules Unauthorized', async ({request}) => {
    const p = {roleId: state.roleId2 as string};
    const res = await request.post(
      buildUrl('/roles/{roleId}/mapping-rules/search', p),
      {headers: {}, data: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Role Mapping Rules Role Not Found', async ({request}) => {
    const p = {roleId: 'invalid-role-id'};
    const res = await request.post(
      buildUrl('/roles/{roleId}/mapping-rules/search', p),
      {headers: jsonHeaders(), data: {}},
    );
    await assertPaginatedRequest(res, {
      itemsLengthEqualTo: 0,
      totalItemsEqualTo: 0,
    });
  });
});
