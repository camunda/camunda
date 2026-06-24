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
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {
  createProcessInstanceWithAJob,
  createSingleIncidentProcessInstance,
  activateJobToObtainAValidJobKey,
} from '@requestHelpers';
import {waitForAssertion} from 'utils/waitForAssertion';

const INCIDENT_SEARCH_ENDPOINT = '/incidents/search';

test.describe.parallel('Resolve Incidents API Tests', () => {
  const processInstanceKeys: string[] = [];

  test.beforeAll(async () => {
    await deploy([
      './resources/singleIncidentProcess.bpmn',
      './resources/FlakyWorker.bpmn',
    ]);
  });

  test.afterAll(async () => {
    for (const key of processInstanceKeys) {
      await cancelProcessInstance(key);
    }
  });

  test('Resolve Incident success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await createSingleIncidentProcessInstance(localState, request);
    const elementInstanceKey = {key: ''};
    const incidentKeys = localState['incidentKeys'] as string[];
    const incidentKey = incidentKeys[0];

    await test.step('Search for incidents and verify the number of incidents and its state', async () => {
      await expect(async () => {
        const searchRes = await request.post(
          buildUrl(INCIDENT_SEARCH_ENDPOINT),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: localState['processInstanceKey'] as string,
              },
            },
          },
        );

        await assertStatusCode(searchRes, 200);
        await validateResponse(
          {
            path: INCIDENT_SEARCH_ENDPOINT,
            method: 'POST',
            status: '200',
          },
          searchRes,
        );

        const body = await searchRes.json();
        expect(body.page.totalItems).toEqual(1);
        expect(body.items.length).toBeGreaterThan(0);
        expect(body.items[0].state).toBe('ACTIVE');
        expect(body.items[0].incidentKey).toBe(incidentKey);
        elementInstanceKey['key'] = body.items[0].elementInstanceKey;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Update element instance variables', async () => {
      const updateRes = await request.put(
        buildUrl(`/element-instances/${elementInstanceKey['key']}/variables`),
        {
          headers: jsonHeaders(),
          data: {
            variables: {
              goUp: 6,
            },
          },
        },
      );
      await assertStatusCode(updateRes, 204);
    });

    await test.step('Resolve Incident', async () => {
      const resolveRes = await request.post(
        buildUrl(`/incidents/${incidentKey}/resolution`),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(resolveRes, 204);
    });

    await test.step('Search for incidents and verify the number of incidents and its state after resolving the incident', async () => {
      await expect(async () => {
        const searchRes = await request.post(
          buildUrl(INCIDENT_SEARCH_ENDPOINT),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: localState['processInstanceKey'] as string,
              },
            },
          },
        );

        await assertStatusCode(searchRes, 200);
        await validateResponse(
          {
            path: INCIDENT_SEARCH_ENDPOINT,
            method: 'POST',
            status: '200',
          },
          searchRes,
        );

        const body = await searchRes.json();
        expect(body.page.totalItems).toEqual(1);
        expect(body.items.length).toBeGreaterThan(0);
        expect(body.items[0].state).toBe('RESOLVED');
        expect(body.items[0].incidentKey).toBe(incidentKey);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Resolve Incident - not found', async ({request}) => {
    await expect(async () => {
      const someWrongValue = '9675540027';
      const res = await request.post(
        buildUrl(`/incidents/${someWrongValue}/resolution`),
        {
          headers: jsonHeaders(),
        },
      );

      await assertNotFoundRequest(
        res,
        "Command 'RESOLVE' rejected with code 'NOT_FOUND': Expected to resolve incident with key '9675540027', but no such incident was found",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Resolve Incident - bad request', async ({request}) => {
    await expect(async () => {
      const someInvalidValue = 'meow';
      const res = await request.post(
        buildUrl(`/incidents/${someInvalidValue}/resolution`),
        {
          headers: jsonHeaders(),
        },
      );

      await assertBadRequest(
        res,
        "Failed to convert 'incidentKey' with value: 'meow'",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Resolve Incident - unauthorized', async ({request}) => {
    const someNotExistingValue = '9999999999999999';
    await expect(async () => {
      const res = await request.post(
        buildUrl(`/incidents/${someNotExistingValue}/resolution`),
        {
          headers: {
            'Content-Type': 'application/json',
          },
          data: {},
        },
      );

      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Resolve Incident with a job - success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await createProcessInstanceWithAJob(localState);
    processInstanceKeys.push(localState['processInstanceKey'] as string);
    const incidentKey = {key: ''};

    const jobKey = await activateJobToObtainAValidJobKey(request, 'test-fail');

    await test.step('Fail job to create incident', async () => {
      const failRes = await request.post(buildUrl(`/jobs/${jobKey}/failure`), {
        headers: jsonHeaders(),
        data: {
          retries: 0,
          errorMessage: 'Simulated failure',
        },
      });
      await assertStatusCode(failRes, 204);
    });

    await test.step('Search for incidents and verify the number of incidents and its state', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(async () => {
            const searchRes = await request.post(
              buildUrl(INCIDENT_SEARCH_ENDPOINT),
              {
                headers: jsonHeaders(),
                data: {
                  filter: {
                    processInstanceKey: localState[
                      'processInstanceKey'
                    ] as string,
                  },
                },
              },
            );

            await assertStatusCode(searchRes, 200);
            await validateResponse(
              {
                path: INCIDENT_SEARCH_ENDPOINT,
                method: 'POST',
                status: '200',
              },
              searchRes,
            );

            const body = await searchRes.json();
            expect(body.page.totalItems).toEqual(1);
            expect(body.items.length).toBeGreaterThan(0);
            expect(body.items[0].state).toBe('ACTIVE');
            incidentKey['key'] = body.items[0].incidentKey;
          }).toPass(defaultAssertionOptions);
        },
        onFailure: async () => {},
        maxRetries: 10,
      });
    });

    const incidentKeyValue = incidentKey['key'];

    await test.step('Update job to create additional retry', async () => {
      const updateRes = await request.patch(buildUrl(`/jobs/${jobKey}`), {
        headers: jsonHeaders(),
        data: {
          changeset: {retries: 1},
        },
      });
      await assertStatusCode(updateRes, 204);
    });

    await test.step('Resolve Incident', async () => {
      const resolveRes = await request.post(
        buildUrl(`/incidents/${incidentKeyValue}/resolution`),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(resolveRes, 204);
    });

    await test.step('Search for incidents and verify the number of incidents and its state after resolving the incident', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(async () => {
            const searchRes = await request.post(
              buildUrl(INCIDENT_SEARCH_ENDPOINT),
              {
                headers: jsonHeaders(),
                data: {
                  filter: {
                    processInstanceKey: localState[
                      'processInstanceKey'
                    ] as string,
                  },
                },
              },
            );

            await assertStatusCode(searchRes, 200);
            await validateResponse(
              {
                path: INCIDENT_SEARCH_ENDPOINT,
                method: 'POST',
                status: '200',
              },
              searchRes,
            );

            const body = await searchRes.json();
            expect(body.page.totalItems).toEqual(1);
            expect(body.items.length).toBeGreaterThan(0);
            expect(body.items[0].state).toBe('RESOLVED');
            expect(body.items[0].incidentKey).toBe(incidentKey.key);
          }).toPass(defaultAssertionOptions);
        },
        onFailure: async () => {},
        maxRetries: 3,
      });
    });
  });
});
