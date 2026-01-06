/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {cancelProcessInstance, deploy} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {
  createTwoIncidentsInOneProcess,
  createIncidentsInTwoProcesses,
  createTwoDifferentIncidentsInOneProcess,
} from '@requestHelpers';

const INCIDENT_SEARCH_ENDPOINT = '/incidents/search';

test.describe.parallel('Search Incidents API Tests', () => {
  const processInstanceKeys: string[] = [];

  test.beforeAll(async () => {
    await deploy([
      './resources/processWithAnError.bpmn',
      './resources/loanApprovalProcess.bpmn',
      './resources/MultipleErrorTypesProcess.bpmn',
    ]);
  });

  test.afterAll(async () => {
    for (const key of processInstanceKeys) {
      await cancelProcessInstance(key);
    }
  });

  test('Search Incidents Success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await createTwoIncidentsInOneProcess(localState, request);
    processInstanceKeys.push(localState['processInstanceKey'] as string);

    await expect(async () => {
      const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {},
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: INCIDENT_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      expect(Array.isArray(body.items)).toBe(true);
      expect(body.items.length).toBeGreaterThan(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Incidents within multiple process instances Success', async ({
    request,
  }) => {
    const localState: Record<string, unknown> = {};
    await createIncidentsInTwoProcesses(localState, request);
    for (const element of localState['processInstanceKey'] as string[]) {
      processInstanceKeys.push(element);
    }

    await expect(async () => {
      const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {},
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: INCIDENT_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(3);
      expect(Array.isArray(body.items)).toBe(true);
      expect(body.items.length).toBeGreaterThan(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Incidents With IncidentKey Filter Success', async ({
    request,
  }) => {
    const localState: Record<string, unknown> = {};
    await createTwoIncidentsInOneProcess(localState, request);
    processInstanceKeys.push(localState['processInstanceKey'] as string);
    const incidentKeys = localState['incidentKeys'] as string[];

    await expect(async () => {
      const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            incidentKey: incidentKeys[0],
          },
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: INCIDENT_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      expect(body.items.length).toBeGreaterThan(0);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.incidentKey).toEqual(incidentKeys[0]);
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Incidents With Error Type Filter Success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await createTwoIncidentsInOneProcess(localState, request);
    processInstanceKeys.push(localState['processInstanceKey'] as string);

    await expect(async () => {
      const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            errorType: 'EXTRACT_VALUE_ERROR',
          },
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: INCIDENT_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(6);
      expect(body.items.length).toBeGreaterThan(0);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.errorType).toBe('EXTRACT_VALUE_ERROR');
      });
    }).toPass(defaultAssertionOptions);

    await expect(async () => {
      const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            errorType: 'CALLED_DECISION_ERROR',
          },
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: INCIDENT_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      expect(body.items.length).toBeGreaterThan(0);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.errorType).toBe('CALLED_DECISION_ERROR');
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Incidents With Error Type Filter and Process Instance Key Success', async ({
    request,
  }) => {
    const localState: Record<string, unknown> = {};
    await createTwoDifferentIncidentsInOneProcess(localState, request);
    processInstanceKeys.push(localState['processInstanceKey'] as string);

    await test.step('Search for EXTRACT_VALUE_ERROR incidents', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
          headers: jsonHeaders(),
          data: {
            filter: {
              errorType: 'EXTRACT_VALUE_ERROR',
              processInstanceKey: localState['processInstanceKey'] as string,
            },
          },
        });

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: INCIDENT_SEARCH_ENDPOINT,
            method: 'POST',
            status: '200',
          },
          res,
        );

        const body = await res.json();
        expect(body.page.totalItems).toEqual(1);
        expect(body.items.length).toBeGreaterThan(0);
        body.items.forEach((item: Record<string, unknown>) => {
          expect(item.errorType).toBe('EXTRACT_VALUE_ERROR');
        });
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search for CALLED_DECISION_ERROR incidents', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
          headers: jsonHeaders(),
          data: {
            filter: {
              errorType: 'CALLED_DECISION_ERROR',
              processInstanceKey: localState['processInstanceKey'] as string,
            },
          },
        });

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: INCIDENT_SEARCH_ENDPOINT,
            method: 'POST',
            status: '200',
          },
          res,
        );

        const body = await res.json();
        expect(body.page.totalItems).toEqual(1);
        expect(body.items.length).toBeGreaterThan(0);
        body.items.forEach((item: Record<string, unknown>) => {
          expect(item.errorType).toBe('CALLED_DECISION_ERROR');
        });
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Search Incidents Unauthorized', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
        headers: {
          'Content-Type': 'application/json',
        },
        data: {},
      });

      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Incidents Invalid Filter', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            meow: 'meowValue',
          },
        },
      });

      await assertBadRequest(
        res,
        'Request property [filter.meow] cannot be parsed',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Incidents Empty Result success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await createTwoIncidentsInOneProcess(localState, request);
    processInstanceKeys.push(localState['processInstanceKey'] as string);

    await expect(async () => {
      const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: '225179981', // non-existing
          },
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: INCIDENT_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toEqual(0);
      expect(body.items.length).toEqual(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Incident Pagination Limit 1', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          page: {
            limit: 1,
          },
        },
      });

      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(11);
      expect(body.items.length).toBe(1);
    }).toPass(defaultAssertionOptions);
  });
});
