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
  deployInlineFilesRaw,
  deployInlineResource,
  searchResources,
  serviceTaskWithLinkedResourcesBpmn,
  uniqueResourceName,
} from '@requestHelpers';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';

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
    expect(body.deployments).toHaveLength(5);

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

  test('Deploy Resource - Generic File Success', async ({request}) => {
    const genericResourceName = uniqueResourceName('system-prompt', 'md');

    const res = await deployInlineFilesRaw(request, [
      {fileName: genericResourceName, content: '# System prompt'},
    ]);

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
    expect(body.deployments[0].resource).toMatchObject({
      resourceName: genericResourceName,
      resourceId: genericResourceName,
      version: 1,
      tenantId: '<default>',
    });
    expect(body.deployments[0].resource.resourceKey).toBeDefined();
  });

  test('Deploy Resource - File Types That Are Not Interpreted As A Model', async ({
    request,
  }) => {
    const xmlResourceName = uniqueResourceName('sap-mapping', 'xml');
    const extensionlessResourceName = `LICENSE-${generateUniqueId()}`;

    const res = await deployInlineFilesRaw(request, [
      {
        fileName: xmlResourceName,
        content: '<mapping><field name="amount" /></mapping>',
      },
      {fileName: extensionlessResourceName, content: 'Camunda License 1.0'},
    ]);

    await assertStatusCode(res, 200);
    const body = await res.json();
    validateDeploymentResponse(body, 2);
    expect(
      body.deployments.map(
        (deployment: {resource?: {resourceName: string}}) =>
          deployment.resource?.resourceName,
      ),
    ).toEqual(
      expect.arrayContaining([xmlResourceName, extensionlessResourceName]),
    );
  });

  test('Deploy Resource - Generic File Versioning', async ({request}) => {
    const genericResourceName = uniqueResourceName('config', 'json');

    const firstVersion = await deployInlineResource(
      request,
      genericResourceName,
      '{"timeout": 30}',
    );
    const secondVersion = await deployInlineResource(
      request,
      genericResourceName,
      '{"timeout": 60}',
    );

    expect(firstVersion.version).toBe(1);
    expect(secondVersion.version).toBe(2);
    expect(secondVersion.resourceKey).not.toBe(firstVersion.resourceKey);

    await expect(async () => {
      const res = await searchResources(request, {
        filter: {resourceId: genericResourceName},
      });
      await assertStatusCode(res, 200);
      const keys = (await res.json()).items.map(
        (item: {resourceKey: string}) => item.resourceKey,
      );
      expect(keys.sort()).toEqual(
        [firstVersion.resourceKey, secondVersion.resourceKey].sort(),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Deploy Resource - Rejected Deployment Leaves No Resource Behind', async ({
    request,
  }) => {
    const genericResourceName = uniqueResourceName('runbook', 'md');

    const rejected = await deployInlineFilesRaw(request, [
      {fileName: genericResourceName, content: '# Runbook'},
      {fileName: `broken-${generateUniqueId()}.bpmn`, content: 'not bpmn'},
    ]);
    await assertStatusCode(rejected, 400);

    await expect(async () => {
      const res = await searchResources(request, {
        filter: {resourceId: genericResourceName},
      });
      await assertStatusCode(res, 200);
      expect((await res.json()).items).toHaveLength(0);
    }).toPass(defaultAssertionOptions);

    const redeployed = await deployInlineResource(
      request,
      genericResourceName,
      '# Runbook',
    );
    expect(redeployed.version).toBe(1);
  });

  test('Deploy Resource - Bad Request 400 - Linked Resource Missing From Deployment', async ({
    request,
  }) => {
    const suffix = generateUniqueId();
    const processDefinitionId = `linked-generic-missing-${suffix}`;

    const res = await deployInlineFilesRaw(request, [
      {
        fileName: `${processDefinitionId}.bpmn`,
        content: serviceTaskWithLinkedResourcesBpmn(
          processDefinitionId,
          `linked-generic-missing-job-${suffix}`,
          [
            {
              resourceId: `absent-${suffix}.md`,
              resourceType: 'GenericScript',
              bindingType: 'deployment',
              linkName: 'my_generic_link',
            },
          ],
        ),
      },
    ]);

    await assertStatusCode(res, 400);
    expect((await res.json()).detail).toContain(`absent-${suffix}.md`);
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
