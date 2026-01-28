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
  assertRequiredFields,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  createMammalProcessInstanceAndDeployMammalDecision,
  DecisionInstance,
} from '@requestHelpers';
import {validateResponse} from '../../../../json-body-assertions';
import {decisionInstanceRequiredFields} from 'utils/beans/requestBeans';

const DECISION_INSTANCES_SEARCH_ENDPOINT = '/decision-instances/search';

test.describe.parallel('Search Decision Instances API Tests', () => {
  let decisionInstances: DecisionInstance[] = [];
  let processInstanceKey: string;

  test.beforeAll(async ({request}) => {
    const result =
      await createMammalProcessInstanceAndDeployMammalDecision(request);
    processInstanceKey = result.instance.processInstanceKey;
    decisionInstances = result.decisions;
  });

  test('Search decision instances - multiple results - success', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(DECISION_INSTANCES_SEARCH_ENDPOINT, {}),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );

      await assertStatusCode(res, 200);
      // this assertion is commented as response shape isn't correct yet. As soon as it's fixed, uncomment it.
      // await validateResponse(
      //   {
      //     path: DECISION_INSTANCES_SEARCH_ENDPOINT,
      //     method: 'POST',
      //     status: '200',
      //   },
      //   res,
      // );

      const body = await res.json();
      for (const element of body.items) {
        assertRequiredFields(element, decisionInstanceRequiredFields);
      }
      expect(body.items.length).toBeGreaterThanOrEqual(3);
      expect(Array.isArray(body.items)).toBe(true);
    }).toPass(defaultAssertionOptions);
  });

  test('Search decision instances - filter by state', async ({request}) => {
    const stateToSearch = 'EVALUATED';
    await expect(async () => {
      const res = await request.post(
        buildUrl(DECISION_INSTANCES_SEARCH_ENDPOINT, {}),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              state: stateToSearch,
            },
          },
        },
      );

      await assertStatusCode(res, 200);
      // this assertion is commented as response shape isn't correct yet. As soon as it's fixed, uncomment it.
      // await validateResponse(
      //   {
      //     path: DECISION_INSTANCES_SEARCH_ENDPOINT,
      //     method: 'POST',
      //     status: '200',
      //   },
      //   res,
      // );

      const body = await res.json();
      for (const element of body.items) {
        assertRequiredFields(element, decisionInstanceRequiredFields);
      }
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.state).toBe('EVALUATED');
      });
      expect(body.items.length).toBeGreaterThanOrEqual(3);
      expect(Array.isArray(body.items)).toBe(true);
    }).toPass(defaultAssertionOptions);
  });

  test('Search decision instances - filter by decisionEvaluationInstanceKey', async ({
    request,
  }) => {
    const decisionEvaluationInstanceKeyToSearch =
      decisionInstances[1].decisionEvaluationInstanceKey;
    await expect(async () => {
      const res = await request.post(
        buildUrl(DECISION_INSTANCES_SEARCH_ENDPOINT, {}),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              decisionEvaluationInstanceKey:
                decisionEvaluationInstanceKeyToSearch,
            },
          },
        },
      );

      await assertStatusCode(res, 200);
      // this assertion is commented as response shape isn't correct yet. As soon as it's fixed, uncomment it.
      // await validateResponse(
      //   {
      //     path: DECISION_INSTANCES_SEARCH_ENDPOINT,
      //     method: 'POST',
      //     status: '200',
      //   },
      //   res,
      // );

      const body = await res.json();
      for (const element of body.items) {
        assertRequiredFields(element, decisionInstanceRequiredFields);
      }
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.decisionEvaluationInstanceKey).toBe(
          decisionEvaluationInstanceKeyToSearch,
        );
      });
      expect(body.items.length).toBeGreaterThanOrEqual(1);
      expect(Array.isArray(body.items)).toBe(true);
    }).toPass(defaultAssertionOptions);
  });

  test('Search decision instances - filter by processInstanceKey', async ({
    request,
  }) => {
    const processInstanceKeyToSearch = processInstanceKey;
    await expect(async () => {
      const res = await request.post(
        buildUrl(DECISION_INSTANCES_SEARCH_ENDPOINT, {}),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: processInstanceKeyToSearch,
            },
          },
        },
      );

      await assertStatusCode(res, 200);
      // this assertion is commented as response shape isn't correct yet. As soon as it's fixed, uncomment it.
      // await validateResponse(
      //   {
      //     path: DECISION_INSTANCES_SEARCH_ENDPOINT,
      //     method: 'POST',
      //     status: '200',
      //   },
      //   res,
      // );

      const body = await res.json();
      expect(body.items.length).toEqual(3);
      for (const element of body.items) {
        assertRequiredFields(element, decisionInstanceRequiredFields);
      }
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.processInstanceKey).toBe(processInstanceKey);
      });
      expect(Array.isArray(body.items)).toBe(true);
    }).toPass(defaultAssertionOptions);
  });

  test('Search decision instances - filter by decisionDefinitionId', async ({
    request,
  }) => {
    const decisionDefinitionIdToSearch =
      decisionInstances[1].decisionDefinitionId;
    await expect(async () => {
      const res = await request.post(
        buildUrl(DECISION_INSTANCES_SEARCH_ENDPOINT, {}),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              decisionDefinitionId: decisionDefinitionIdToSearch,
            },
          },
        },
      );

      await assertStatusCode(res, 200);
      // this assertion is commented as response shape isn't correct yet. As soon as it's fixed, uncomment it.
      // await validateResponse(
      //   {
      //     path: DECISION_INSTANCES_SEARCH_ENDPOINT,
      //     method: 'POST',
      //     status: '200',
      //   },
      //   res,
      // );

      const body = await res.json();
      expect(body.items.length).toBeGreaterThanOrEqual(1);
      for (const element of body.items) {
        assertRequiredFields(element, decisionInstanceRequiredFields);
      }
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.decisionDefinitionId).toBe(decisionDefinitionIdToSearch);
      });
      expect(Array.isArray(body.items)).toBe(true);
    }).toPass(defaultAssertionOptions);
  });

  test('Search decision by multiple filters: processInstanceKey and decisionDefinitionId', async ({
    request,
  }) => {
    const processInstanceKeyToSearch = processInstanceKey;
    const decisionDefinitionIdToSearch =
      decisionInstances[1].decisionDefinitionId;

    await expect(async () => {
      const res = await request.post(
        buildUrl(DECISION_INSTANCES_SEARCH_ENDPOINT, {}),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: processInstanceKeyToSearch,
              decisionDefinitionId: decisionDefinitionIdToSearch,
            },
          },
        },
      );

      await assertStatusCode(res, 200);
      // this assertion is commented as response shape isn't correct yet. As soon as it's fixed, uncomment it.
      // await validateResponse(
      //   {
      //     path: DECISION_INSTANCES_SEARCH_ENDPOINT,
      //     method: 'POST',
      //     status: '200',
      //   },
      //   res,
      // );

      const body = await res.json();
      for (const element of body.items) {
        assertRequiredFields(element, decisionInstanceRequiredFields);
      }
      expect(body.items.length).toBeGreaterThanOrEqual(1);
      expect(body.items[0].decisionDefinitionId).toBe(
        decisionDefinitionIdToSearch,
      );
      expect(body.items[0].processInstanceKey).toBe(processInstanceKeyToSearch);
    }).toPass(defaultAssertionOptions);
  });

  test('Search decision instances - empty result', async ({request}) => {
    const someNotExistingProcessInstanceKey = '99999999999999999';

    await expect(async () => {
      const res = await request.post(
        buildUrl(DECISION_INSTANCES_SEARCH_ENDPOINT, {}),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: someNotExistingProcessInstanceKey,
            },
          },
        },
      );

      await assertStatusCode(res, 200);
      // this assertion is commented as response shape isn't correct yet. As soon as it's fixed, uncomment it.
      // await validateResponse(
      //   {
      //     path: DECISION_INSTANCES_SEARCH_ENDPOINT,
      //     method: 'POST',
      //     status: '200',
      //   },
      //   res,
      // );

      const body = await res.json();
      expect(body.items.length).toEqual(0);
      expect(body.page.totalItems).toEqual(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search decision instances - invalid request - bad request', async ({
    request,
  }) => {
    const someRandomFilterValue = 'meow';
    await expect(async () => {
      const res = await request.post(
        buildUrl(DECISION_INSTANCES_SEARCH_ENDPOINT),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              randomNotExistingFieldName: someRandomFilterValue,
            },
          },
        },
      );

      await assertBadRequest(
        res,
        'Request property [filter.randomNotExistingFieldName] cannot be parsed',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search decision instances - unauthorized request', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(DECISION_INSTANCES_SEARCH_ENDPOINT),
        {
          headers: {
            'Content-Type': 'application/json',
          },
        },
      );

      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });
});
