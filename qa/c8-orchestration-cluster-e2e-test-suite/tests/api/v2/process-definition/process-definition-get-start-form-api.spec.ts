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
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {validateResponseShape} from '../../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {readFileSync} from 'node:fs';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Process Definition Get Start Form API', () => {
  const state: Record<string, unknown> = {};
  test.beforeAll(async () => {
    const formPath = './resources/sign_up_form.form';
    const deployment = await deploy([
      './resources/process_with_linked_start_form.bpmn',
      formPath,
      './resources/Process_with_embedded_Form.bpmn',
    ]);

    deployment.processes.forEach((processDefinition) => {
      if (
        processDefinition.processDefinitionId ==
        'process_with_linked_start_form'
      ) {
        state['processDefinitionKey_withLinkedForm'] =
          processDefinition.processDefinitionKey;
      } else if (
        processDefinition.processDefinitionId == 'Process_with_embedded_form'
      ) {
        state['processDefinitionKey_withEmbeddedForm'] =
          processDefinition.processDefinitionKey;
      } else {
        throw new Error(
          `Unrecognized processDefinition definition id: ${processDefinition.processDefinitionId}`,
        );
      }
    });

    state['expectedForm'] = readFileSync(formPath, 'utf-8');
  });

  test('Get Process Definition Start Form - Success 200', async ({request}) => {
    await expect(async () => {
      const res = await request.get(
        buildUrl(
          `/process-definitions/${state.processDefinitionKey_withLinkedForm}/form`,
        ),
        {headers: jsonHeaders()},
      );
      expect(res.status()).toBe(200);
      const body = await res.json();
      validateResponseShape(
        {
          path: '/process-definitions/{processDefinitionKey}/form',
          method: 'GET',
          status: '200',
        },
        body,
      );
      expect(body.formId).toBe('sign_up_form');
      expect(body.version).toBe(1);
      expect(body.formKey).toBeDefined();
      expect(body.tenantId).toBe('<default>');
      expect(body.schema).toBe(state['expectedForm']);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Process Definition Start Form - Success 204 No Content', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.get(
        buildUrl(
          `/process-definitions/${state.processDefinitionKey_withEmbeddedForm}/form`,
        ),
        {headers: jsonHeaders()},
      );
      await assertStatusCode(res, 204);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Process Definition Start Form - Not Found', async ({request}) => {
    const res = await request.get(
      buildUrl(`/process-definitions/123456/form`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      "Process Definition with key '123456' not found",
    );
  });

  test('Get Process Definition Start Form - Unauthorized', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl(`/process-definitions/${state.processDefinitionKey}/form`),
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Process Definition Start Form - Invalid Key', async ({request}) => {
    const res = await request.get(
      buildUrl(`/process-definitions/invalidKey/form`),
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
