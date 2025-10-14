/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {deploy} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
  textXMLHeaders,
} from '../../../../utils/http';
import {readFileSync} from 'node:fs';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Process Definition Get XML API', () => {
  const state: Record<string, unknown> = {};
  test.beforeAll(async () => {
    const deployment = await deploy([
      './resources/process_definition_api_tests.bpmn',
    ]);

    state['processDefinitionKey'] =
      deployment.processes[0].processDefinitionKey;

    const xmlPath = './resources/process_definition_api_tests.bpmn';
    state['expectedXml'] = readFileSync(xmlPath, 'utf-8');
  });

  test('Get Process Definition XML - Success', async ({request}) => {
    const res = await request.get(
      buildUrl(`/process-definitions/${state.processDefinitionKey}/xml`),
      {headers: textXMLHeaders()},
    );
    expect(res.status()).toBe(200);
    const body = await res.text();
    expect(body).toEqual(state['expectedXml']);
  });

  test('Get Process Definition XML - Not Found', async ({request}) => {
    const res = await request.get(buildUrl(`/process-definitions/123456/xml`), {
      headers: jsonHeaders(),
    });
    await assertNotFoundRequest(
      res,
      "Process Definition with key '123456' not found",
    );
  });

  test('Get Process Definition XML - Unauthorized', async ({request}) => {
    const res = await request.get(
      buildUrl(`/process-definitions/${state.processDefinitionKey}/xml`),
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Process Definition XML - Invalid Key', async ({request}) => {
    const res = await request.get(
      buildUrl(`/process-definitions/invalidKey/xml`),
      {
        headers: {...jsonHeaders()},
      },
    );
    await assertBadRequest(
      res,
      "Failed to convert 'processDefinitionKey' with value: 'invalidKey'",
    );
  });
});
