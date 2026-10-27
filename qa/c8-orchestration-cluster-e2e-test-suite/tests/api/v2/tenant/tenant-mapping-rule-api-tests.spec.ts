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
  assertStatusCode,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  assertMappingRulesInResponse,
  assignMappingRulesToTenant,
  createMappingRule,
  createTenant,
  mappingRuleIdFromState,
} from '@requestHelpers';
import {MAPPING_RULES_EXPECTED_BODY} from '../../../../utils/beans/requestBeans';
import {validateResponse} from 'json-body-assertions';

test.describe.serial('Tenant Mapping Rule API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createTenant(request, state, '1');
    await createTenant(request, state, '2');
    await createTenant(request, state, '3');
    await assignMappingRulesToTenant(request, 2, 'tenantId1', state);
    await assignMappingRulesToTenant(request, 1, 'tenantId2', state);
    await assignMappingRulesToTenant(request, 3, 'tenantId3', state);
  });

  test('Assign Mapping Rule To Tenant - Success', async ({request}) => {
    const ruleBody = await createMappingRule(request);
    const p = {
      mappingRuleId: ruleBody.mappingRuleId as string,
      tenantId: state['tenantId1'] as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/mapping-rules/{mappingRuleId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res, 204);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Mapping Rule To Tenant Non Existent Mapping Rule - Not Found', async ({
    request,
  }) => {
    const stateParams: Record<string, string> = {
      mappingRuleId: 'invalidMappingRuleId',
      tenantId: state['tenantId1'] as string,
    };

    const res = await request.put(
      buildUrl(
        '/tenants/{tenantId}/mapping-rules/{mappingRuleId}',
        stateParams,
      ),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign Mapping Rule To Tenant Non Existent Tenant - Not Found', async ({
    request,
  }) => {
    const stateParams: Record<string, string> = {
      mappingRuleId: mappingRuleIdFromState('tenantId1', state) as string,
      tenantId: 'invalidTenantId',
    };

    const res = await request.put(
      buildUrl(
        '/tenants/{tenantId}/mapping-rules/{mappingRuleId}',
        stateParams,
      ),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign Mapping Rule To Tenant - Unauthorized', async ({request}) => {
    const stateParams: Record<string, string> = {
      mappingRuleId: mappingRuleIdFromState('tenantId1', state) as string,
      tenantId: state['tenantId1'] as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl(
          '/tenants/{tenantId}/mapping-rules/{mappingRuleId}',
          stateParams,
        ),
        {
          headers: {},
        },
      );
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Already Added Mapping Rule To Tenant - Conflict', async ({
    request,
  }) => {
    const stateParams: Record<string, string> = {
      mappingRuleId: mappingRuleIdFromState('tenantId1', state) as string,
      tenantId: state['tenantId1'] as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl(
          '/tenants/{tenantId}/mapping-rules/{mappingRuleId}',
          stateParams,
        ),
        {
          headers: jsonHeaders(),
        },
      );

      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Unassign Mapping Rule From Tenant - Success', async ({request}) => {
    const p = {
      mappingRuleId: mappingRuleIdFromState('tenantId2', state) as string,
      tenantId: state['tenantId2'] as string,
    };

    await test.step('Unassign Mapping Rule From Tenant', async () => {
      await expect(async () => {
        const res = await request.delete(
          buildUrl('/tenants/{tenantId}/mapping-rules/{mappingRuleId}', p),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(res, 204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Tenant Mapping Rules After Deletion', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/tenants/{tenantId}/mapping-rules/search', p),
          {
            headers: jsonHeaders(),
            data: {},
          },
        );

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/tenants/{tenantId}/mapping-rules/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.page.totalItems).toBe(0);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Unassign Mapping Rule From Tenant - Unauthorized', async ({
    request,
  }) => {
    const p = {
      mappingRuleId: mappingRuleIdFromState('tenantId2', state) as string,
      tenantId: state['tenantId2'] as string,
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/mapping-rules/{mappingRuleId}', p),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign Mapping Rule From Tenant Non Existent Mapping Rule - Not Found', async ({
    request,
  }) => {
    const p = {
      mappingRuleId: 'invalidMappingRuleId',
      tenantId: state['tenantId2'] as string,
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/mapping-rules/{mappingRuleId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Unassign Mapping Rule From Tenant Non Existent Tenant - Not Found', async ({
    request,
  }) => {
    const p = {
      mappingRuleId: mappingRuleIdFromState('tenantId2', state) as string,
      tenantId: 'invalidTenantId',
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/mapping-rules/{mappingRuleId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Search Tenant Mapping Rules - Success', async ({request}) => {
    const p = {tenantId: state['tenantId3'] as string};
    const rule1 = mappingRuleIdFromState('tenantId3', state, 1);
    const rule2 = mappingRuleIdFromState('tenantId3', state, 2);
    const rule3 = mappingRuleIdFromState('tenantId3', state, 3);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/tenants/{tenantId}/mapping-rules/search', p),
        {headers: jsonHeaders(), data: {}},
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/tenants/{tenantId}/mapping-rules/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 3,
        totalItemsEqualTo: 3,
      });
      const json = await res.json();
      assertMappingRulesInResponse(
        json,
        MAPPING_RULES_EXPECTED_BODY(rule1),
        rule1,
      );
      assertMappingRulesInResponse(
        json,
        MAPPING_RULES_EXPECTED_BODY(rule2),
        rule2,
      );
      assertMappingRulesInResponse(
        json,
        MAPPING_RULES_EXPECTED_BODY(rule3),
        rule3,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenant Mapping Rules Tenant With No Assignments Returns Empty - Success', async ({
    request,
  }) => {
    const tenant = await createTenant(request);
    const p = {tenantId: tenant.tenantId as string};

    await expect(async () => {
      const res = await request.post(
        buildUrl('/tenants/{tenantId}/mapping-rules/search', p),
        {headers: jsonHeaders(), data: {}},
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 0,
        totalItemsEqualTo: 0,
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/tenants/{tenantId}/mapping-rules/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenant Mapping Rules - Unauthorized', async ({request}) => {
    const p = {tenantId: state['tenantId3'] as string};
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/mapping-rules/search', p),
      {headers: {}, data: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Tenant Mapping Rules Tenant Not Found - Not Found', async ({
    request,
  }) => {
    const p = {tenantId: 'invalid-tenant-id'};
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/mapping-rules/search', p),
      {headers: jsonHeaders(), data: {}},
    );
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/tenants/{tenantId}/mapping-rules/search',
        method: 'POST',
        status: '200',
      },
      res,
    );
    await assertPaginatedRequest(res, {
      itemsLengthEqualTo: 0,
      totalItemsEqualTo: 0,
    });
  });
});
