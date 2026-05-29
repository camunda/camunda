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

    test('start EL failure with no retries raises EXECUTION_LISTENER_NO_RETRIES incident; /jobs/search returns 200 in FAILED and RETRIES_UPDATED states', async ({
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

        // TODO: #54238: https://github.com/camunda/camunda/issues/54238
        // /jobs/search returns HTTP 500 in FAILED/RETRIES_UPDATED state (ES-only,
        // null flowNodeId). Workaround: activate before searching to avoid those states.
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

        // TODO: #54238 workaround — activate before searching.
        // After incident resolution all 3 start EL jobs are activatable:
        // 1 retried + 2 fresh (never-activated) instances. Activating first
        // moves them all out of FAILED/RETRIES_UPDATED state so that the
        // subsequent /jobs/search call returns 200 instead of 500.
        let allStartJobs: Awaited<ReturnType<typeof activateJobsByType>> = [];
        await expect(async () => {
          allStartJobs = await activateJobsByType(
            request,
            startElJobType,
            piKey,
          );
          expect(allStartJobs).toHaveLength(3);
        }).toPass(extendedAssertionOptions);

        // Search after activation: no jobs in FAILED/RETRIES_UPDATED state.
        await expectJobsByType(
          request,
          piKey,
          startElJobType,
          3,
          extendedAssertionOptions,
        );

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
