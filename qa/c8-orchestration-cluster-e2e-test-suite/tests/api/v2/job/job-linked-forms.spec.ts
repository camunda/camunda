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
  deployInlineForm,
  deployInlineForms,
  deployInlineResource,
  formResourceContent,
  getFormByKey,
  parseLinkedResourcesHeader,
  serviceTaskWithLinkedResourcesBpmn,
  uniqueResourceName,
  LinkedResourceBinding,
} from '@requestHelpers';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';

const FORM_RESOURCE_TYPE = 'form';
const LINK_NAME = 'my_form';

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

function linkedFormProcess(
  processDefinitionId: string,
  jobType: string,
  links: LinkedResourceBinding[],
) {
  return {
    fileName: `${processDefinitionId}.bpmn`,
    content: serviceTaskWithLinkedResourcesBpmn(
      processDefinitionId,
      jobType,
      links,
    ),
  };
}

test.describe.parallel('Job Linked Forms', () => {
  test('Linked form with latest binding resolves to the newest form version', async ({
    request,
  }) => {
    const suffix = generateUniqueId();
    const formId = `approval-form-${suffix}`;
    const fileName = uniqueResourceName('approval', 'form');
    const processDefinitionId = `linked-form-latest-${suffix}`;
    const jobType = `linked-form-latest-job-${suffix}`;

    const firstVersion = await deployInlineForm(
      request,
      fileName,
      formResourceContent(formId, 'Applicant name'),
    );
    await deployInlineFiles(request, [
      linkedFormProcess(processDefinitionId, jobType, [
        {
          resourceId: formId,
          resourceType: FORM_RESOURCE_TYPE,
          bindingType: 'latest',
          linkName: LINK_NAME,
        },
      ]),
    ]);

    await startProcessInstance(request, processDefinitionId);
    const job = await activateJobAndGetHeaders(request, jobType);
    const [link] = parseLinkedResourcesHeader(job.customHeaders);

    expect(link).toMatchObject({
      resourceType: FORM_RESOURCE_TYPE,
      linkName: LINK_NAME,
      resourceKey: firstVersion.formKey,
    });
    await completeJob(request, job.jobKey);

    const secondVersion = await deployInlineForm(
      request,
      fileName,
      formResourceContent(formId, 'Full legal name'),
    );
    expect(secondVersion.version).toBe(2);

    await startProcessInstance(request, processDefinitionId);
    const jobAfterRedeploy = await activateJobAndGetHeaders(request, jobType);
    const [linkAfterRedeploy] = parseLinkedResourcesHeader(
      jobAfterRedeploy.customHeaders,
    );

    expect(linkAfterRedeploy.resourceKey).toBe(secondVersion.formKey);
    await completeJob(request, jobAfterRedeploy.jobKey);
  });

  test('Linked form with deployment binding stays pinned to its deployment', async ({
    request,
  }) => {
    const suffix = generateUniqueId();
    const formId = `pinned-form-${suffix}`;
    const fileName = uniqueResourceName('pinned', 'form');
    const processDefinitionId = `linked-form-deployment-${suffix}`;
    const jobType = `linked-form-deployment-job-${suffix}`;

    const [pinnedForm] = await deployInlineForms(
      request,
      [{fileName, content: formResourceContent(formId, 'Applicant name')}],
      [
        linkedFormProcess(processDefinitionId, jobType, [
          {
            resourceId: formId,
            resourceType: FORM_RESOURCE_TYPE,
            bindingType: 'deployment',
            linkName: LINK_NAME,
          },
        ]),
      ],
    );

    const laterVersion = await deployInlineForm(
      request,
      fileName,
      formResourceContent(formId, 'Full legal name'),
    );
    expect(laterVersion.formKey).not.toBe(pinnedForm.formKey);

    await startProcessInstance(request, processDefinitionId);
    const job = await activateJobAndGetHeaders(request, jobType);
    const [link] = parseLinkedResourcesHeader(job.customHeaders);

    expect(link.resourceKey).toBe(pinnedForm.formKey);
    await completeJob(request, job.jobKey);
  });

  test('Linked form with versionTag binding resolves to the tagged version', async ({
    request,
  }) => {
    const suffix = generateUniqueId();
    const formId = `tagged-form-${suffix}`;
    const fileName = uniqueResourceName('tagged', 'form');
    const versionTag = `v-${suffix}`;
    const processDefinitionId = `linked-form-tag-${suffix}`;
    const jobType = `linked-form-tag-job-${suffix}`;

    const taggedVersion = await deployInlineForm(
      request,
      fileName,
      formResourceContent(formId, 'Applicant name', versionTag),
    );
    await deployInlineForm(
      request,
      fileName,
      formResourceContent(formId, 'Full legal name'),
    );
    await deployInlineFiles(request, [
      linkedFormProcess(processDefinitionId, jobType, [
        {
          resourceId: formId,
          resourceType: FORM_RESOURCE_TYPE,
          bindingType: 'versionTag',
          versionTag,
          linkName: LINK_NAME,
        },
      ]),
    ]);

    await startProcessInstance(request, processDefinitionId);
    const job = await activateJobAndGetHeaders(request, jobType);
    const [link] = parseLinkedResourcesHeader(job.customHeaders);

    expect(link.resourceKey).toBe(taggedVersion.formKey);
    await completeJob(request, job.jobKey);
  });

  test('Worker can fetch the linked form schema using the key from the job header', async ({
    request,
  }) => {
    const suffix = generateUniqueId();
    const formId = `render-form-${suffix}`;
    const schema = formResourceContent(formId, 'Applicant name');
    const processDefinitionId = `linked-form-fetch-${suffix}`;
    const jobType = `linked-form-fetch-job-${suffix}`;

    await deployInlineForm(
      request,
      uniqueResourceName('render', 'form'),
      schema,
    );
    await deployInlineFiles(request, [
      linkedFormProcess(processDefinitionId, jobType, [
        {
          resourceId: formId,
          resourceType: FORM_RESOURCE_TYPE,
          bindingType: 'latest',
          linkName: LINK_NAME,
        },
      ]),
    ]);

    await startProcessInstance(request, processDefinitionId);
    const job = await activateJobAndGetHeaders(request, jobType);
    const [link] = parseLinkedResourcesHeader(job.customHeaders);

    await expect(async () => {
      const res = await getFormByKey(request, link.resourceKey);
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.formId).toBe(formId);
      expect(JSON.parse(body.schema)).toEqual(JSON.parse(schema));
    }).toPass(defaultAssertionOptions);

    await completeJob(request, job.jobKey);
  });

  test('A service task can link a form and a generic resource at the same time', async ({
    request,
  }) => {
    const suffix = generateUniqueId();
    const formId = `mixed-form-${suffix}`;
    const genericResourceName = `prompt-${suffix}.md`;
    const processDefinitionId = `linked-form-mixed-${suffix}`;
    const jobType = `linked-form-mixed-job-${suffix}`;

    const form = await deployInlineForm(
      request,
      uniqueResourceName('mixed', 'form'),
      formResourceContent(formId, 'Applicant name'),
    );
    const genericResource = await deployInlineResource(
      request,
      genericResourceName,
      '# System prompt',
    );
    await deployInlineFiles(request, [
      linkedFormProcess(processDefinitionId, jobType, [
        {
          resourceId: formId,
          resourceType: FORM_RESOURCE_TYPE,
          bindingType: 'latest',
          linkName: LINK_NAME,
        },
        {
          resourceId: genericResourceName,
          resourceType: 'GenericScript',
          bindingType: 'latest',
          linkName: 'my_prompt',
        },
      ]),
    ]);

    await startProcessInstance(request, processDefinitionId);
    const job = await activateJobAndGetHeaders(request, jobType);
    const links = parseLinkedResourcesHeader(job.customHeaders);

    expect(links).toHaveLength(2);
    expect(links).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          resourceType: FORM_RESOURCE_TYPE,
          linkName: LINK_NAME,
          resourceKey: form.formKey,
        }),
        expect.objectContaining({
          resourceType: 'GenericScript',
          linkName: 'my_prompt',
          resourceKey: genericResource.resourceKey,
        }),
      ]),
    );
    await completeJob(request, job.jobKey);
  });

  test('A linked form that does not exist raises a FORM_NOT_FOUND incident', async ({
    request,
  }) => {
    const suffix = generateUniqueId();
    const missingFormId = `absent-form-${suffix}`;
    const processDefinitionId = `linked-form-missing-${suffix}`;
    const jobType = `linked-form-missing-job-${suffix}`;

    await deployInlineFiles(request, [
      linkedFormProcess(processDefinitionId, jobType, [
        {
          resourceId: missingFormId,
          resourceType: FORM_RESOURCE_TYPE,
          bindingType: 'latest',
          linkName: LINK_NAME,
        },
      ]),
    ]);

    const processInstanceKey = await startProcessInstance(
      request,
      processDefinitionId,
    );

    await expect(async () => {
      const res = await request.post(
        buildUrl('/process-instances/{processInstanceKey}/incidents/search', {
          processInstanceKey,
        }),
        {headers: jsonHeaders(), data: {filter: {state: 'ACTIVE'}}},
      );
      await assertStatusCode(res, 200);
      const {items} = await res.json();
      expect(items).toHaveLength(1);
      expect(items[0].errorType).toBe('FORM_NOT_FOUND');
      expect(items[0].errorMessage).toContain(missingFormId);
    }).toPass(defaultAssertionOptions);
  });
});
