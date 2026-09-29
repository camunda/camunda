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
  completeJob,
  createInstanceOnceDeployed,
  expectNoIncidents,
  expectProcessState,
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

/**
 * Runs the work a boundary event spawned and waits for its branch to end. The
 * model cannot reach a terminal state — one branch parks on an event-based
 * gateway a year out — so draining the revived branch is the available proof
 * that the work runs, rather than a job record that merely appeared.
 */
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

/**
 * The start times of every tick the boundary timer has produced, oldest first.
 *
 * Timestamps rather than counts: the export into secondary storage lags by an
 * unknown amount, so a count read at an arbitrary moment cannot tell a
 * double-fire from a legitimate tick that arrived while the reader was behind.
 * The recorded times say which it was no matter when they are read.
 */
async function tickStartTimes(
  request: APIRequestContext,
  processInstanceKey: string,
): Promise<number[]> {
  const res = await request.post(buildUrl('/element-instances/search'), {
    headers: jsonHeaders(),
    data: {
      filter: {processInstanceKey, elementId: 'Activity_tick'},
      page: {limit: 50},
    },
  });
  await assertStatusCode(res, 200);
  const items: Array<{startDate: string}> = (await res.json()).items ?? [];
  return items
    .map((item) => new Date(item.startDate).getTime())
    .sort((a, b) => a - b);
}

/** Waits until the timer has produced `expected` ticks. */
async function expectTickCount(
  request: APIRequestContext,
  processInstanceKey: string,
  expected: number,
  assertionOptions = extendedAssertionOptions,
): Promise<number[]> {
  let times: number[] = [];
  await expect(async () => {
    times = await tickStartTimes(request, processInstanceKey);
    expect(times).toHaveLength(expected);
  }).toPass(assertionOptions);
  return times;
}

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
    // The duration must outlast the indexing wait below, or the timer fires
    // before the suspend; the suspension must then outlast the due date, or
    // "still waiting" only means "not due yet".
    const TIMER_SECONDS = 30;
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

    // Still waiting, past its due date.
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
    await expectTickCount(request, fixture.processInstanceKey, 1);

    await suspendAndExpectSuspended(request, fixture.processInstanceKey);
    await hold(70);
    expect(
      await tickStartTimes(request, fixture.processInstanceKey),
    ).toHaveLength(1);

    await assertStatusCode(
      await resumeProcessInstance(request, fixture.processInstanceKey),
      204,
    );

    // Three intervals were missed, and the cadence re-arms afterwards, so the
    // contract allows exactly two more ticks: the catch-up and the next one.
    const times = await expectTickCount(request, fixture.processInstanceKey, 3);

    // The whole point of #62637: the reschedule anchored on now instead of one
    // interval past it, so the catch-up was followed immediately by a second
    // fire. A gap of a full interval is what says it did not happen — and
    // because this reads recorded times, a slow export cannot fake it either
    // way.
    expect(times[2]! - times[1]!).toBeGreaterThan(15_000);
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
