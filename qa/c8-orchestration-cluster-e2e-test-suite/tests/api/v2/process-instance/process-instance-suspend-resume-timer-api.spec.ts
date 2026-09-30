/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, expect, test} from '@playwright/test';
import {
  cancelProcessInstance,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {
  activateJobsByType,
  elementInstanceStartTimes,
  expectElementInstancesStartedAfter,
  completeJob,
  createInstanceOnceDeployed,
  expectNoIncidents,
  expectProcessState,
  getProcessInstance,
  resumeProcessInstance,
  searchElementInstanceByElementIdAndState,
  suspendAndExpectSuspended,
} from '@requestHelpers';
import {
  extendedAssertionOptions,
  uniquePrefixedId,
} from '../../../../utils/constants';

/**
 * The catch-up contract from ADR 0009: a repeating timer that missed several
 * cycles while suspended fires once, rescheduled one interval past now. Counts
 * are exact on purpose — "at least one fire" passes straight through #62637.
 */

const instancesToCancel: string[] = [];

async function deployDurationTimerProcess(processDefinitionId: string) {
  const deployment = await deployWithSubstitutions(
    './resources/timerIntermediateCatchEvent.bpmn',
    {timerIntermediateCatchEventProcess: processDefinitionId},
  );
  return deployment.processes[0];
}

/** The child's job is left unworked: the boundary timers stay armed only while
 * the call activity runs. */
async function deployCycleTimerProcess(prefix: string) {
  const childId = `${prefix}-child`;
  const processDefinitionId = `${prefix}-timer`;
  const tickJobType = `${prefix}-tick`;
  const thresholdJobType = `${prefix}-threshold`;
  await deployWithSubstitutions('./resources/childProcess_v_1.bpmn', {
    'id="childProcess"': `id="${childId}"`,
    'type="Task"': `type="${prefix}-child-job"`,
  });
  await deployWithSubstitutions(
    './resources/repeating_boundary_timer_process.bpmn',
    {
      'id="updatable_boundary_timer_process"': `id="${processDefinitionId}"`,
      'type="sr-tick"': `type="${tickJobType}"`,
      'type="sr-threshold"': `type="${thresholdJobType}"`,
    },
  );
  return {processDefinitionId, childId, tickJobType, thresholdJobType};
}

async function startCycleTimerInstance(prefix: string, amount = 10) {
  const fixture = await deployCycleTimerProcess(prefix);
  const instance = await createInstanceOnceDeployed(
    fixture.processDefinitionId,
    1,
    {
      dynamicProcessId: fixture.childId,
      // Relative: a hardcoded date becomes an already-due timer once it passes.
      dueDate: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString(),
      correlationKey: uniquePrefixedId(`${prefix}-corr`),
      amount,
    },
  );
  instancesToCancel.push(instance.processInstanceKey);
  return {...fixture, processInstanceKey: instance.processInstanceKey};
}

/** The model cannot reach a terminal state — a branch parks on an event-based
 * gateway a year out — so draining the revived branch is the proof available. */
async function completeBoundaryWork(
  request: APIRequestContext,
  processInstanceKey: string,
  jobType: string,
  endElementId: string,
) {
  const jobs = await activateJobsByType(
    request,
    jobType,
    processInstanceKey,
    [],
    10,
  );
  expect(jobs.length).toBeGreaterThan(0);
  for (const job of jobs) {
    await completeJob(request, job.jobKey);
  }
  // One end event per job — a non-interrupting boundary spawns a token per fire.
  await expect(async () => {
    const res = await request.post(buildUrl('/element-instances/search'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          processInstanceKey,
          elementId: endElementId,
          state: 'COMPLETED',
        },
      },
    });
    await assertStatusCode(res, 200);
    expect((await res.json()).items ?? []).toHaveLength(jobs.length);
  }).toPass(extendedAssertionOptions);
  await expectNoIncidents(request, processInstanceKey);
}

async function countJobs(
  request: APIRequestContext,
  processInstanceKey: string,
  type: string,
): Promise<number> {
  const res = await request.post(buildUrl('/jobs/search'), {
    headers: jsonHeaders(),
    data: {filter: {processInstanceKey, type}},
  });
  await assertStatusCode(res, 200);
  return ((await res.json()).items ?? []).length;
}

const TICK_ELEMENT = 'Activity_tick';

const tickTimes = (request: APIRequestContext, processInstanceKey: string) =>
  elementInstanceStartTimes(request, processInstanceKey, TICK_ELEMENT);

const ticksAfter = (
  request: APIRequestContext,
  processInstanceKey: string,
  since: number,
  expected: number,
) =>
  expectElementInstancesStartedAfter(
    request,
    processInstanceKey,
    TICK_ELEMENT,
    since,
    expected,
    extendedAssertionOptions,
  );

async function expectJobCount(
  request: APIRequestContext,
  processInstanceKey: string,
  type: string,
  expected: number,
  assertionOptions = extendedAssertionOptions,
) {
  await expect(async () => {
    expect(await countJobs(request, processInstanceKey, type)).toBe(expected);
  }).toPass(assertionOptions);
}

/** Holds for a fixed stretch. Absences cannot be polled for. */
async function hold(seconds: number) {
  await new Promise((resolve) => setTimeout(resolve, seconds * 1000));
}

/** Waits until `instant` has passed, plus a margin. Returns immediately if it already has. */
async function holdUntilPast(instant: number, marginSeconds = 5) {
  const remainingMs = instant + marginSeconds * 1000 - Date.now();
  if (remainingMs > 0) {
    await new Promise((resolve) => setTimeout(resolve, remainingMs));
  }
}

test.describe('Process Instance Suspend and Resume Timer API', () => {
  test.afterAll(async () => {
    for (const processInstanceKey of instancesToCancel) {
      try {
        await cancelProcessInstance(processInstanceKey);
      } catch {
        // Already terminal.
      }
    }
    instancesToCancel.length = 0;
  });

  test('A timer that comes due during a suspension fires only after the resume', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('sr-timer-due');
    await deployDurationTimerProcess(processDefinitionId);
    // The duration has to outlast the readiness wait below, whose budget is
    // 60s — at 30s a slow index could let the timer fire before the suspend.
    // The suspension must then outlast the due date, or "still waiting" only
    // means "not due yet".
    const TIMER_SECONDS = 90;
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1, {
      duration: `PT${TIMER_SECONDS}S`,
    });
    const dueAt = Date.now() + TIMER_SECONDS * 1000;
    instancesToCancel.push(instance.processInstanceKey);
    await searchElementInstanceByElementIdAndState(
      request,
      instance.processInstanceKey,
      'timer-catch',
      'ACTIVE',
    );

    await suspendAndExpectSuspended(request, instance.processInstanceKey);
    await holdUntilPast(dueAt);

    await searchElementInstanceByElementIdAndState(
      request,
      instance.processInstanceKey,
      'timer-catch',
      'ACTIVE',
    );
    await expectProcessState(
      request,
      instance.processInstanceKey,
      'SUSPENDED',
      extendedAssertionOptions,
    );

    await assertStatusCode(
      await resumeProcessInstance(request, instance.processInstanceKey),
      204,
    );
    await expectProcessState(
      request,
      instance.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectNoIncidents(request, instance.processInstanceKey);
  });

  test('A timer that becomes due after the resume fires normally', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('sr-timer-after');
    await deployDurationTimerProcess(processDefinitionId);
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1, {
      duration: 'PT30S',
    });
    instancesToCancel.push(instance.processInstanceKey);

    await suspendAndExpectSuspended(request, instance.processInstanceKey);
    await assertStatusCode(
      await resumeProcessInstance(request, instance.processInstanceKey),
      204,
    );

    await expectProcessState(
      request,
      instance.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectNoIncidents(request, instance.processInstanceKey);
  });

  test('A repeating timer overdue across several intervals fires once on resume, then re-arms', async ({
    request,
  }) => {
    test.setTimeout(8 * 60 * 1000);
    const fixture = await startCycleTimerInstance(uniquePrefixedId('sr-cycle'));

    // One tick first, so what follows is about the gap, not a timer that never fired.
    await ticksAfter(request, fixture.processInstanceKey, 0, 1);

    await suspendAndExpectSuspended(request, fixture.processInstanceKey);
    const suspendedAt = new Date(
      String(
        (await getProcessInstance(request, fixture.processInstanceKey))
          .suspendedDate,
      ),
    ).getTime();
    await hold(70);
    // Against the engine's own suspension time, so a late export cannot look
    // like a tick that fired while suspended.
    expect(
      (await tickTimes(request, fixture.processInstanceKey)).filter(
        (startedAt) => startedAt > suspendedAt,
      ),
    ).toHaveLength(0);

    await assertStatusCode(
      await resumeProcessInstance(request, fixture.processInstanceKey),
      204,
    );

    // By suspension time, not position: a second tick can land before the
    // suspend, and a positional pair then straddles the suspension.
    const afterResume = await ticksAfter(
      request,
      fixture.processInstanceKey,
      suspendedAt,
      2,
    );

    // One fire for the gap: #62637 fired again immediately.
    expect(
      afterResume.filter((startedAt) => startedAt <= afterResume[0]! + 5_000),
    ).toHaveLength(1);
    // And the cadence re-arms a full interval on, rather than snapping to now.
    expect(afterResume[1]! - afterResume[0]!).toBeGreaterThan(15_000);
    await completeBoundaryWork(
      request,
      fixture.processInstanceKey,
      fixture.tickJobType,
      'Event_tick_end',
    );
  });

  test('A conditional boundary raised during a suspension fires once on resume', async ({
    request,
  }) => {
    test.setTimeout(6 * 60 * 1000);
    const fixture = await startCycleTimerInstance(uniquePrefixedId('sr-cond'));
    // The boundary subscribes only once the call activity is active.
    await searchElementInstanceByElementIdAndState(
      request,
      fixture.processInstanceKey,
      'Activity_1dpj0f1',
      'ACTIVE',
    );
    await suspendAndExpectSuspended(request, fixture.processInstanceKey);

    await assertStatusCode(
      await request.put(
        buildUrl('/element-instances/{elementInstanceKey}/variables', {
          elementInstanceKey: fixture.processInstanceKey,
        }),
        {headers: jsonHeaders(), data: {variables: {amount: 250}}},
      ),
      204,
    );

    await hold(20);
    expect(
      await countJobs(
        request,
        fixture.processInstanceKey,
        fixture.thresholdJobType,
      ),
    ).toBe(0);

    await assertStatusCode(
      await resumeProcessInstance(request, fixture.processInstanceKey),
      204,
    );
    await expectJobCount(
      request,
      fixture.processInstanceKey,
      fixture.thresholdJobType,
      1,
    );
    await completeBoundaryWork(
      request,
      fixture.processInstanceKey,
      fixture.thresholdJobType,
      'Event_threshold_end',
    );
  });
});
