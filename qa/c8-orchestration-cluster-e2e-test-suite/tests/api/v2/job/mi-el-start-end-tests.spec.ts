/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {randomUUID} from 'crypto';
import {
  cancelProcessInstance,
  createSingleInstance,
  createWorker,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {extendedAssertionOptions} from '../../../../utils/constants';
import {
  activateJobToObtainAValidJobKey,
  activateJobsByType,
  completeJob,
  expectJobsByType,
  expectProcessState,
} from '@requestHelpers';
import {searchIncidentByPIK} from '../../../../utils/requestHelpers/incident-requestHelpers';

type Worker = {close: () => Promise<unknown> | unknown};

const closeAll = async (workers: Worker[]) => {
  for (const w of workers) {
    try {
      await w.close();
    } catch {
      // best-effort cleanup
    }
  }
};

test.describe
  .parallel('Multi-Instance Execution Listeners — start/end EL (no beforeAll)', () => {
  test.describe.parallel('Happy path', () => {
    const suffix = randomUUID().slice(0, 8);
    const processId = `mi-el-start-end-${suffix}`;
    const startElJobType = `mi-coexist-before-each-${suffix}`;
    const innerJobType = `mi-coexist-inner-worker-${suffix}`;
    const endElJobType = `mi-coexist-after-each-${suffix}`;
    const workers: Worker[] = [];

    test.beforeAll(async () => {
      await deployWithSubstitutions('./resources/mi-el-start-end.bpmn', {
        'mi-el-coexistence': processId,
        'mi-coexist-before-each': startElJobType,
        'mi-coexist-inner-worker': innerJobType,
        'mi-coexist-after-each': endElJobType,
      });
    });

    test.afterAll(async () => {
      await closeAll(workers);
    });

    test('start/end ELs fire per inner instance (×3); process completes with 9 total jobs', async ({
      request,
    }) => {
      const instance = await createSingleInstance(processId, 1, {
        items: ['alpha', 'beta', 'gamma'],
      });
      const piKey = String(instance.processInstanceKey);

      try {
        await expectJobsByType(
          request,
          piKey,
          startElJobType,
          3,
          extendedAssertionOptions,
        );
        await expectJobsByType(request, piKey, innerJobType, 0);
        await expectJobsByType(request, piKey, endElJobType, 0);

        workers.push(createWorker(startElJobType));

        await expectJobsByType(
          request,
          piKey,
          innerJobType,
          3,
          extendedAssertionOptions,
        );
        await expectJobsByType(request, piKey, startElJobType, 3);

        workers.push(createWorker(innerJobType));

        await expectJobsByType(
          request,
          piKey,
          endElJobType,
          3,
          extendedAssertionOptions,
        );

        workers.push(createWorker(endElJobType));

        await expectProcessState(
          request,
          piKey,
          'COMPLETED',
          extendedAssertionOptions,
        );

        // Total: 3 + 3 + 3 = 9 jobs
        await expectJobsByType(request, piKey, startElJobType, 3);
        await expectJobsByType(request, piKey, innerJobType, 3);
        await expectJobsByType(request, piKey, endElJobType, 3);
      } catch (e) {
        await cancelProcessInstance(piKey);
        throw e;
      }
    });
  });

  test.describe.parallel('Failure handling', () => {
    const suffix = randomUUID().slice(0, 8);
    const processId = `mi-el-start-end-neg-${suffix}`;
    const startElJobType = `mi-coexist-before-each-neg-${suffix}`;
    const innerJobType = `mi-coexist-inner-worker-neg-${suffix}`;
    const endElJobType = `mi-coexist-after-each-neg-${suffix}`;

    test.beforeAll(async () => {
      await deployWithSubstitutions('./resources/mi-el-start-end.bpmn', {
        'mi-el-coexistence': processId,
        'mi-coexist-before-each': startElJobType,
        'mi-coexist-inner-worker': innerJobType,
        'mi-coexist-after-each': endElJobType,
      });
    });

    test('start EL failure with no retries raises NO_RETRIES incident', async ({
      request,
    }) => {
      const instance = await createSingleInstance(processId, 1, {
        items: ['alpha', 'beta', 'gamma'],
      });
      const piKey = String(instance.processInstanceKey);

      try {
        await expectJobsByType(
          request,
          piKey,
          startElJobType,
          3,
          extendedAssertionOptions,
        );

        const jobKey = await activateJobToObtainAValidJobKey(
          request,
          startElJobType,
        );

        const failRes = await request.post(
          buildUrl(`/jobs/${jobKey}/failure`),
          {
            headers: jsonHeaders(),
            data: {retries: 0, errorMessage: 'Simulated start EL failure'},
          },
        );
        await assertStatusCode(failRes, 204);

        // Regression check #1 (#54238 fixed): /jobs/search must return HTTP 200
        // even when an EL job is in FAILED state. Before the fix, null elementId
        // (flowNodeId) caused HTTP 500. Assert state=FAILED, elementId='miTask'
        // (not null/empty), confirming the fix and making the check precise.
        await expect(async () => {
          const res = await request.post(buildUrl('/jobs/search'), {
            headers: jsonHeaders(),
            data: {filter: {processInstanceKey: piKey, type: startElJobType}},
          });
          await assertStatusCode(res, 200);
          const items = (await res.json()).items as Array<{
            jobKey: string;
            elementId: string;
            state: string;
          }>;
          const failedJob = items.find((j) => j.jobKey === String(jobKey));
          expect(failedJob).toBeDefined();
          expect(failedJob?.state).toBe('FAILED');
          expect(failedJob?.elementId).toBe('miTask');
        }).toPass(extendedAssertionOptions);

        const incidents = await searchIncidentByPIK(request, {
          processInstanceKey: piKey,
        });
        expect(incidents.length).toBeGreaterThanOrEqual(1);
        expect(incidents[0].errorMessage).toContain(
          'Simulated start EL failure',
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
        await expectJobsByType(
          request,
          piKey,
          innerJobType,
          0,
          extendedAssertionOptions,
        );

        const updateRes = await request.patch(buildUrl(`/jobs/${jobKey}`), {
          headers: jsonHeaders(),
          data: {changeset: {retries: 1}},
        });
        await assertStatusCode(updateRes, 204);

        // Regression check #2 (#54238 fixed): /jobs/search must return HTTP 200
        // even when the EL job is in RETRIES_UPDATED state. Assert state on the
        // specific job to confirm it is in RETRIES_UPDATED and the search works.
        await expect(async () => {
          const res = await request.post(buildUrl('/jobs/search'), {
            headers: jsonHeaders(),
            data: {filter: {processInstanceKey: piKey, type: startElJobType}},
          });
          await assertStatusCode(res, 200);
          const items = (await res.json()).items as Array<{
            jobKey: string;
            state: string;
          }>;
          const retriedJob = items.find((j) => j.jobKey === String(jobKey));
          expect(retriedJob).toBeDefined();
          expect(retriedJob?.state).toBe('RETRIES_UPDATED');
        }).toPass(extendedAssertionOptions);

        const resolveRes = await request.post(
          buildUrl(`/incidents/${incidents[0].incidentKey}/resolution`),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(resolveRes, 204);

        // All 3 start EL jobs (1 retried + 2 fresh) are activatable after resolution.
        // Wrapped in toPass because activation filters client-side by PI key and
        // may return fewer than 3 on the first call if not all jobs are activatable yet.
        let allStartJobs: Awaited<ReturnType<typeof activateJobsByType>> = [];
        await expect(async () => {
          allStartJobs = await activateJobsByType(
            request,
            startElJobType,
            piKey,
            [],
            3,
          );
          expect(allStartJobs).toHaveLength(3);
        }).toPass(extendedAssertionOptions);
        for (const j of allStartJobs) {
          await completeJob(request, j.jobKey);
        }

        await expectJobsByType(
          request,
          piKey,
          innerJobType,
          3,
          extendedAssertionOptions,
        );
        const innerJobs = await activateJobsByType(
          request,
          innerJobType,
          piKey,
          [],
          3,
        );
        expect(innerJobs).toHaveLength(3);
        for (const j of innerJobs) {
          await completeJob(request, j.jobKey);
        }

        await expectJobsByType(
          request,
          piKey,
          endElJobType,
          3,
          extendedAssertionOptions,
        );
        const endJobs = await activateJobsByType(
          request,
          endElJobType,
          piKey,
          [],
          3,
        );
        expect(endJobs).toHaveLength(3);
        for (const j of endJobs) {
          await completeJob(request, j.jobKey);
        }

        await expectProcessState(
          request,
          piKey,
          'COMPLETED',
          extendedAssertionOptions,
        );
      } catch (e) {
        await cancelProcessInstance(piKey);
        throw e;
      }
    });
  });
});
