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
  createTwoDifferentIncidentsInOneProcess,
  createTwoIncidentsInOneProcess,
} from '../../../../utils/requestHelpers/incident-requestHelpers';

test.describe.parallel('Search Incidents API Tests', () => {
  const processInstanceKeys: string[] = [];

  test.beforeAll(async () => {
    await deploy([
      './resources/MultipleErrorTypesProcess.bpmn',
      './resources/processWithAnError.bpmn',
    ]);
  });

  test.afterAll(async () => {
    for (const key of processInstanceKeys) {
      await cancelProcessInstance(key);
    }
  });

  test('Get Incident Success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await createTwoDifferentIncidentsInOneProcess(localState, request);
    processInstanceKeys.push(localState['processInstanceKey'] as string);
    const expectedErrorTypes = ['CALLED_DECISION_ERROR', 'EXTRACT_VALUE_ERROR'];

    for (const incidentKey of localState['incidentKeys'] as string[]) {
      await expect(async () => {
        const res = await request.get(buildUrl(`/incidents/${incidentKey}`), {
          headers: jsonHeaders(),
        });

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/incidents/{incidentKey}',
            method: 'GET',
            status: '200',
          },
          res,
        );
        localState['responseJson'] = await res.json();
      }).toPass(defaultAssertionOptions);
      const json = localState['responseJson'] as {[key: string]: string};
      expect(json.incidentKey).toBe(incidentKey);
      expect(json.processInstanceKey).toBe(
        localState['processInstanceKey'] as string,
      );
      expect(expectedErrorTypes).toContain(json.errorType);
    }
  });

  test('Get Incidents Unauthorized', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await createTwoIncidentsInOneProcess(localState, request);
    processInstanceKeys.push(localState['processInstanceKey'] as string);

    const incidentKeys = localState['incidentKeys'] as string[];
    const firstIncidentKey = incidentKeys[0];

    await expect(async () => {
      const res = await request.get(
        buildUrl(`/incidents/${firstIncidentKey}`),
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

  test('Get Incidents Not Found', async ({request}) => {
    await expect(async () => {
      const someWrongValue = '9675540027';
      const res = await request.get(buildUrl(`/incidents/${someWrongValue}`), {
        headers: jsonHeaders(),
      });

      await assertNotFoundRequest(
        res,
        "Incident with key '9675540027' not found",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get Incidents Invalid Value', async ({request}) => {
    await expect(async () => {
      const someInvalidValue = 'meow';
      const res = await request.get(
        buildUrl(`/incidents/${someInvalidValue}`),
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
});
