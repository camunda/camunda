/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {APIRequestContext} from 'playwright-core';
import {assertStatusCode, buildUrl, defaultHeaders} from '../http';

export function validateProcessDefinitionDeployment(
  deployments: unknown[],
  expectedResourceName: string,
  expectedProcessDefinitionId: string,
): void {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const processDeployment = deployments.find(
    (d) => (d as any).processDefinition != null,
  ) as any;
  expect(processDeployment).toBeDefined();
  expect(processDeployment.processDefinition.tenantId).toEqual('<default>');
  expect(processDeployment.processDefinition.resourceName).toEqual(
    expectedResourceName,
  );
  expect(processDeployment.processDefinition.processDefinitionId).toEqual(
    expectedProcessDefinitionId,
  );
  expect(
    processDeployment.processDefinition.processDefinitionKey,
  ).toBeDefined();
}

export function validateFormDeployment(
  deployments: unknown[],
  expectedResourceName: string,
  expectedFormId: string,
): void {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const formDeployment = deployments.find(
    (d) => (d as any).form != null,
  ) as any;
  expect(
    formDeployment,
    `No Form Deployment found in ${JSON.stringify(deployments)}`,
  ).toBeDefined();
  expect(formDeployment.form.tenantId).toEqual('<default>');
  expect(formDeployment.form.resourceName).toEqual(expectedResourceName);
  expect(formDeployment.form.formId).toEqual(expectedFormId);
  expect(formDeployment.form.formKey).toBeDefined();
  expect(formDeployment.form.version).toBeDefined();
}

export function validateDecisionDefinitionDeployment(
  deployments: unknown[],
  expectedDecisionDefinitionId: string,
  expectedName: string,
): void {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const decisionDeployment = deployments.find(
    (d) => (d as any).decisionDefinition != null,
  ) as any;
  expect(
    decisionDeployment,
    `No Decision Definition found in ${JSON.stringify(deployments)}`,
  ).toBeDefined();
  expect(decisionDeployment.decisionDefinition.tenantId).toEqual('<default>');
  expect(decisionDeployment.decisionDefinition.decisionDefinitionId).toEqual(
    expectedDecisionDefinitionId,
  );
  expect(decisionDeployment.decisionDefinition.name).toEqual(expectedName);
  expect(
    decisionDeployment.decisionDefinition.decisionDefinitionKey,
  ).toBeDefined();
  expect(decisionDeployment.decisionDefinition.version).toBeDefined();
  expect(
    decisionDeployment.decisionDefinition.decisionRequirementsId,
  ).toBeDefined();
  expect(
    decisionDeployment.decisionDefinition.decisionRequirementsKey,
  ).toBeDefined();
}

export function validateDecisionRequirementsDeployment(
  deployments: unknown[],
  expectedResourceName: string,
  expectedDecisionRequirementsId: string,
  expectedDecisionRequirementsName: string,
): void {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const decisionRequirementsDeployment = deployments.find(
    (d) => (d as any).decisionRequirements != null,
  ) as any;
  expect(
    decisionRequirementsDeployment,
    `No Decision Requirements found in ${JSON.stringify(deployments)}`,
  ).toBeDefined();
  expect(decisionRequirementsDeployment.decisionRequirements.tenantId).toEqual(
    '<default>',
  );
  expect(
    decisionRequirementsDeployment.decisionRequirements.resourceName,
  ).toEqual(expectedResourceName);
  expect(
    decisionRequirementsDeployment.decisionRequirements.decisionRequirementsId,
  ).toEqual(expectedDecisionRequirementsId);
  expect(
    decisionRequirementsDeployment.decisionRequirements
      .decisionRequirementsName,
  ).toEqual(expectedDecisionRequirementsName);
  expect(
    decisionRequirementsDeployment.decisionRequirements.decisionRequirementsKey,
  ).toBeDefined();
  expect(
    decisionRequirementsDeployment.decisionRequirements.version,
  ).toBeDefined();
}

export function validateRpaDeployment(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  deployments: any[],
  expectedResourceName: string,
  expectedRpaId: string,
): void {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const rpaDeployment = deployments.find(
    (d) => (d as any).resource != null,
  ) as any;
  expect(rpaDeployment).toBeDefined();
  expect(rpaDeployment.resource.tenantId).toEqual('<default>');
  expect(rpaDeployment.resource.resourceName).toEqual(expectedResourceName);
  expect(rpaDeployment.resource.resourceId).toEqual(expectedRpaId);
  expect(rpaDeployment.resource.resourceKey).toBeDefined();
  expect(rpaDeployment.resource.version).toBeDefined();
}

export function validateDeploymentResponse(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  body: any,
  expectedDeploymentCount: number,
): void {
  expect(body.tenantId).toEqual('<default>');
  expect(body.deploymentKey).toBeDefined();
  expect(body.deployments.length).toBe(expectedDeploymentCount);
}

export function createResourceFormData(resourceName: string): FormData {
  const formData = new FormData();
  const blob = createResourceBlob(resourceName);
  formData.append('resources', blob, resourceName);
  return formData;
}

export function createMultiResourceFormData(
  ...resourceNames: string[]
): FormData {
  const formData = new FormData();
  for (const resourceName of resourceNames) {
    const blob = createResourceBlob(resourceName);
    formData.append('resources', blob, resourceName);
  }
  return formData;
}

function createResourceBlob(resourceName: string) {
  const resourcePath = `./resources/${resourceName}`;
  const resourceBuffer = readFileSync(resourcePath);
  const mimeType = resourceName.endsWith('.bpmn')
    ? 'application/xml'
    : 'application/json';
  return new Blob([resourceBuffer], {type: mimeType});
}

export interface ResourceMetadata {
  resourceKey: string;
  resourceName: string;
  resourceId?: string;
  version?: number;
}

export async function deployResourceAndGetMetadata(
  request: APIRequestContext,
  resourceName: string,
  deploymentIndex: number = 0,
): Promise<ResourceMetadata> {
  const formData = createResourceFormData(resourceName);

  const res = await request.post(buildUrl('/deployments'), {
    headers: defaultHeaders(),
    multipart: formData,
  });

  await assertStatusCode(res, 200);
  const body = await res.json();
  expect(body.deployments.length).toBeGreaterThan(deploymentIndex);

  const deployment = body.deployments[deploymentIndex];

  if (deployment.processDefinition) {
    return {
      resourceKey: deployment.processDefinition.processDefinitionKey,
      resourceName: deployment.processDefinition.resourceName,
      resourceId: deployment.processDefinition.processDefinitionId,
      version: deployment.processDefinition.version,
    };
  } else if (deployment.form) {
    return {
      resourceKey: deployment.form.formKey,
      resourceName: deployment.form.resourceName,
      resourceId: deployment.form.formId,
      version: deployment.form.version,
    };
  } else if (deployment.decisionDefinition) {
    return {
      resourceKey: deployment.decisionDefinition.decisionDefinitionKey,
      resourceName: deployment.decisionDefinition.name,
      resourceId: deployment.decisionDefinition.decisionDefinitionId,
      version: deployment.decisionDefinition.version,
    };
  } else if (deployment.decisionRequirements) {
    return {
      resourceKey: deployment.decisionRequirements.decisionRequirementsKey,
      resourceName: deployment.decisionRequirements.resourceName,
      resourceId: deployment.decisionRequirements.decisionRequirementsId,
      version: deployment.decisionRequirements.version,
    };
  } else if (deployment.resource) {
    return {
      resourceKey: deployment.resource.resourceKey,
      resourceName: deployment.resource.resourceName,
      resourceId: deployment.resource.resourceId,
      version: deployment.resource.version,
    };
  }

  throw new Error(`Unknown deployment type: ${JSON.stringify(deployment)}`);
}
