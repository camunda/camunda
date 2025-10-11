/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {createInstances, deploy} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {validateResponseShape} from '../../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../../utils/constants';

test.describe.parallel('Process Definition Get Statistics API', () => {
  const state: Record<string, string> = {};
  test.beforeAll(async () => {
    await deploy(['./resources/process_definition_api_tests.bpmn']);
    await createInstances('process_definition_api_tests', 1, 1).then(
      (instances) => {
        state['processDefinitionKey'] = instances[0].processDefinitionKey;
        state['processDefinitionId'] = instances[0].processDefinitionId;
        state['processInstanceKey'] = instances[0].processInstanceKey;
      },
    );
  });

  test('Get Process Definition Statistics - Basic', async ({request}) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/process-definitions/${state.processDefinitionKey}/statistics/element-instances`,
        ),
        {
          headers: jsonHeaders(),
          data: {}, // empty body for basic search
        },
      );
      await assertStatusCode(res, 200);
      const body = await res.json();
      validateResponseShape(
        {
          path: '/process-definitions/{processDefinitionKey}/statistics/element-instances',
          method: 'POST',
          status: '200',
        },
        body,
      );
      expect(body.items.length).toBe(2);
      expect(body.items[0].elementId).toBe('StartEvent');
      expect(body.items[1].elementId).toBe('EndEvent');
      body.items.forEach((item: Record<string, number>) => {
        expect(item.incidents).toBe(0);
        expect(item.active).toBe(0);
        expect(item.completed).toBeGreaterThan(0);
        expect(item.canceled).toBe(0);
      });
    }).toPass(defaultAssertionOptions);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Process Definition Statistics - Unauthorized', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(
        `/process-definitions/${state.processDefinitionKey}/statistics/element-instances`,
      ),
      {
        headers: {},
        data: {}, // empty body for basic search
      },
    );
    await assertUnauthorizedRequest(res);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Process Definition Statistics - Invalid Process Definition Key', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(`/process-definitions/invalidKey/statistics/element-instances`),
      {
        headers: jsonHeaders(),
        data: {}, // empty body for basic search
      },
    );
    await assertBadRequest(
      res,
      `Failed to convert 'processDefinitionKey' with value: 'invalidKey'`,
    );
  });

  test('Get Process Definition Statistics - Filter by Element Id', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/process-definitions/${state.processDefinitionKey}/statistics/element-instances`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              elementId: 'StartEvent',
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.items.length).toBe(1);
      expect(body.items[0].elementId).toBe('StartEvent');
    }).toPass(defaultAssertionOptions);
  });

  test('Get Process Definition Statistics - Filter by Invalid Element Id - empty result', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(
        `/process-definitions/${state.processDefinitionKey}/statistics/element-instances`,
      ),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            elementId: 'InvalidElementId',
          },
        },
      },
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    expect(body.items.length).toBe(0);
  });

  test('Get Process Definition Statistics - Or Filter', async ({request}) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/process-definitions/${state.processDefinitionKey}/statistics/element-instances`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: state.processInstanceKey,
              $or: [{elementId: 'StartEvent'}, {elementId: 'EndEvent'}],
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.items.length).toBe(2);
      const elementIds = body.items.map(
        (item: Record<string, number>) => item.elementId,
      );
      expect(elementIds).toContain('StartEvent');
      expect(elementIds).toContain('EndEvent');
    }).toPass(defaultAssertionOptions);
  });
});
