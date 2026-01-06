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
  assertPaginatedRequest,
  assertBadRequest,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  assertDecisionDefinitionInResponse,
  DECISION_DEFINITION_RESPONSE_FROM_DEPLOYMENT,
  deployDecisionAndStoreResponse,
} from '@requestHelpers';
import {DecisionDeployment} from '@camunda8/sdk/dist/c8/lib/C8Dto';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Search Decision Definitions API Tests', () => {
  const state: Record<string, unknown> = {};
  let decisionDefinition1: DecisionDeployment;
  let decisionDefinition2: DecisionDeployment;
  let expectedBody1: Record<string, unknown>;
  let expectedBody2: Record<string, unknown>;

  test.beforeAll(async () => {
    await deployDecisionAndStoreResponse(
      state,
      '1',
      './resources/simpleDecisionTable1.dmn',
    );
    await deployDecisionAndStoreResponse(
      state,
      '2',
      './resources/simpleDecisionTable2.dmn',
    );
    decisionDefinition1 = state['decisionDefinition1'] as DecisionDeployment;
    decisionDefinition2 = state['decisionDefinition2'] as DecisionDeployment;
    expectedBody1 =
      DECISION_DEFINITION_RESPONSE_FROM_DEPLOYMENT(decisionDefinition1);
    expectedBody2 =
      DECISION_DEFINITION_RESPONSE_FROM_DEPLOYMENT(decisionDefinition2);
  });

  test('Search Decision Definitions', async ({request}) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 0,
        totalItemGreaterThan: 0,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by decisionDefinitionId', async ({
    request,
  }) => {
    const filter = {
      decisionDefinitionId: decisionDefinition1.decisionDefinitionId,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by name', async ({request}) => {
    const filter = {
      name: decisionDefinition1.name as string,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by version', async ({request}) => {
    const filter = {
      version: 1,
    };
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 1,
        totalItemGreaterThan: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by decisionRequirementsId', async ({
    request,
  }) => {
    const filter = {
      decisionRequirementsId:
        decisionDefinition2.decisionRequirementsId as string,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by decisionDefinitionKey', async ({
    request,
  }) => {
    const filter = {
      decisionDefinitionKey:
        decisionDefinition2.decisionDefinitionKey as string,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by decisionRequirementsKey', async ({
    request,
  }) => {
    const filter = {
      decisionRequirementsKey:
        decisionDefinition1.decisionRequirementsKey as string,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by tenantId', async ({request}) => {
    const filter = {
      tenantId: '<default>',
    };
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 1,
        totalItemGreaterThan: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by multiple filters', async ({request}) => {
    const filter = {
      decisionRequirementsId:
        decisionDefinition2.decisionRequirementsId as string,
      decisionDefinitionId: decisionDefinition2.decisionDefinitionId as string,
      name: decisionDefinition2.name as string,
      version: decisionDefinition2.version as number,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions - No Matching Item', async ({request}) => {
    const filter = {decisionDefinitionId: 'non-existent-id'};

    const res = await request.post(
      buildUrl('/decision-definitions/search', {}),
      {
        headers: jsonHeaders(),
        data: {filter},
      },
    );
    await assertPaginatedRequest(res, {
      itemsLengthEqualTo: 0,
      totalItemsEqualTo: 0,
    });
  });

  test('Search Decision Definitions Unauthorized', async ({request}) => {
    const res = await request.post(
      buildUrl('/decision-definitions/search', {}),
      {
        headers: {},
        data: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Decision Definitions Sort By decisionDefinitionId ASC', async ({
    request,
  }) => {
    const sort = [
      {
        field: 'decisionDefinitionId',
        order: 'ASC',
      },
    ];
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {sort},
        },
      );

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 1,
        totalItemGreaterThan: 1,
      });
      const json = await res.json();
      expect(json.items[0].decisionDefinitionId).toBe(
        decisionDefinition1.decisionDefinitionId,
      );
      expect(json.items[1].decisionDefinitionId).toBe(
        decisionDefinition2.decisionDefinitionId,
      );
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions Sort By decisionDefinitionId DESC', async ({
    request,
  }) => {
    const sort = [
      {
        field: 'decisionDefinitionId',
        order: 'DESC',
      },
    ];
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {sort},
        },
      );

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 1,
        totalItemGreaterThan: 1,
      });
      const json = await res.json();
      expect(json.items[0].decisionDefinitionId).toBe(
        decisionDefinition2.decisionDefinitionId,
      );
      expect(json.items[1].decisionDefinitionId).toBe(
        decisionDefinition1.decisionDefinitionId,
      );
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions Sort Invalid Body', async ({request}) => {
    const sort = [
      {
        order: 'DESC',
      },
    ];
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {sort},
        },
      );
      await assertBadRequest(
        res,
        'Sort field must not be null.',
        'INVALID_ARGUMENT',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions Sort By name ASC', async ({request}) => {
    const sort = [
      {
        field: 'name',
        order: 'ASC',
      },
    ];
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {sort},
        },
      );

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 1,
        totalItemGreaterThan: 1,
      });
      const json = await res.json();

      // Find our deployed decisions in the results
      const def1Index = json.items.findIndex(
        (item: {decisionDefinitionKey: string}) =>
          item.decisionDefinitionKey ===
          decisionDefinition1.decisionDefinitionKey,
      );
      const def2Index = json.items.findIndex(
        (item: {decisionDefinitionKey: string}) =>
          item.decisionDefinitionKey ===
          decisionDefinition2.decisionDefinitionKey,
      );

      // Verify both exist
      expect(def1Index).toBeGreaterThanOrEqual(0);
      expect(def2Index).toBeGreaterThanOrEqual(0);

      // Verify correct sort order: GenerationsDecision (def2) should come before SingleTableDecision (def1)
      expect(def2Index).toBeLessThan(def1Index);

      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions Sort By name DESC', async ({request}) => {
    const sort = [
      {
        field: 'name',
        order: 'DESC',
      },
    ];
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {sort},
        },
      );

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 1,
        totalItemGreaterThan: 1,
      });
      const json = await res.json();

      // Find our deployed decisions in the results
      const def1Index = json.items.findIndex(
        (item: {decisionDefinitionKey: string}) =>
          item.decisionDefinitionKey ===
          decisionDefinition1.decisionDefinitionKey,
      );
      const def2Index = json.items.findIndex(
        (item: {decisionDefinitionKey: string}) =>
          item.decisionDefinitionKey ===
          decisionDefinition2.decisionDefinitionKey,
      );

      // Verify both exist
      expect(def1Index).toBeGreaterThanOrEqual(0);
      expect(def2Index).toBeGreaterThanOrEqual(0);

      // Verify correct sort order: SingleTableDecision (def1) should come before GenerationsDecision (def2) in DESC
      expect(def1Index).toBeLessThan(def2Index);

      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });
});
