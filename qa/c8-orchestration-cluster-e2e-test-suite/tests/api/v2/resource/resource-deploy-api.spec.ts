/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  assertStatusCode,
  buildUrl,
  defaultHeaders,
  assertUnauthorizedRequest,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {
  validateDecisionDefinitionDeployment,
  validateDecisionRequirementsDeployment,
  validateDeploymentResponse,
  validateFormDeployment,
  validateProcessDefinitionDeployment,
  validateRpaDeployment,
  createMultiResourceFormData,
  createResourceFormData,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Resource Deploy API', () => {
  const resourceName = 'process_with_linked_start_form.bpmn';

  test('Deploy Resource - Process Definition Success', async ({request}) => {
    const formData = createResourceFormData(resourceName);

    const res = await request.post(buildUrl('/deployments'), {
      headers: defaultHeaders(),
      multipart: formData,
    });

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/deployments',
        method: 'POST',
        status: '200',
      },
      res,
    );

    const body = await res.json();
    validateDeploymentResponse(body, 1);
    validateProcessDefinitionDeployment(
      body.deployments,
      resourceName,
      'process_with_linked_start_form',
    );
  });

  test('Deploy Resource - Form Success', async ({request}) => {
    const formResourceName = 'sign_up_form.form';
    const formData = createResourceFormData(formResourceName);

    const res = await request.post(buildUrl('/deployments'), {
      headers: defaultHeaders(),
      multipart: formData,
    });

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/deployments',
        method: 'POST',
        status: '200',
      },
      res,
    );

    const body = await res.json();
    validateDeploymentResponse(body, 1);
    validateFormDeployment(body.deployments, formResourceName, 'sign_up_form');
  });

  test('Deploy Resource - Decision Definition Success', async ({request}) => {
    const decisionResourceName = 'simpleDecisionTable1.dmn';
    const formData = createResourceFormData(decisionResourceName);

    const res = await request.post(buildUrl('/deployments'), {
      headers: defaultHeaders(),
      multipart: formData,
    });

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/deployments',
        method: 'POST',
        status: '200',
      },
      res,
    );

    const body = await res.json();
    validateDeploymentResponse(body, 2);
    validateDecisionDefinitionDeployment(
      body.deployments,
      'Decision_f6ej9i5',
      'SingleTableDecision',
    );
    validateDecisionRequirementsDeployment(
      body.deployments,
      decisionResourceName,
      'Definitions_1lja2g1',
      'DRD',
    );
  });

  test('Deploy Resource - RPA success', async ({request}) => {
    const rpaResourceName = 'rpa_get_resource_api_test.rpa';
    const formData = createResourceFormData(rpaResourceName);

    const res = await request.post(buildUrl('/deployments'), {
      headers: defaultHeaders(),
      multipart: formData,
    });

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/deployments',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const body = await res.json();
    validateDeploymentResponse(body, 1);
    validateRpaDeployment(body.deployments, rpaResourceName, 'RPA_04pgbrx');
  });

  test('Deploy Multiple Resources - Process Definition and Form Success', async ({
    request,
  }) => {
    const processResourceName = 'process_with_linked_start_form.bpmn';
    const formResourceName = 'sign_up_form.form';

    const formData = createMultiResourceFormData(
      processResourceName,
      formResourceName,
    );

    const res = await request.post(buildUrl('/deployments'), {
      headers: defaultHeaders(),
      multipart: formData,
    });

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/deployments',
        method: 'POST',
        status: '200',
      },
      res,
    );

    const body = await res.json();
    validateDeploymentResponse(body, 2);
    validateProcessDefinitionDeployment(
      body.deployments,
      processResourceName,
      'process_with_linked_start_form',
    );
    validateFormDeployment(body.deployments, formResourceName, 'sign_up_form');
  });

  test('Deploy Multiple Resources - All Resource Types Success', async ({
    request,
  }) => {
    const processResourceName = 'process_with_linked_start_form.bpmn';
    const formResourceName = 'sign_up_form.form';
    const decisionResourceName = 'simpleDecisionTable1.dmn';
    const rpaResourceName = 'rpa_get_resource_api_test.rpa';

    const formData = createMultiResourceFormData(
      processResourceName,
      formResourceName,
      decisionResourceName,
      rpaResourceName,
    );

    const res = await request.post(buildUrl('/deployments'), {
      headers: defaultHeaders(),
      multipart: formData,
    });

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/deployments',
        method: 'POST',
        status: '200',
      },
      res,
    );

    const body = await res.json();
    expect(body.tenantId).toEqual('<default>');
    expect(body.deploymentKey).toBeDefined();
    expect(body.deployments.length).toBe(5);

    const deploymentTypes = body.deployments.map(
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (d: any) => Object.keys(d).find((key) => d[key] !== null),
    );
    expect(deploymentTypes).toContain('processDefinition');
    expect(deploymentTypes).toContain('form');
    expect(deploymentTypes).toContain('decisionDefinition');
    expect(deploymentTypes).toContain('decisionRequirements');
    expect(deploymentTypes).toContain('resource');
  });

  test('Deploy Resource - Unauthorized 401', async ({request}) => {
    const formData = createResourceFormData(resourceName);

    const res = await request.post(buildUrl('/deployments'), {
      headers: {},
      multipart: formData,
    });

    await assertUnauthorizedRequest(res);
  });

  test('Deploy Resource - Bad Request 400', async ({request}) => {
    const emptyBlob = new Blob([], {type: 'application/xml'});
    const formData = new FormData();
    formData.append('resources', emptyBlob, 'empty.bpmn');

    const res = await request.post(buildUrl('/deployments'), {
      headers: defaultHeaders(),
      multipart: formData,
    });

    await assertStatusCode(res, 400);

    const body = await res.json();
    expect(body.type).toBeDefined();
    expect(body.title).toBeDefined();
    expect(body.status).toBe(400);
  });
});
