/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';

export function validateProcessDefinitionDeployment(
  deployments: unknown[],
  expectedResourceName: string,
  expectedProcessDefinitionId: string,
): void {
  const processDeployment = deployments.find(
    (d) => 'processDefinition' in (d as object),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
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
  const formDeployment = deployments.find(
    (d) => 'form' in (d as object),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
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
  const decisionDeployment = deployments.find(
    (d) => 'decisionDefinition' in (d as object),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
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
  const decisionRequirementsDeployment = deployments.find(
    (d) => 'decisionRequirements' in (d as object),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
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
  const rpaDeployment = deployments.find(
    (d) => 'resource' in (d as object),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
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
