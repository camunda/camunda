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
  assertStatusCode,
  assertBadRequest,
  assertUnauthorizedRequest,
  assertUnsupportedMediaTypeRequest,
  assertNotFoundRequest,
} from '../../../../utils/http';
import {
  EVALUATE_CONDITIONAL,
  EVALUATE_CONDITIONAL_WITH_TENANT,
  EVALUATE_CONDITIONAL_WITH_PROCESS_DEF_KEY,
  EVALUATE_CONDITIONAL_MULTIPLE_CONDITIONS,
  EVALUATE_CONDITIONAL_NO_MATCH,
  EVALUATE_CONDITIONAL_PARTIAL_MATCH,
  conditionalEvaluationResponseRequiredFields,
  conditionalProcessInstanceItemRequiredFields,
} from '../../../../utils/beans/conditional-evaluation-requestBeans';
import {deploy, cancelProcessInstance} from '../../../../utils/zeebeClient';

const CONDITIONAL_EVALUATION_ENDPOINT = '/conditionals/evaluation';

test.describe.parallel('Conditional Evaluation API Tests', () => {
  const processInstancesToCleanup: string[] = [];

  test.beforeAll(async () => {
    await deploy([
      './resources/conditional_start_event_single.bpmn',
      './resources/conditional_start_event_multiple.bpmn',
    ]);
  });

  test.afterAll(async () => {
    for (const instanceKey of processInstancesToCleanup) {
      await cancelProcessInstance(instanceKey);
    }
  });

  test('Evaluate Conditional - Single Match Success', async ({request}) => {
    const requestBody = EVALUATE_CONDITIONAL();
    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: jsonHeaders(),
      data: requestBody,
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    assertRequiredFields(json, conditionalEvaluationResponseRequiredFields);
    expect(json.processInstances.length).toBeGreaterThan(0);

    for (const instance of json.processInstances) {
      assertRequiredFields(
        instance,
        conditionalProcessInstanceItemRequiredFields,
      );
      processInstancesToCleanup.push(instance.processInstanceKey);
    }
  });

  test('Evaluate Conditional - Multiple Conditions Match', async ({
    request,
  }) => {
    const requestBody = EVALUATE_CONDITIONAL_MULTIPLE_CONDITIONS();
    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: jsonHeaders(),
      data: requestBody,
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    assertRequiredFields(json, conditionalEvaluationResponseRequiredFields);
    expect(Array.isArray(json.processInstances)).toBe(true);
    expect(json.processInstances.length).toBeGreaterThan(0);

    for (const instance of json.processInstances) {
      assertRequiredFields(
        instance,
        conditionalProcessInstanceItemRequiredFields,
      );
      processInstancesToCleanup.push(instance.processInstanceKey);
    }
  });

  test('Evaluate Conditional - Partial Match', async ({request}) => {
    const requestBody = EVALUATE_CONDITIONAL_PARTIAL_MATCH();
    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: jsonHeaders(),
      data: requestBody,
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    assertRequiredFields(json, conditionalEvaluationResponseRequiredFields);
    expect(Array.isArray(json.processInstances)).toBe(true);
    expect(json.processInstances.length).toBe(1);

    const instance = json.processInstances[0];
    assertRequiredFields(
      instance,
      conditionalProcessInstanceItemRequiredFields,
    );
    expect(instance.processDefinitionKey).toBeDefined();
    expect(instance.processInstanceKey).toBeDefined();
    processInstancesToCleanup.push(instance.processInstanceKey);
  });

  test('Evaluate Conditional - No Match Returns Empty List', async ({
    request,
  }) => {
    const requestBody = EVALUATE_CONDITIONAL_NO_MATCH();
    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: jsonHeaders(),
      data: requestBody,
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    assertRequiredFields(json, conditionalEvaluationResponseRequiredFields);
    expect(Array.isArray(json.processInstances)).toBe(true);
    expect(json.processInstances.length).toBe(0);
  });

  test('Evaluate Conditional With Tenant ID', async ({request}) => {
    const requestBody = EVALUATE_CONDITIONAL_WITH_TENANT('<default>');
    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: jsonHeaders(),
      data: requestBody,
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    assertRequiredFields(json, conditionalEvaluationResponseRequiredFields);

    for (const instance of json.processInstances) {
      assertRequiredFields(
        instance,
        conditionalProcessInstanceItemRequiredFields,
      );
      processInstancesToCleanup.push(instance.processInstanceKey);
    }
  });

  test('Evaluate Conditional By Process Definition Key', async ({request}) => {
    // First, evaluate conditionals to get a process definition key from the response
    // We cannot use getProcessDefinitionKey() because processes with conditional start events
    // cannot be started via the regular /process-instances endpoint
    const initialRes = await request.post(
      buildUrl(CONDITIONAL_EVALUATION_ENDPOINT),
      {
        headers: jsonHeaders(),
        data: EVALUATE_CONDITIONAL(),
      },
    );
    await assertStatusCode(initialRes, 200);
    const initialJson = await initialRes.json();
    expect(initialJson.processInstances.length).toBeGreaterThan(0);

    const processDefinitionKey =
      initialJson.processInstances[0].processDefinitionKey;

    // Track initial instances for cleanup
    for (const instance of initialJson.processInstances) {
      processInstancesToCleanup.push(instance.processInstanceKey);
    }

    // Now test the conditional evaluation with a specific process definition key
    const requestBody =
      EVALUATE_CONDITIONAL_WITH_PROCESS_DEF_KEY(processDefinitionKey);
    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: jsonHeaders(),
      data: requestBody,
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    assertRequiredFields(json, conditionalEvaluationResponseRequiredFields);
    expect(json.processInstances.length).toBeGreaterThan(0);
    expect(json.processInstances[0].processDefinitionKey).toBe(
      processDefinitionKey,
    );

    for (const instance of json.processInstances) {
      assertRequiredFields(
        instance,
        conditionalProcessInstanceItemRequiredFields,
      );
      processInstancesToCleanup.push(instance.processInstanceKey);
    }
  });

  test('Evaluate Conditional - Unauthorized', async ({request}) => {
    const requestBody = EVALUATE_CONDITIONAL();

    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: {},
      data: requestBody,
    });

    await assertUnauthorizedRequest(res);
  });

  test('Evaluate Conditional - Bad Request Missing Variables', async ({
    request,
  }) => {
    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: jsonHeaders(),
      data: {},
    });

    await assertBadRequest(res, /variables|required/i, 'INVALID_ARGUMENT');
  });

  test('Evaluate Conditional - Invalid Tenant', async ({request}) => {
    const requestBody = EVALUATE_CONDITIONAL_WITH_TENANT('invalidTenant');
    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: jsonHeaders(),
      data: requestBody,
    });

    await assertBadRequest(res, /.+/, 'INVALID_ARGUMENT');
  });

  test('Evaluate Conditional - Invalid Process Definition Key', async ({
    request,
  }) => {
    const requestBody = EVALUATE_CONDITIONAL_WITH_PROCESS_DEF_KEY('99999999');
    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: jsonHeaders(),
      data: requestBody,
    });

    await assertNotFoundRequest(res, '');
  });

  test('Evaluate Conditional - Unsupported Media Type', async ({request}) => {
    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: {
        Authorization: `Basic ${Buffer.from('demo:demo').toString('base64')}`,
        'Content-Type': 'text/plain',
      },
      data: 'plain text data',
    });

    await assertUnsupportedMediaTypeRequest(res);
  });

  test('Evaluate Conditional - Invalid JSON', async ({request}) => {
    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: jsonHeaders(),
      data: '{invalid json',
    });

    await assertBadRequest(res, /.+/);
  });

  test('Evaluate Conditional - Verify Response Schema', async ({request}) => {
    const requestBody = EVALUATE_CONDITIONAL();
    const res = await request.post(buildUrl(CONDITIONAL_EVALUATION_ENDPOINT), {
      headers: jsonHeaders(),
      data: requestBody,
    });

    await assertStatusCode(res, 200);
    const json = await res.json();

    expect(json).toHaveProperty('processInstances');
    expect(Array.isArray(json.processInstances)).toBe(true);
    expect(json.processInstances.length).toBeGreaterThan(0);

    for (const instance of json.processInstances) {
      expect(instance).toHaveProperty('processDefinitionKey');
      expect(instance).toHaveProperty('processInstanceKey');
      expect(typeof instance.processDefinitionKey).toBe('string');
      expect(typeof instance.processInstanceKey).toBe('string');
      processInstancesToCleanup.push(instance.processInstanceKey);
    }
  });
});
