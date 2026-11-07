/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
import type {APIRequestContext, APIResponse} from 'playwright-core';
import {assertStatusCode, buildUrl, defaultHeaders} from '../http';
import {generateUniqueId} from '../../utils/constants';

type DeploymentItem = Record<string, Record<string, unknown> | undefined>;

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

  return resourceMetadataOf(body.deployments[deploymentIndex]);
}

interface DeployedEntity {
  resourceName: string;
  version: number;
}

// One entry of the `deployments` array of a POST /deployments response.
export interface DeploymentEntry {
  processDefinition?: DeployedEntity & {
    processDefinitionKey: string;
    processDefinitionId: string;
  };
  form?: DeployedEntity & {formKey: string; formId: string};
  decisionDefinition?: Omit<DeployedEntity, 'resourceName'> & {
    decisionDefinitionKey: string;
    decisionDefinitionId: string;
    name: string;
  };
  decisionRequirements?: DeployedEntity & {
    decisionRequirementsKey: string;
    decisionRequirementsId: string;
  };
  resource?: DeployedEntity & {resourceKey: string; resourceId: string};
}

// Maps one entry of a POST /deployments response to the key and id of the
// resource it deployed. For a DMN file, pass the `decisionRequirements` entry to
// get the key POST /resources/:key/deletion takes.
export function resourceMetadataOf(
  deployment: DeploymentEntry,
): ResourceMetadata {
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

export interface DeployedResource extends ResourceMetadata {
  deploymentKey: string;
  resourceId: string;
}

export interface InlineResource {
  fileName: string;
  content: string | Buffer;
}

// A minimal valid RPA document. `id` and `versionTag` are the only fields the
// deployment transformer reads, and versionTag is the sole way any resource
// type can carry one.
export function rpaResourceContent(rpaId: string, versionTag?: string): string {
  return JSON.stringify({
    id: rpaId,
    name: rpaId,
    executionPlatform: 'Camunda Cloud',
    executionPlatformVersion: '8.8.0',
    ...(versionTag ? {versionTag} : {}),
    script: '*** Tasks ***\nDo nothing\n    Log    hello',
  });
}

export function formResourceContent(formId: string, label: string): string {
  return JSON.stringify({
    type: 'default',
    id: formId,
    components: [{key: 'applicant', label, type: 'textfield'}],
  });
}

export function inlineBpmnProcess(processId: string): InlineResource {
  return {
    fileName: `${processId}.bpmn`,
    content: `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_${processId}" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="${processId}" isExecutable="true">
    <bpmn:startEvent id="start" />
  </bpmn:process>
</bpmn:definitions>`,
  };
}

export function inlineDmnDecision(drdId: string): InlineResource {
  return {
    fileName: `${drdId}.dmn`,
    content: `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="${drdId}" name="${drdId}" namespace="http://camunda.org/schema/1.0/dmn">
  <decision id="decision_${drdId}" name="decision_${drdId}">
    <literalExpression>
      <text>"ok"</text>
    </literalExpression>
  </decision>
</definitions>`,
  };
}

export function inlineRpaResource(): InlineResource {
  const rpaId = `rpa_${generateUniqueId()}`;
  return {fileName: `${rpaId}.rpa`, content: rpaResourceContent(rpaId)};
}

export function createInlineFormData(files: InlineResource[]): FormData {
  const formData = new FormData();
  for (const {fileName, content} of files) {
    const bytes = Uint8Array.from(
      typeof content === 'string' ? Buffer.from(content, 'utf-8') : content,
    );
    formData.append(
      'resources',
      new Blob([bytes], {type: 'application/octet-stream'}),
      fileName,
    );
  }
  return formData;
}

export function deployInlineFilesRaw(
  request: APIRequestContext,
  files: InlineResource[],
  headers: Record<string, string> = defaultHeaders(),
): Promise<APIResponse> {
  return request.post(buildUrl('/deployments'), {
    headers,
    multipart: createInlineFormData(files),
  });
}

export async function deployInlineFiles(
  request: APIRequestContext,
  files: InlineResource[],
): Promise<Record<string, unknown>> {
  const res = await deployInlineFilesRaw(request, files);
  await assertStatusCode(res, 200);
  return res.json();
}

export async function deployInlineResources(
  request: APIRequestContext,
  resources: InlineResource[],
): Promise<{deploymentKey: string; resources: DeployedResource[]}> {
  const body = await deployInlineFiles(request, resources);
  const deployed = (body.deployments as DeploymentItem[])
    .filter((deployment) => deployment.resource != null)
    .map((deployment) => {
      const resource = deployment.resource!;
      return {
        deploymentKey: body.deploymentKey as string,
        resourceKey: resource.resourceKey as string,
        resourceName: resource.resourceName as string,
        resourceId: resource.resourceId as string,
        version: resource.version as number,
      };
    });

  expect(
    deployed.length,
    `Expected every deployed file to be a resource, got ${JSON.stringify(body.deployments)}`,
  ).toBe(resources.length);

  return {deploymentKey: body.deploymentKey as string, resources: deployed};
}

export interface LinkedResourceBinding {
  resourceId: string;
  resourceType: string;
  bindingType: 'latest' | 'deployment' | 'versionTag';
  linkName: string;
  versionTag?: string;
}

// Generated per test rather than kept as a fixture: the job type has to be
// unique, because /jobs/activation has no process-instance filter and would
// otherwise hand a parallel test's job to this one.
export function serviceTaskWithLinkedResourcesBpmn(
  processDefinitionId: string,
  jobType: string,
  linkedResources: LinkedResourceBinding[],
): string {
  const links = linkedResources
    .map(
      (link) =>
        `<zeebe:linkedResource resourceId="${link.resourceId}" resourceType="${link.resourceType}" bindingType="${link.bindingType}" linkName="${link.linkName}"${
          link.versionTag ? ` versionTag="${link.versionTag}"` : ''
        } />`,
    )
    .join('\n            ');

  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                  id="Definitions_${processDefinitionId}"
                  targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="${processDefinitionId}" isExecutable="true">
    <bpmn:startEvent id="start">
      <bpmn:outgoing>flow_to_task</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="flow_to_task" sourceRef="start" targetRef="linked_task" />
    <bpmn:serviceTask id="linked_task">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="${jobType}" />
        <zeebe:linkedResources>
            ${links}
        </zeebe:linkedResources>
      </bpmn:extensionElements>
      <bpmn:incoming>flow_to_task</bpmn:incoming>
      <bpmn:outgoing>flow_to_end</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:sequenceFlow id="flow_to_end" sourceRef="linked_task" targetRef="end" />
    <bpmn:endEvent id="end">
      <bpmn:incoming>flow_to_end</bpmn:incoming>
    </bpmn:endEvent>
  </bpmn:process>
</bpmn:definitions>`;
}

export interface LinkedResourceHeader {
  resourceType: string;
  linkName: string;
  resourceKey: string;
}

export function parseLinkedResourcesHeader(
  customHeaders: unknown,
): LinkedResourceHeader[] {
  const linkedResources = (customHeaders as Record<string, string>)
    ?.linkedResources;
  expect(
    linkedResources,
    `Job has no linkedResources header: ${JSON.stringify(customHeaders)}`,
  ).toBeDefined();
  return JSON.parse(linkedResources);
}

export async function deployInlineResource(
  request: APIRequestContext,
  fileName: string,
  content: string | Buffer,
): Promise<DeployedResource> {
  const {resources} = await deployInlineResources(request, [
    {fileName, content},
  ]);
  return resources[0];
}

// The endpoint constants are exported because a spec needs the same literal a
// second time for `validateResponse({path, ...})`. Reusing the constant keeps
// the requested path and the validated path from drifting apart.
export const RESOURCE_ENDPOINT = '/resources/{resourceKey}';
export const RESOURCE_CONTENT_ENDPOINT = '/resources/{resourceKey}/content';
export const RESOURCE_DELETION_ENDPOINT = '/resources/{resourceKey}/deletion';

export interface ResourceRequestOptions {
  headers?: Record<string, string>;
}

export function getResource(
  request: APIRequestContext,
  resourceKey: string,
  options: ResourceRequestOptions = {},
): Promise<APIResponse> {
  return request.get(buildUrl(RESOURCE_ENDPOINT, {resourceKey}), {
    headers: options.headers ?? defaultHeaders(),
  });
}

export function getResourceContent(
  request: APIRequestContext,
  resourceKey: string,
  options: ResourceRequestOptions = {},
): Promise<APIResponse> {
  return request.get(buildUrl(RESOURCE_CONTENT_ENDPOINT, {resourceKey}), {
    headers: options.headers ?? defaultHeaders(),
  });
}

export function deleteResource(
  request: APIRequestContext,
  resourceKey: string,
  options: ResourceRequestOptions = {},
): Promise<APIResponse> {
  return request.post(buildUrl(RESOURCE_DELETION_ENDPOINT, {resourceKey}), {
    headers: options.headers ?? defaultHeaders(),
  });
}
