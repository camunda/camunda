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
import {
  defaultAssertionOptions,
  extendedAssertionOptions,
} from '../../../../utils/constants';
import {
  activateJobToObtainAValidJobKey,
  activateJobsByType,
  completeJob,
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

const countJobsByType = async (
  request: APIRequestContext,
  processInstanceKey: string,
  type: string,
): Promise<number> => {
  const res = await request.post(buildUrl('/jobs/search'), {
    headers: jsonHeaders(),
    data: {filter: {processInstanceKey, type}, page: {limit: 100}},
  });
  await assertStatusCode(res, 200);
  return (await res.json()).items.length;
};

const expectJobsByType = async (
  request: APIRequestContext,
  processInstanceKey: string,
  type: string,
  expected: number,
  assertionOptions = defaultAssertionOptions,
): Promise<void> => {
  await expect(async () => {
    expect(await countJobsByType(request, processInstanceKey, type)).toBe(
      expected,
    );
  }).toPass(assertionOptions);
};

const expectProcessState = async (
  request: APIRequestContext,
  processInstanceKey: string,
  state: 'ACTIVE' | 'COMPLETED',
  assertionOptions = defaultAssertionOptions,
): Promise<void> => {
  await expect(async () => {
    const res = await request.post(buildUrl('/process-instances/search'), {
      headers: jsonHeaders(),
      data: {filter: {processInstanceKey}},
    });
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.items).toHaveLength(1);
    expect(json.items[0].state).toBe(state);
  }).toPass(assertionOptions);
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

        // ── Recovery: resolve the incident and verify MI proceeds ────────────
        // Update retries to re-enable activation, then resolve the incident
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

        // MI body now proceeds normally
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
        // ── Step 1: beforeAll EL is blocking ────────────────────────────────
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

        // Complete beforeAll with items (drives inputCollection) + sharedCtx (scope-isolation var)
        await completeJob(request, beforeAllJobs[0].jobKey, {
          items: ['alpha', 'beta', 'gamma'],
          sharedCtx: {env: 'test', batchId: 42},
        });

        // ── Step 2: inner instances — verify scope inheritance ──────────────
        await expectJobsByType(request, piKey, innerJobType, 3);
        expect(
          await countRootScopeVarsByName(request, piKey, 'sharedCtx'),
        ).toBe(0);

        // Activate all 3 inner jobs, fetching item + sharedCtx
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
          // Complete with item-specific result per spec
          await completeJob(request, job.jobKey, {
            result: `${job.variables['item'] as string}_done`,
          });
        }

        // ── Step 3: downstream job — sharedCtx must be absent ───────────────
        await expectJobsByType(request, piKey, downstreamJobType, 1);
        expect(
          await countRootScopeVarsByName(request, piKey, 'sharedCtx'),
        ).toBe(0);

        // Activate downstream job fetching sharedCtx — must not be present
        const downstreamJobs = await activateJobsByType(
          request,
          downstreamJobType,
          piKey,
          ['sharedCtx'],
        );
        expect(downstreamJobs).toHaveLength(1);
        expect(downstreamJobs[0].variables['sharedCtx']).toBeUndefined();

        await completeJob(request, downstreamJobs[0].jobKey);

        // ── Step 4: process completion — final root scope check ─────────────
        await expectProcessState(request, piKey, 'COMPLETED');
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

        // Pure read-through: process-root `count` unchanged.
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
});
