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
 * cycles while suspended fires once, rescheduled one interval past now.
 * Asserting "at least one fire" would pass straight through #62637, where the
 * reschedule snapped onto now and fired a second time immediately (fixed by
 * #63687, and present on 8.7 through 8.10).
 *
 * These tests wait on wall-clock timers, so they are slow by nature — the
 * developers' ProcessInstanceSuspendResumeTimerIT drives a controlled clock
 * instead. What it cannot cover is the wall-clock path a customer hits.
 */

const instancesToCancel: string[] = [];

async function deployDurationTimerProcess(processDefinitionId: string) {
  const deployment = await deployWithSubstitutions(
    './resources/timerIntermediateCatchEvent.bpmn',
    {timerIntermediateCatchEventProcess: processDefinitionId},
  );
  return deployment.processes[0];
}

/**
 * The child is substituted too and its job left unworked: the boundary timers
 * are attached to the call activity, so they stay armed only while the child
 * keeps running.
 */
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
      dueDate: '2027-01-01T00:00:00Z',
      correlationKey: uniquePrefixedId(`${prefix}-corr`),
      amount,
    },
  );
  instancesToCancel.push(instance.processInstanceKey);
  return {...fixture, processInstanceKey: instance.processInstanceKey};
}

/**
 * Runs the work a boundary event spawned and waits for its branch to end.
 *
 * The model cannot reach a terminal state — one branch parks on an event-based
 * gateway whose timer is years out — so completing the branch the resume
 * revived is the strongest available evidence that the work is genuinely
 * runnable, rather than a job record that merely appeared.
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
  // One end event per job: a non-interrupting boundary spawns its own token
  // each time it fires, so the branch is only fully drained when every one of
  // them has reached the end.
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

/**
 * The cadence re-arms 20s after the catch-up fire, so a count that must equal
 * an exact value has only that window to be observed in. Poll it fast rather
 * than on the shared interval, which can first look after the next tick landed.
 */
const promptAssertionOptions = {
  intervals: [250, 250, 500, 1000, 2000],
  timeout: 15_000,
};

/** Holds for a fixed stretch. Absences cannot be polled for. */
async function hold(seconds: number) {
  await new Promise((resolve) => setTimeout(resolve, seconds * 1000));
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
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1, {
      duration: 'PT15S',
    });
    instancesToCancel.push(instance.processInstanceKey);
    await searchElementInstanceByElementIdAndState(
      request,
      instance.processInstanceKey,
      'timer-catch',
      'ACTIVE',
    );

    await suspendAndExpectSuspended(request, instance.processInstanceKey);
    await hold(25);

    // Still waiting, 10s past its due date.
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

    // One tick before the suspension, so the assertions below are about the gap
    // rather than about the timer having never fired.
    await expectJobCount(
      request,
      fixture.processInstanceKey,
      fixture.tickJobType,
      1,
    );

    await suspendAndExpectSuspended(request, fixture.processInstanceKey);
    await hold(70);
    expect(
      await countJobs(request, fixture.processInstanceKey, fixture.tickJobType),
    ).toBe(1);

    await assertStatusCode(
      await resumeProcessInstance(request, fixture.processInstanceKey),
      204,
    );

    // Exactly one fire for three missed intervals, and it stays one: the
    // reschedule anchors an interval past now rather than snapping onto now.
    await expectJobCount(
      request,
      fixture.processInstanceKey,
      fixture.tickJobType,
      2,
      promptAssertionOptions,
    );
    await hold(10);
    expect(
      await countJobs(request, fixture.processInstanceKey, fixture.tickJobType),
    ).toBe(2);

    // A catch-up that fires once and then stalls would satisfy everything above.
    await expectJobCount(
      request,
      fixture.processInstanceKey,
      fixture.tickJobType,
      3,
    );
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
    // The conditional boundary is attached to the call activity, so it is only
    // subscribed once that element is active. Suspending before then would
    // leave the condition first observed after the resume, which is not the
    // case under test.
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
