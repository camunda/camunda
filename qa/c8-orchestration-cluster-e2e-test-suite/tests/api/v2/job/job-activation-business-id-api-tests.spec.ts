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
  cancelProcessInstance,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {
  defaultAssertionOptions,
  generateUniqueId,
  uniqueBusinessId,
} from '../../../../utils/constants';

const PROCESS_INSTANCE_ENDPOINT = '/process-instances';
const JOB_ACTIVATION_ENDPOINT = '/jobs/activation';
const SERVICE_TASK_RESOURCE =
  './resources/service_task_business_id_process.bpmn';

async function deployUniqueServiceTaskProcess() {
  const suffix = generateUniqueId();
  const processId = `service_task_bizid_${suffix}`;
  const jobType = `service_task_bizid_job_${suffix}`;
  await deployWithSubstitutions(SERVICE_TASK_RESOURCE, {
    service_task_business_id_process: processId,
    service_task_business_id_job: jobType,
  });
  return {processId, jobType};
}

async function startInstance(
  request: APIRequestContext,
  processId: string,
  businessId?: string,
) {
  const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
    headers: jsonHeaders(),
    data: {
      processDefinitionId: processId,
      ...(businessId !== undefined ? {businessId} : {}),
    },
  });
  await assertStatusCode(res, 200);
  return (await res.json()).processInstanceKey as string;
}

async function activateOneJob(request: APIRequestContext, jobType: string) {
  const res = await request.post(buildUrl(JOB_ACTIVATION_ENDPOINT), {
    headers: jsonHeaders(),
    data: {type: jobType, timeout: 10000, maxJobsToActivate: 1},
  });
  await assertStatusCode(res, 200);
  return (await res.json()).jobs as Record<string, unknown>[];
}

test.describe.parallel('Activate Jobs - Business ID API', () => {
  test('Activated job returns the owning instance Business ID', async ({
    request,
  }) => {
    const {processId, jobType} = await deployUniqueServiceTaskProcess();
    const businessId = uniqueBusinessId('job-activate');
    const processInstanceKey = await startInstance(
      request,
      processId,
      businessId,
    );

    await test.step('Activate the job and verify the Business ID', async () => {
      await expect(async () => {
        const jobs = await activateOneJob(request, jobType);
        expect(jobs).toHaveLength(1);
        expect(jobs[0].processInstanceKey).toBe(processInstanceKey);
        expect(jobs[0].businessId).toBe(businessId);
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(processInstanceKey);
  });

  test('Activated job returns a null Business ID when the owning instance has none', async ({
    request,
  }) => {
    const {processId, jobType} = await deployUniqueServiceTaskProcess();
    const processInstanceKey = await startInstance(request, processId);

    await test.step('Activate the job and verify the Business ID is null', async () => {
      await expect(async () => {
        const jobs = await activateOneJob(request, jobType);
        expect(jobs).toHaveLength(1);
        expect(jobs[0].processInstanceKey).toBe(processInstanceKey);
        expect(jobs[0].businessId == null).toBe(true);
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(processInstanceKey);
  });
});
