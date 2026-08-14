/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, expect, test} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {
  activateJobAndGetHeaders,
  completeJob,
  deployInlineFiles,
  deployInlineResource,
  getResourceContentBinary,
  parseLinkedResourcesHeader,
  serviceTaskWithLinkedResourcesBpmn,
} from '@requestHelpers';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';

const RESOURCE_TYPE = 'GenericScript';
const LINK_NAME = 'my_generic_link';

async function startProcessInstance(
  request: APIRequestContext,
  processDefinitionId: string,
): Promise<string> {
  const res = await request.post(buildUrl('/process-instances'), {
    headers: jsonHeaders(),
    data: {processDefinitionId},
  });
  await assertStatusCode(res, 200);
  return String((await res.json()).processInstanceKey);
}

async function fetchResourceContent(
  request: APIRequestContext,
  resourceKey: string,
): Promise<string> {
  const res = await getResourceContentBinary(request, resourceKey);
  await assertStatusCode(res, 200);
  return res.text();
}

test.describe.parallel('Job Linked Generic Resources', () => {
  test('Linked generic resource with latest binding resolves to the newest version', async ({
    request,
  }) => {
    const suffix = generateUniqueId();
    const resourceName = `prompt-${suffix}.md`;
    const processDefinitionId = `linked-generic-latest-${suffix}`;
    const jobType = `linked-generic-latest-job-${suffix}`;

    const firstVersion = await deployInlineResource(
      request,
      resourceName,
      '# version one',
    );
    await deployInlineFiles(request, [
      {
        fileName: `${processDefinitionId}.bpmn`,
        content: serviceTaskWithLinkedResourcesBpmn(
          processDefinitionId,
          jobType,
          [
            {
              resourceId: resourceName,
              resourceType: RESOURCE_TYPE,
              bindingType: 'latest',
              linkName: LINK_NAME,
            },
          ],
        ),
      },
    ]);

    await startProcessInstance(request, processDefinitionId);
    const job = await activateJobAndGetHeaders(request, jobType);
    const [link] = parseLinkedResourcesHeader(job.customHeaders);

    expect(link).toMatchObject({
      resourceType: RESOURCE_TYPE,
      linkName: LINK_NAME,
      resourceKey: firstVersion.resourceKey,
    });
    await completeJob(request, job.jobKey);

    const secondVersion = await deployInlineResource(
      request,
      resourceName,
      '# version two',
    );
    expect(secondVersion.version).toBe(2);

    await startProcessInstance(request, processDefinitionId);
    const jobAfterRedeploy = await activateJobAndGetHeaders(request, jobType);
    const [linkAfterRedeploy] = parseLinkedResourcesHeader(
      jobAfterRedeploy.customHeaders,
    );

    expect(linkAfterRedeploy.resourceKey).toBe(secondVersion.resourceKey);
    await completeJob(request, jobAfterRedeploy.jobKey);
  });

  test('Linked generic resource with deployment binding stays pinned to its deployment', async ({
    request,
  }) => {
    const suffix = generateUniqueId();
    const resourceName = `config-${suffix}.yml`;
    const processDefinitionId = `linked-generic-deployment-${suffix}`;
    const jobType = `linked-generic-deployment-job-${suffix}`;

    const deployment = await deployInlineFiles(request, [
      {fileName: resourceName, content: 'timeout: 30'},
      {
        fileName: `${processDefinitionId}.bpmn`,
        content: serviceTaskWithLinkedResourcesBpmn(
          processDefinitionId,
          jobType,
          [
            {
              resourceId: resourceName,
              resourceType: RESOURCE_TYPE,
              bindingType: 'deployment',
              linkName: LINK_NAME,
            },
          ],
        ),
      },
    ]);
    const pinnedResourceKey = (
      deployment.deployments as {resource?: {resourceKey: string}}[]
    ).find((item) => item.resource != null)!.resource!.resourceKey;

    const laterVersion = await deployInlineResource(
      request,
      resourceName,
      'timeout: 60',
    );
    expect(laterVersion.resourceKey).not.toBe(pinnedResourceKey);

    await startProcessInstance(request, processDefinitionId);
    const job = await activateJobAndGetHeaders(request, jobType);
    const [link] = parseLinkedResourcesHeader(job.customHeaders);

    expect(link.resourceKey).toBe(pinnedResourceKey);
    await expect(async () => {
      expect(await fetchResourceContent(request, link.resourceKey)).toBe(
        'timeout: 30',
      );
    }).toPass(defaultAssertionOptions);

    await completeJob(request, job.jobKey);
  });

  test('Worker can fetch the linked resource content using the key from the job header', async ({
    request,
  }) => {
    const suffix = generateUniqueId();
    const resourceName = `system-prompt-${suffix}.md`;
    const content = '# System prompt\n\nYou are a helpful agent.';
    const processDefinitionId = `linked-generic-fetch-${suffix}`;
    const jobType = `linked-generic-fetch-job-${suffix}`;

    await deployInlineResource(request, resourceName, content);
    await deployInlineFiles(request, [
      {
        fileName: `${processDefinitionId}.bpmn`,
        content: serviceTaskWithLinkedResourcesBpmn(
          processDefinitionId,
          jobType,
          [
            {
              resourceId: resourceName,
              resourceType: RESOURCE_TYPE,
              bindingType: 'latest',
              linkName: LINK_NAME,
            },
          ],
        ),
      },
    ]);

    await startProcessInstance(request, processDefinitionId);
    const job = await activateJobAndGetHeaders(request, jobType);
    const [link] = parseLinkedResourcesHeader(job.customHeaders);

    await expect(async () => {
      expect(await fetchResourceContent(request, link.resourceKey)).toBe(
        content,
      );
    }).toPass(defaultAssertionOptions);

    await completeJob(request, job.jobKey);
  });
});
