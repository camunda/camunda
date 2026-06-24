/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {randomUUID} from 'crypto';
import {readFileSync} from 'node:fs';
import {
  cancelProcessInstance,
  createSingleInstance,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {extendedAssertionOptions} from '../../../../utils/constants';
import {
  activateJobAndGetHeaders,
  activateJobToObtainAValidJobKey,
  completeJob,
  expectJobsByType,
  expectProcessState,
} from '@requestHelpers';
import {searchIncidentByPIK} from '../../../../utils/requestHelpers/incident-requestHelpers';

test.describe.parallel('Cancel Process Instance Execution Listener', () => {
  test.describe.parallel('Single cancel listener with headers', () => {
    const suffix = randomUUID().slice(0, 8);
    const processId = `el-cancel-process-${suffix}`;
    const cancelListenerType = `el-cancel-listener-${suffix}`;
    const mainTaskType = `el-cancel-main-task-${suffix}`;

    test.beforeAll(async () => {
      await deployWithSubstitutions('./resources/el-cancel-process.bpmn', {
        'el-cancel-process': processId,
        'el-cancel-listener': cancelListenerType,
        'el-cancel-main-task': mainTaskType,
      });
    });

    test('E2E-CEL-01: cancel listener job is created; instance terminates only after listener completes', async ({
      request,
    }) => {
      const instance = await createSingleInstance(processId, 1);
      const piKey = String(instance.processInstanceKey);

      try {
        await expectProcessState(request, piKey, 'ACTIVE');
        await expectJobsByType(request, piKey, cancelListenerType, 0);

        await cancelProcessInstance(piKey);
        await expectJobsByType(request, piKey, cancelListenerType, 1);

        const searchRes = await request.post(
          buildUrl('/process-instances/search'),
          {
            headers: jsonHeaders(),
            data: {filter: {processInstanceKey: piKey}},
          },
        );
        await assertStatusCode(searchRes, 200);
        const beforeJson = await searchRes.json();
        expect(beforeJson.items).toHaveLength(1);
        expect(beforeJson.items[0].state).not.toBe('TERMINATED');

        const jobKey = await activateJobToObtainAValidJobKey(
          request,
          cancelListenerType,
        );
        await completeJob(request, jobKey);

        await expectProcessState(
          request,
          piKey,
          'TERMINATED',
          extendedAssertionOptions,
        );
      } catch (e) {
        await cancelProcessInstance(piKey);
        throw e;
      }
    });

    test('E2E-CEL-02: cancel listener job carries the custom headers defined in the BPMN', async ({
      request,
    }) => {
      const instance = await createSingleInstance(processId, 1);
      const piKey = String(instance.processInstanceKey);

      try {
        await cancelProcessInstance(piKey);
        await expectJobsByType(request, piKey, cancelListenerType, 1);

        const job = await activateJobAndGetHeaders(request, cancelListenerType);
        expect(job.customHeaders).toMatchObject({
          reason: 'user-cancellation',
          audit: 'true',
        });

        await completeJob(request, job.jobKey);
        await expectProcessState(
          request,
          piKey,
          'TERMINATED',
          extendedAssertionOptions,
        );
      } catch (e) {
        await cancelProcessInstance(piKey);
        throw e;
      }
    });
  });

  test.describe.parallel('Multiple cancel listeners', () => {
    const suffix = randomUUID().slice(0, 8);
    const processId = `el-cancel-process-multi-${suffix}`;
    const firstType = `el-cancel-listener-first-${suffix}`;
    const secondType = `el-cancel-listener-second-${suffix}`;
    const mainType = `el-cancel-main-multi-${suffix}`;

    test.beforeAll(async () => {
      await deployWithSubstitutions(
        './resources/el-cancel-process-multi.bpmn',
        {
          'el-cancel-process-multi': processId,
          'el-cancel-listener-first': firstType,
          'el-cancel-listener-second': secondType,
          'el-cancel-main-multi': mainType,
        },
      );
    });

    test('E2E-CEL-03: cancel listeners run sequentially in declaration order; instance terminates only after the last one', async ({
      request,
    }) => {
      const instance = await createSingleInstance(processId, 1);
      const piKey = String(instance.processInstanceKey);

      try {
        await cancelProcessInstance(piKey);

        await expectJobsByType(request, piKey, firstType, 1);
        await expectJobsByType(request, piKey, secondType, 0);

        const firstJob = await activateJobToObtainAValidJobKey(
          request,
          firstType,
        );
        await completeJob(request, firstJob);

        await expectJobsByType(request, piKey, secondType, 1);
        const intermediate = await request.post(
          buildUrl('/process-instances/search'),
          {
            headers: jsonHeaders(),
            data: {filter: {processInstanceKey: piKey}},
          },
        );
        await assertStatusCode(intermediate, 200);
        const intermediateJson = await intermediate.json();
        expect(intermediateJson.items).toHaveLength(1);
        expect(intermediateJson.items[0].state).not.toBe('TERMINATED');

        const secondJob = await activateJobToObtainAValidJobKey(
          request,
          secondType,
        );
        await completeJob(request, secondJob);

        await expectProcessState(
          request,
          piKey,
          'TERMINATED',
          extendedAssertionOptions,
        );
      } catch (e) {
        await cancelProcessInstance(piKey);
        throw e;
      }
    });
  });

  test.describe.parallel('Failure handling', () => {
    const suffix = randomUUID().slice(0, 8);
    const processId = `el-cancel-process-neg-${suffix}`;
    const cancelListenerType = `el-cancel-listener-neg-${suffix}`;
    const mainTaskType = `el-cancel-main-task-neg-${suffix}`;

    test.beforeAll(async () => {
      await deployWithSubstitutions('./resources/el-cancel-process.bpmn', {
        'el-cancel-process': processId,
        'el-cancel-listener': cancelListenerType,
        'el-cancel-main-task': mainTaskType,
      });
    });

    test('E2E-CEL-04: failed cancel listener with no retries raises EXECUTION_LISTENER_NO_RETRIES incident; stays ACTIVE', async ({
      request,
    }) => {
      const instance = await createSingleInstance(processId, 1);
      const piKey = String(instance.processInstanceKey);

      try {
        await cancelProcessInstance(piKey);
        await expectJobsByType(request, piKey, cancelListenerType, 1);

        const jobKey = await activateJobToObtainAValidJobKey(
          request,
          cancelListenerType,
        );
        const failRes = await request.post(
          buildUrl(`/jobs/${jobKey}/failure`),
          {
            headers: jsonHeaders(),
            data: {
              retries: 0,
              errorMessage: 'Simulated cancel-listener failure CEL-04',
            },
          },
        );
        await assertStatusCode(failRes, 204);

        const incidents = await searchIncidentByPIK(request, {
          processInstanceKey: piKey,
        });
        expect(incidents.length).toBeGreaterThanOrEqual(1);
        expect(incidents[0].errorMessage).toContain(
          'Simulated cancel-listener failure CEL-04',
        );

        const incidentDetail = await request.post(
          buildUrl('/incidents/search'),
          {
            headers: jsonHeaders(),
            data: {filter: {processInstanceKey: piKey}},
          },
        );
        await assertStatusCode(incidentDetail, 200);
        expect((await incidentDetail.json()).items[0].errorType).toBe(
          'EXECUTION_LISTENER_NO_RETRIES',
        );

        await expectProcessState(
          request,
          piKey,
          'ACTIVE',
          extendedAssertionOptions,
        );

        const updateRes = await request.patch(buildUrl(`/jobs/${jobKey}`), {
          headers: jsonHeaders(),
          data: {changeset: {retries: 1}},
        });
        await assertStatusCode(updateRes, 204);

        const resolveRes = await request.post(
          buildUrl(`/incidents/${incidents[0].incidentKey}/resolution`),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(resolveRes, 204);

        let retriedJobKey = 0;
        await expect(async () => {
          retriedJobKey = await activateJobToObtainAValidJobKey(
            request,
            cancelListenerType,
          );
        }).toPass(extendedAssertionOptions);
        await completeJob(request, retriedJobKey);

        await expectProcessState(
          request,
          piKey,
          'TERMINATED',
          extendedAssertionOptions,
        );
      } finally {
        await cancelProcessInstance(piKey);
      }
    });
  });

  test.describe.parallel('Validation', () => {
    test('E2E-CEL-05: deploying a `cancel` listener on a non-process element is rejected at deploy time', async () => {
      const filePath = './resources/el-cancel-on-task-invalid.bpmn';

      const xml = readFileSync(filePath, 'utf-8');
      expect(xml).toContain('eventType="cancel"');

      let caught: Error | undefined;
      try {
        await deployWithSubstitutions(filePath, {
          'el-cancel-on-task-invalid': `el-cancel-on-task-invalid-${randomUUID().slice(0, 8)}`,
          'el-cancel-invalid-on-task': `el-cancel-invalid-on-task-${randomUUID().slice(0, 8)}`,
          'el-cancel-invalid-main': `el-cancel-invalid-main-${randomUUID().slice(0, 8)}`,
        });
      } catch (e) {
        caught = e as Error;
      }

      expect(caught, 'Deployment should have failed validation').toBeDefined();
      const message = String(
        (caught as Error & {details?: string}).details ?? caught?.message ?? '',
      );
      expect(message).toMatch(/cancel.*execution listener/i);
      expect(message).toMatch(/only.*supported.*process/i);
    });
  });
});
