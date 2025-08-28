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
  assertRequiredFields,
  assertUnauthorizedRequest,
  paginatedResponseFields,
  assertNotFoundRequest,
  assertEqualsForKeys,
  assertBadRequest,
  assertConflictRequest,
  assertPaginatedRequest,
} from '../../../utils/http';
import {defaultAssertionOptions} from '../../../utils/constants';
import {
  CREATE_NEW_MAPPING_RULE,
  mappingRuleBundle,
  mappingRuleRequiredFields,
} from '../../../utils/beans/requestBeans';

test.describe.parallel('Mapping Rules API Tests', () => {
  const state: Record<string, unknown> = {};
  let mappingRule1: Record<string, unknown>;
  let mappingRule2: Record<string, unknown>;
  let mappingRule3: Record<string, unknown>;
  let mappingRule4: Record<string, unknown>;

  test.beforeAll(async ({request}) => {
    mappingRule1 = await mappingRuleBundle(request, state);
    mappingRule2 = await mappingRuleBundle(request, state);
    mappingRule3 = await mappingRuleBundle(request, state);
    mappingRule4 = await mappingRuleBundle(request, state);
  });

  test('Create Mapping Rule', async ({request}) => {
    await expect(async () => {
      const requestBody = CREATE_NEW_MAPPING_RULE();
      const res = await request.post(buildUrl('/mapping-rules'), {
        headers: jsonHeaders(),
        data: requestBody,
      });

      expect(res.status()).toBe(201);
      const json = await res.json();
      assertRequiredFields(json, mappingRuleRequiredFields);
      assertEqualsForKeys(json, requestBody, mappingRuleRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Create Mapping Rule Unauthorized', async ({request}) => {
    const requestBody = CREATE_NEW_MAPPING_RULE();

    const res = await request.post(buildUrl('/mapping-rules'), {
      headers: {},
      data: requestBody,
    });

    await assertUnauthorizedRequest(res);
  });

  test('Create Mapping Rule With Only Claim Name Invalid Body 400', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/mapping-rules'), {
      headers: jsonHeaders(),
      data: {claimName: 'claimName'},
    });

    await assertBadRequest(
      res,
      /.*\b(mappingRuleId|name|claimValue)\b.*/,
      'INVALID_ARGUMENT',
    );
  });

  test('Create Mapping Rule With Only Claim Value Invalid Body 400', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/mapping-rules'), {
      headers: jsonHeaders(),
      data: {claimValue: 'claimValue'},
    });

    await assertBadRequest(
      res,
      /.*\b(mappingRuleId|name|claimName)\b.*/,
      'INVALID_ARGUMENT',
    );
  });

  test('Create Mapping Rule With Only Name Invalid Body 400', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/mapping-rules'), {
      headers: jsonHeaders(),
      data: {name: 'name'},
    });

    await assertBadRequest(
      res,
      /.*\b(mappingRuleId|claimValue|claimName)\b.*/,
      'INVALID_ARGUMENT',
    );
  });

  test('Create Mapping Rule With Only Mapping Rule Id Invalid Body 400', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/mapping-rules'), {
      headers: jsonHeaders(),
      data: {mappingRuleId: 'mappingRuleId'},
    });

    await assertBadRequest(
      res,
      /.*\b(name|claimValue|claimName)\b.*/,
      'INVALID_ARGUMENT',
    );
  });

  test('Create Mapping Rule With Same Id Conflict', async ({request}) => {
    const requestBody = {
      ...CREATE_NEW_MAPPING_RULE(),
      mappingRuleId: mappingRule1.mappingRuleId,
    };

    const res = await request.post(buildUrl('/mapping-rules'), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    await assertConflictRequest(res);
  });

  test('Get Mapping Rule', async ({request}) => {
    await expect(async () => {
      const res = await request.get(
        buildUrl('/mapping-rules/{mappingRuleId}', {
          mappingRuleId: mappingRule1.mappingRuleId as string,
        }),
        {headers: jsonHeaders()},
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, mappingRuleRequiredFields);
      assertEqualsForKeys(
        json,
        mappingRule1.responseBody as Record<string, unknown>,
        mappingRuleRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get Mapping Rule Not Found', async ({request}) => {
    const p = {mappingRuleId: 'does-not-exist'};
    const res = await request.get(
      buildUrl('/mapping-rules/{mappingRuleId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Mapping Rule with id '${p.mappingRuleId}' not found`,
    );
  });

  test('Get Mapping Rule Unauthorized', async ({request}) => {
    const p = {
      mappingRuleId: mappingRule1.mappingRuleId as string,
    };

    const res = await request.get(
      buildUrl('/mapping-rules/{mappingRuleId}', p),
      {headers: {}},
    );

    await assertUnauthorizedRequest(res);
  });

  test('Search Mapping Rules', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/mapping-rules/search', {}), {
        headers: jsonHeaders(),
        data: {},
      });

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 2,
        totalItemGreaterThan: 2,
      });
      const json = await res.json();

      const matchingItem1 = json.items.find(
        (it: {mappingRuleId: string}) =>
          it.mappingRuleId === mappingRule1.mappingRuleId,
      );
      expect(matchingItem1).toBeDefined();
      assertRequiredFields(matchingItem1, mappingRuleRequiredFields);
      assertEqualsForKeys(
        matchingItem1,
        mappingRule1.responseBody as Record<string, unknown>,
        mappingRuleRequiredFields,
      );

      const matchingItem2 = json.items.find(
        (it: {mappingRuleId: string}) =>
          it.mappingRuleId === mappingRule2.mappingRuleId,
      );
      expect(matchingItem2).toBeDefined();
      assertRequiredFields(matchingItem2, mappingRuleRequiredFields);
      assertEqualsForKeys(
        matchingItem2,
        mappingRule2.responseBody as Record<string, unknown>,
        mappingRuleRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Mapping Rules By Id', async ({request}) => {
    const body = {
      filter: {
        mappingRuleId: mappingRule2.mappingRuleId,
      },
    };
    await expect(async () => {
      const res = await request.post(buildUrl('/mapping-rules/search', {}), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      assertRequiredFields(json.items[0], mappingRuleRequiredFields);
      assertEqualsForKeys(
        json.items[0],
        mappingRule2.responseBody as Record<string, unknown>,
        mappingRuleRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Mapping Rules By Name', async ({request}) => {
    const mappingRule = mappingRule1.responseBody as Record<string, unknown>;
    const body = {
      filter: {
        claimName: mappingRule.claimName as string,
      },
    };
    await expect(async () => {
      const res = await request.post(buildUrl('/mapping-rules/search', {}), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertRequiredFields(json.items[0], mappingRuleRequiredFields);
      assertEqualsForKeys(
        json.items[0],
        mappingRule,
        mappingRuleRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Mapping Rules By Claim Value', async ({request}) => {
    const mappingRule = mappingRule2.responseBody as Record<string, unknown>;
    const body = {
      filter: {
        claimValue: mappingRule.claimValue as string,
      },
    };
    await expect(async () => {
      const res = await request.post(buildUrl('/mapping-rules/search', {}), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertRequiredFields(json.items[0], mappingRuleRequiredFields);
      assertEqualsForKeys(
        json.items[0],
        mappingRule,
        mappingRuleRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Mapping Rules By Claim Name', async ({request}) => {
    const mappingRule = mappingRule2.responseBody as Record<string, unknown>;
    const body = {
      filter: {
        mappingRuleId: mappingRule.mappingRuleId as string,
      },
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/mapping-rules/search', {}), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertRequiredFields(json.items[0], mappingRuleRequiredFields);
      assertEqualsForKeys(
        json.items[0],
        mappingRule,
        mappingRuleRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Mapping Rules No Matching Item', async ({request}) => {
    const body = {
      filter: {
        mappingRuleId: 'invalidMappingRule',
      },
    };

    const res = await request.post(buildUrl('/mapping-rules/search', {}), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertPaginatedRequest(res, {
      itemsLengthEqualTo: 0,
      totalItemsEqualTo: 0,
    });
  });

  test('Search Mapping Rules By Multiple Fields', async ({request}) => {
    const mappingRule = mappingRule2.responseBody as Record<string, unknown>;
    const body = {
      filter: {
        mappingRuleId: mappingRule2.mappingRuleId as string,
        claimName: mappingRule.claimName as string,
        claimValue: mappingRule.claimValue as string,
        name: mappingRule.name as string,
      },
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/mapping-rules/search', {}), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertRequiredFields(json.items[0], mappingRuleRequiredFields);
      assertEqualsForKeys(
        json.items[0],
        mappingRule,
        mappingRuleRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Mapping Rules Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/mapping-rules/search', {}), {
      headers: {},
      data: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Update Mapping Rule', async ({request}) => {
    const p = {mappingRuleId: mappingRule3.mappingRuleId as string};
    const mappingRule = mappingRule3.responseBody as Record<string, unknown>;
    const updateBody = {
      claimName: `${mappingRule.claimName}-updated`,
      claimValue: `${mappingRule.claimValue}-updated`,
      name: `${mappingRule.name}-updated`,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/mapping-rules/{mappingRuleId}', p),
        {
          headers: jsonHeaders(),
          data: updateBody,
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, mappingRuleRequiredFields);
      expect(json.claimName).toBe(updateBody.claimName);
      expect(json.name).toBe(updateBody.name);
      expect(json.claimValue).toBe(updateBody.claimValue);
    }).toPass(defaultAssertionOptions);
  });

  test('Update Mapping Rule Not Found', async ({request}) => {
    const p = {mappingRuleId: 'missing-id'};
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', p),
      {
        headers: jsonHeaders(),
        data: {
          claimName: 'new-value',
          claimValue: 'new-value',
          name: 'new-value',
        },
      },
    );

    await assertNotFoundRequest(
      res,
      `Command 'UPDATE' rejected with code 'NOT_FOUND`,
    );
  });

  test('Update Mapping Rule Unauthorized', async ({request}) => {
    const p = {mappingRuleId: mappingRule3.mappingRuleId as string};
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', p),
      {
        headers: {},
        data: {
          claimName: 'new-value',
          claimValue: 'new-value',
          name: 'new-value',
        },
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Delete Mapping Rule', async ({request}) => {
    const p = {mappingRuleId: mappingRule4.mappingRuleId as string};

    await test.step('Delete Mapping Rule', async () => {
      await expect(async () => {
        const res = await request.delete(
          buildUrl('/mapping-rules/{mappingRuleId}', p),
          {headers: jsonHeaders()},
        );
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Get Mapping Rule After Deletion', async () => {
      await expect(async () => {
        const resGet = await request.get(
          buildUrl('/mapping-rules/{mappingRuleId}', p),
          {headers: jsonHeaders()},
        );
        await assertNotFoundRequest(
          resGet,
          `Mapping Rule with id '${p.mappingRuleId}' not found`,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Delete Mapping Rule Not Found', async ({request}) => {
    const p = {mappingRuleId: 'non-existent-rule'};
    const res = await request.delete(
      buildUrl('/mapping-rules/{mappingRuleId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'DELETE' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Delete Mapping Rule Unauthorized', async ({request}) => {
    const p = {mappingRuleId: state['mappingRuleId1'] as string};
    const res = await request.delete(
      buildUrl('/mapping-rules/{mappingRuleId}', p),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });
});
