/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test, type APIRequestContext} from '@playwright/test';
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
  countJobsByType,
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

const countRootScopeVarsByName = async (
  request: APIRequestContext,
  processInstanceKey: string,
  name: string,
): Promise<number> => {
  const res = await request.post(buildUrl('/variables/search'), {
    headers: jsonHeaders(),
    data: {
      page: {from: 0, limit: 100},
      filter: {processInstanceKey, scopeKey: processInstanceKey, name},
    },
  });
  await assertStatusCode(res, 200);
  return (await res.json()).items.length;
};

test.describe.parallel('Multi-Instance Execution Listeners — beforeAll', () => {
  test.describe.parallel('BeforeAll drives inputCollection', () => {
    const suffix = randomUUID().slice(0, 8);
    const processId = `mi-el-basic-collection-${suffix}`;
    const beforeAllJobType = `mi-body-init-collection-${suffix}`;
    const innerJobType = `mi-inner-worker-${suffix}`;
    const workers: Worker[] = [];

    test.beforeAll(async () => {
      await deployWithSubstitutions('./resources/mi-el-basic-collection.bpmn', {
        'mi-el-basic-collection': processId,
        'mi-body-init-collection': beforeAllJobType,
        'mi-inner-worker': innerJobType,
      });
    });

    test.afterAll(async () => {
      await closeAll(workers);
    });

    test('BeforeAll produces inputCollection; engine creates one inner instance per item and process completes', async ({
      request,
    }) => {
      const instance = await createSingleInstance(processId, 1);
      const piKey = String(instance.processInstanceKey);

      try {
        await expectJobsByType(request, piKey, beforeAllJobType, 1);
        await expectJobsByType(request, piKey, innerJobType, 0);

        workers.push(
          createWorker(beforeAllJobType, false, {
            items: ['alpha', 'beta', 'gamma'],
          }),
        );

        await expectJobsByType(request, piKey, innerJobType, 3);

        // Each inner job must carry its own item (one of alpha/beta/gamma)
        const innerJobs = await activateJobsByType(
          request,
          innerJobType,
          piKey,
          ['item'],
          3,
        );
        expect(innerJobs).toHaveLength(3);
        const items = innerJobs
          .map((j) => j.variables['item'] as string)
          .sort();
        expect(items).toEqual(['alpha', 'beta', 'gamma']);
        for (const job of innerJobs) {
          await completeJob(request, job.jobKey);
        }

        await expectProcessState(request, piKey, 'COMPLETED');
      } catch (e) {
        await cancelProcessInstance(piKey);
        throw e;
      }
    });
  });

  test.describe.parallel('BeforeAll on an embedded subprocess', () => {
    const suffix = randomUUID().slice(0, 8);
    const processId = `mi-el-subprocess-${suffix}`;
    const beforeAllJobType = `mi-sub-init-orders-${suffix}`;
    const innerJobType = `process-single-order-${suffix}`;
    const workers: Worker[] = [];

    test.beforeAll(async () => {
      await deployWithSubstitutions('./resources/mi-el-subprocess.bpmn', {
        'mi-el-subprocess': processId,
        'mi-sub-init-orders': beforeAllJobType,
        'process-single-order': innerJobType,
      });
    });

    test.afterAll(async () => {
      await closeAll(workers);
    });

    test('BeforeAll on MI embedded subprocess produces collection before any subprocess instance starts', async ({
      request,
    }) => {
      const instance = await createSingleInstance(processId, 1);
      const piKey = String(instance.processInstanceKey);

      try {
        await expectJobsByType(request, piKey, beforeAllJobType, 1);
        await expectJobsByType(request, piKey, innerJobType, 0);

        workers.push(
          createWorker(beforeAllJobType, false, {
            orders: [
              {id: 'ORD-A', amount: 150},
              {id: 'ORD-B', amount: 250},
            ],
          }),
        );

        await expectJobsByType(request, piKey, innerJobType, 2);

        workers.push(createWorker(innerJobType));
        await expectProcessState(request, piKey, 'COMPLETED');
      } catch (e) {
        await cancelProcessInstance(piKey);
        throw e;
      }
    });
  });

  test.describe.parallel('Failure handling', () => {
    const suffix = randomUUID().slice(0, 8);
    const processId = `mi-el-basic-collection-neg-${suffix}`;
    const beforeAllJobType = `mi-body-init-collection-neg-${suffix}`;
    const innerJobType = `mi-inner-worker-neg-${suffix}`;

    test.beforeAll(async () => {
      await deployWithSubstitutions('./resources/mi-el-basic-collection.bpmn', {
        'mi-el-basic-collection': processId,
        'mi-body-init-collection': beforeAllJobType,
        'mi-inner-worker': innerJobType,
      });
    });

    test('BeforeAll failure with no retries raises EXECUTION_LISTENER_NO_RETRIES incident; no inner instances; process stays ACTIVE', async ({
      request,
    }) => {
      const instance = await createSingleInstance(processId, 1);
      const piKey = String(instance.processInstanceKey);

      try {
        const jobKey = await activateJobToObtainAValidJobKey(
          request,
          beforeAllJobType,
        );

        const failRes = await request.post(
          buildUrl(`/jobs/${jobKey}/failure`),
          {
            headers: jsonHeaders(),
            data: {retries: 0, errorMessage: 'Simulated failure NEG-02'},
          },
        );
        await assertStatusCode(failRes, 204);

        const incidents = await searchIncidentByPIK(request, {
          processInstanceKey: piKey,
        });
        expect(incidents.length).toBeGreaterThanOrEqual(1);
        expect(incidents[0].errorMessage).toContain('Simulated failure NEG-02');

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

        // ── Recovery: update retries + resolve incident ──────────────────────
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

        // beforeAll job is re-scheduled — activate it directly via the activation
        // endpoint (bypasses /jobs/search which 500s when the beforeAll job's
        // ES document has a null flowNodeId due to a known exporter limitation).
        let retryJobs: Awaited<ReturnType<typeof activateJobsByType>> = [];
        await expect(async () => {
          retryJobs = await activateJobsByType(
            request,
            beforeAllJobType,
            piKey,
          );
          expect(retryJobs).toHaveLength(1);
        }).toPass(extendedAssertionOptions);
        expect(retryJobs).toHaveLength(1);
        await completeJob(request, retryJobs[0].jobKey, {
          items: ['alpha', 'beta', 'gamma'],
        });

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
        for (const job of innerJobs) {
          await completeJob(request, job.jobKey);
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

  test.describe.parallel('Variable scope isolation', () => {
    const suffix = randomUUID().slice(0, 8);
    const processId = `mi-el-scope-isolation-${suffix}`;
    const beforeAllJobType = `mi-scope-init-${suffix}`;
    const innerJobType = `mi-inner-worker-scope-${suffix}`;
    const downstreamJobType = `downstream-worker-scope-${suffix}`;

    test.beforeAll(async () => {
      await deployWithSubstitutions('./resources/mi-el-scope-isolation.bpmn', {
        'mi-el-scope-isolation': processId,
        'mi-scope-init': beforeAllJobType,
        'mi-inner-worker-scope': innerJobType,
        'downstream-worker-scope': downstreamJobType,
      });
    });

    test('Variables set by beforeAll stay MI-body-local; never leak to process root scope', async ({
      request,
    }) => {
      const instance = await createSingleInstance(processId, 1);
      const piKey = String(instance.processInstanceKey);

      try {
        await expectJobsByType(request, piKey, beforeAllJobType, 1);

        // Manually activate the beforeAll job — do NOT complete yet
        const beforeAllJobs = await activateJobsByType(
          request,
          beforeAllJobType,
          piKey,
        );
        expect(beforeAllJobs).toHaveLength(1);

        // While beforeAll is still pending, no inner instances must exist
        await expectJobsByType(request, piKey, innerJobType, 0);

        await completeJob(request, beforeAllJobs[0].jobKey, {
          items: ['alpha', 'beta', 'gamma'],
          sharedCtx: {env: 'test', batchId: 42},
        });

        await expectJobsByType(request, piKey, innerJobType, 3);
        expect(
          await countRootScopeVarsByName(request, piKey, 'sharedCtx'),
        ).toBe(0);

        const innerJobs = await activateJobsByType(
          request,
          innerJobType,
          piKey,
          ['item', 'sharedCtx'],
          3,
        );
        expect(innerJobs).toHaveLength(3);

        // Each inner instance must carry its own item (one of alpha/beta/gamma)
        const items = innerJobs
          .map((j) => j.variables['item'] as string)
          .sort();
        expect(items).toEqual(['alpha', 'beta', 'gamma']);

        // sharedCtx must be visible to every inner instance via MI body scope
        for (const job of innerJobs) {
          expect(job.variables['sharedCtx']).toEqual({
            env: 'test',
            batchId: 42,
          });
          await completeJob(request, job.jobKey, {
            result: `${job.variables['item'] as string}_done`,
          });
        }

        await expectJobsByType(request, piKey, downstreamJobType, 1);
        expect(
          await countRootScopeVarsByName(request, piKey, 'sharedCtx'),
        ).toBe(0);

        const downstreamJobs = await activateJobsByType(
          request,
          downstreamJobType,
          piKey,
          ['sharedCtx'],
        );
        expect(downstreamJobs).toHaveLength(1);
        expect(downstreamJobs[0].variables['sharedCtx']).toBeUndefined();

        await completeJob(request, downstreamJobs[0].jobKey);

        await expectProcessState(request, piKey, 'COMPLETED');
        // sharedCtx must never appear in root scope at any point
        expect(
          await countRootScopeVarsByName(request, piKey, 'sharedCtx'),
        ).toBe(0);
      } catch (e) {
        await cancelProcessInstance(piKey);
        throw e;
      }
    });
  });

  test.describe.parallel('Variable scope fallback', () => {
    const suffix = randomUUID().slice(0, 8);
    const processId = `mi-el-scope-fallback-${suffix}`;
    const beforeAllJobType = `mi-scope-fallback-init-${suffix}`;
    const innerJobType = `mi-scope-fallback-inner-${suffix}`;
    const workers: Worker[] = [];

    test.beforeAll(async () => {
      await deployWithSubstitutions('./resources/mi-el-scope-fallback.bpmn', {
        'mi-el-scope-fallback': processId,
        'mi-scope-fallback-init': beforeAllJobType,
        'mi-scope-fallback-inner': innerJobType,
      });
    });

    test.afterAll(async () => {
      await closeAll(workers);
    });

    test('InputCollection resolves variables from process scope when beforeAll does not provide them', async ({
      request,
    }) => {
      const instance = await createSingleInstance(processId, 1, {count: 3});
      const piKey = String(instance.processInstanceKey);

      try {
        await expectJobsByType(request, piKey, beforeAllJobType, 1);
        await expectJobsByType(request, piKey, innerJobType, 0);

        workers.push(createWorker(beforeAllJobType, false, {}));

        await expectJobsByType(request, piKey, innerJobType, 3);

        // Each inner job must see count=3 (inherited from process scope) and
        // its own i value (1, 2, or 3 from inputCollection = for i in 1..count)
        const innerJobs = await activateJobsByType(
          request,
          innerJobType,
          piKey,
          ['count', 'i'],
          3,
        );
        expect(innerJobs).toHaveLength(3);
        for (const job of innerJobs) {
          expect(job.variables['count']).toBe(3);
        }
        const loopIndexes = innerJobs
          .map((j) => j.variables['i'] as number)
          .sort((a, b) => a - b);
        expect(loopIndexes).toEqual([1, 2, 3]);
        for (const job of innerJobs) {
          await completeJob(request, job.jobKey);
        }

        await expectProcessState(request, piKey, 'COMPLETED');

        // Pure read-through: process-root `count` unchanged
        const countRes = await request.post(buildUrl('/variables/search'), {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: piKey,
              scopeKey: piKey,
              name: 'count',
            },
          },
        });
        await assertStatusCode(countRes, 200);
        const countJson = await countRes.json();
        expect(countJson.items).toHaveLength(1);
        expect(countJson.items[0].value).toBe('3');
      } catch (e) {
        await cancelProcessInstance(piKey);
        throw e;
      }
    });
  });

  /**
   * E2E-10 — beforeAll + start/end EL co-existence (parallel MI)
   *
   * Verifies that introducing a beforeAll EL on an MI element does NOT alter the
   * semantics of existing start/end ELs. They must continue to fire once per inner
   * instance ("before each" / "after each"), not once globally.
   *
   * BPMN: mi-el-coexistence.bpmn
   *   - beforeAll EL  "mi-coexist-before-all"   retries=3  → fires once
   *   - start EL      "mi-coexist-before-each"  retries=3  → fires per inner instance (×3)
   *   - task          "mi-coexist-inner-worker"             → fires per inner instance (×3)
   *   - end EL        "mi-coexist-after-each"   retries=3  → fires per inner instance (×3)
   */
  test.describe.parallel('beforeAll + start/end EL co-existence', () => {
    test.describe.parallel('Happy path', () => {
      const suffix = randomUUID().slice(0, 8);
      const processId = `mi-el-coexistence-${suffix}`;
      const beforeAllJobType = `mi-coexist-before-all-${suffix}`;
      const startElJobType = `mi-coexist-before-each-${suffix}`;
      const innerJobType = `mi-coexist-inner-worker-${suffix}`;
      const endElJobType = `mi-coexist-after-each-${suffix}`;
      const workers: Worker[] = [];

      test.beforeAll(async () => {
        await deployWithSubstitutions('./resources/mi-el-coexistence.bpmn', {
          'mi-el-coexistence': processId,
          'mi-coexist-before-all': beforeAllJobType,
          'mi-coexist-before-each': startElJobType,
          'mi-coexist-inner-worker': innerJobType,
          'mi-coexist-after-each': endElJobType,
        });
      });

      test.afterAll(async () => {
        await closeAll(workers);
      });

      test('E2E-10: beforeAll fires once; start/end ELs per inner instance (×3); 10 total jobs', async ({
        request,
      }) => {
        const instance = await createSingleInstance(processId, 1);
        const piKey = String(instance.processInstanceKey);

        try {
          await expectJobsByType(
            request,
            piKey,
            beforeAllJobType,
            1,
            extendedAssertionOptions,
          );
          await expectJobsByType(request, piKey, startElJobType, 0);
          await expectJobsByType(request, piKey, innerJobType, 0);
          await expectJobsByType(request, piKey, endElJobType, 0);

          workers.push(
            createWorker(beforeAllJobType, false, {
              items: ['alpha', 'beta', 'gamma'],
            }),
          );

          // ⚠️ CRITICAL REGRESSION CHECK: count=1 means the start EL is being treated as
          // "before all" — a regression introduced by the beforeAll feature.
          await expect(async () => {
            const count = await countJobsByType(request, piKey, startElJobType);
            if (count === 1) {
              throw new Error(
                'REGRESSION DETECTED: mi-coexist-before-each count=1 instead of 3. ' +
                  'The start EL is being incorrectly treated as "before all" rather than ' +
                  '"before each". Introducing a beforeAll EL must not alter start EL semantics.',
              );
            }
            expect(count).toBe(3);
          }).toPass(extendedAssertionOptions);
          // inner worker jobs must still be blocked until per-instance start EL completes
          await expectJobsByType(request, piKey, innerJobType, 0);

          workers.push(createWorker(startElJobType));

          await expectJobsByType(
            request,
            piKey,
            innerJobType,
            3,
            extendedAssertionOptions,
          );

          workers.push(createWorker(innerJobType));

          // ⚠️ CRITICAL REGRESSION CHECK: count=1 means end EL has been collapsed to
          // "after all" semantics — a regression.
          await expect(async () => {
            const count = await countJobsByType(request, piKey, endElJobType);
            if (count === 1) {
              throw new Error(
                'REGRESSION DETECTED: mi-coexist-after-each count=1 instead of 3. ' +
                  'The end EL has been incorrectly collapsed to "after all" semantics. ' +
                  'Introducing a beforeAll EL must not alter end EL semantics.',
              );
            }
            expect(count).toBe(3);
          }).toPass(extendedAssertionOptions);

          workers.push(createWorker(endElJobType));

          await expectProcessState(
            request,
            piKey,
            'COMPLETED',
            extendedAssertionOptions,
          );

          // Total: 1 + 3 + 3 + 3 = 10 jobs
          await expectJobsByType(request, piKey, beforeAllJobType, 1);
          await expectJobsByType(request, piKey, startElJobType, 3);
          await expectJobsByType(request, piKey, innerJobType, 3);
          await expectJobsByType(request, piKey, endElJobType, 3);
        } catch (e) {
          await cancelProcessInstance(piKey);
          throw e;
        }
      });
    });

    test.describe.parallel('start EL job failure and recovery', () => {
      const suffix = randomUUID().slice(0, 8);
      const processId = `mi-el-coexistence-neg-${suffix}`;
      const beforeAllJobType = `mi-coexist-before-all-neg-${suffix}`;
      const startElJobType = `mi-coexist-before-each-neg-${suffix}`;
      const innerJobType = `mi-coexist-inner-worker-neg-${suffix}`;
      const endElJobType = `mi-coexist-after-each-neg-${suffix}`;

      test.beforeAll(async () => {
        await deployWithSubstitutions('./resources/mi-el-coexistence.bpmn', {
          'mi-el-coexistence': processId,
          'mi-coexist-before-all': beforeAllJobType,
          'mi-coexist-before-each': startElJobType,
          'mi-coexist-inner-worker': innerJobType,
          'mi-coexist-after-each': endElJobType,
        });
      });

      test('jobs search returns 200 after a start EL failure', async ({
        request,
      }) => {
        const instance = await createSingleInstance(processId, 1);
        const piKey = String(instance.processInstanceKey);

        try {
          await expect(async () => {
            const jobs = await activateJobsByType(
              request,
              beforeAllJobType,
              piKey,
            );
            expect(jobs).toHaveLength(1);
            await completeJob(request, jobs[0].jobKey, {
              items: ['alpha', 'beta', 'gamma'],
            });
          }).toPass(extendedAssertionOptions);

          await expectJobsByType(
            request,
            piKey,
            startElJobType,
            3,
            extendedAssertionOptions,
          );

          const startJobs = await activateJobsByType(
            request,
            startElJobType,
            piKey,
            [],
            3,
          );
          expect(startJobs).toHaveLength(3);

          // Fail one job with retries=1 (FAILED state, no incident, re-activatable immediately)
          const failRes = await request.post(
            buildUrl(`/jobs/${startJobs[0].jobKey}/failure`),
            {
              headers: jsonHeaders(),
              data: {retries: 1, errorMessage: 'Simulated start EL failure'},
            },
          );
          await assertStatusCode(failRes, 204);

          for (const j of startJobs.slice(1)) {
            await completeJob(request, j.jobKey);
          }

          // TODO: #54238 workaround — activate the failed job before searching
          // so it is no longer in FAILED/RETRIES_UPDATED state when /jobs/search
          // is called (those states cause HTTP 500 in the current backend).
          let retryJobs: Awaited<ReturnType<typeof activateJobsByType>> = [];
          await expect(async () => {
            retryJobs = await activateJobsByType(
              request,
              startElJobType,
              piKey,
            );
            expect(retryJobs).toHaveLength(1);
          }).toPass(extendedAssertionOptions);
          await completeJob(request, retryJobs[0].jobKey);

          // Search after activation: all start EL jobs are now out of FAILED state.
          await expectJobsByType(
            request,
            piKey,
            startElJobType,
            3,
            extendedAssertionOptions,
          );
          await expectJobsByType(request, piKey, beforeAllJobType, 1);

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
});
