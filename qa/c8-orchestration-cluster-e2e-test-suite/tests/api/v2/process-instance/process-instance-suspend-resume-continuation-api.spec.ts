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
  deploy,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {
  activateJobsByType,
  activateSingleJob,
  completeJob,
  completeUserTask,
  createInstanceOnceDeployed,
  deployCallActivityPair,
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
 * Gate assertions prove a command was refused; they do not prove the instance
 * can still finish. These drive each model through to a terminal state and
 * assert an exact count of every job and task involved, so work that resume
 * loses or duplicates shows up.
 */

const instancesToCancel: string[] = [];

async function publishMessage(
  request: APIRequestContext,
  name: string,
  correlationKey: string,
  variables: Record<string, unknown> = {},
) {
  return request.post(buildUrl('/messages/publication'), {
    headers: jsonHeaders(),
    data: {name, correlationKey, timeToLive: 600_000, variables},
  });
}

async function broadcastSignal(request: APIRequestContext, signalName: string) {
  return request.post(buildUrl('/signals/broadcast'), {
    headers: jsonHeaders(),
    data: {signalName},
  });
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
) {
  await expect(async () => {
    expect(await countJobs(request, processInstanceKey, type)).toBe(expected);
  }).toPass(extendedAssertionOptions);
}

async function hold(seconds: number) {
  await new Promise((resolve) => setTimeout(resolve, seconds * 1000));
}

function track(processInstanceKey: string) {
  instancesToCancel.push(processInstanceKey);
  return processInstanceKey;
}

/**
 * PT5M is shortened to PT40S. The fixture's value is the shape — a message
 * subscription and an overdue timer rejoining at a parallel join — not the
 * literal five minutes, which would only add wall-clock cost.
 */
async function deployLongSuspendProcess(prefix: string) {
  const processDefinitionId = `${prefix}-long`;
  const messageName = `${prefix}-msg`;
  const msgJobType = `${prefix}-msg-done`;
  const timerJobType = `${prefix}-timer-done`;
  await deployWithSubstitutions('./resources/sr_long_suspend.bpmn', {
    'id="sr_long_suspend"': `id="${processDefinitionId}"`,
    'name="sr-long-msg"': `name="${messageName}"`,
    'type="sr-long-msg-done"': `type="${msgJobType}"`,
    'type="sr-long-timer-done"': `type="${timerJobType}"`,
    '<timeDuration>PT5M</timeDuration>': '<timeDuration>PT40S</timeDuration>',
  });
  return {processDefinitionId, messageName, msgJobType, timerJobType};
}

async function deployAsymProcess(prefix: string) {
  const processDefinitionId = `${prefix}-asym`;
  const signalName = `${prefix}-Signal`;
  const messageName = `${prefix}-asym-msg`;
  await deployWithSubstitutions('./resources/sr_asym_signal_vs_message.bpmn', {
    'id="sr_asym_signal_vs_message"': `id="${processDefinitionId}"`,
    'name="SrAsymSignal"': `name="${signalName}"`,
    'name="sr-asym-msg"': `name="${messageName}"`,
  });
  return {processDefinitionId, signalName, messageName};
}

async function deploySignalCatchProcess(prefix: string) {
  const processDefinitionId = `${prefix}-signal`;
  const signalName = `${prefix}-Signal`;
  const jobType = `${prefix}-signal-done`;
  await deployWithSubstitutions('./resources/sr_p3_signal.bpmn', {
    'id="sr_p3_signal"': `id="${processDefinitionId}"`,
    'name="SrP3Signal"': `name="${signalName}"`,
    'type="sr-p3-signal-done"': `type="${jobType}"`,
  });
  return {processDefinitionId, signalName, jobType};
}

async function deployMultiInstanceProcess(prefix: string) {
  const processDefinitionId = `${prefix}-mi`;
  const jobType = `${prefix}-item-job`;
  // The user task's form has to exist before the task can be created.
  await deploy(['./resources/review-item-1arzgj2.form']);
  await deployWithSubstitutions('./resources/multi_instance_sub_process.bpmn', {
    'id="Process_MultiInstanceSubprocess"': `id="${processDefinitionId}"`,
    'type="tesz"': `type="${jobType}"`,
  });
  return {processDefinitionId, jobType};
}

test.describe('Process Instance Suspend and Resume Continuation API', () => {
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

  test('Both branches of a long suspension resume and meet at the parallel join', async ({
    request,
  }) => {
    test.setTimeout(6 * 60 * 1000);
    const fixture = await deployLongSuspendProcess(uniquePrefixedId('sr-cont'));
    const correlationKey = uniquePrefixedId('sr-cont-corr');
    const instance = await createInstanceOnceDeployed(
      fixture.processDefinitionId,
      1,
      {corrId: correlationKey},
    );
    track(instance.processInstanceKey);
    await searchElementInstanceByElementIdAndState(
      request,
      instance.processInstanceKey,
      'waitMsg',
      'ACTIVE',
    );

    await suspendAndExpectSuspended(request, instance.processInstanceKey);
    await assertStatusCode(
      await publishMessage(request, fixture.messageName, correlationKey),
      200,
    );
    await hold(50);

    expect(
      await countJobs(request, instance.processInstanceKey, fixture.msgJobType),
    ).toBe(0);
    expect(
      await countJobs(
        request,
        instance.processInstanceKey,
        fixture.timerJobType,
      ),
    ).toBe(0);

    await assertStatusCode(
      await resumeProcessInstance(request, instance.processInstanceKey),
      204,
    );

    // The join only closes if both branches recovered, so a resume that lost
    // one of them hangs the instance here rather than failing an assertion.
    await expectJobCount(
      request,
      instance.processInstanceKey,
      fixture.msgJobType,
      1,
    );
    await expectJobCount(
      request,
      instance.processInstanceKey,
      fixture.timerJobType,
      1,
    );
    const msgJob = await activateSingleJob(
      request,
      fixture.msgJobType,
      instance.processInstanceKey,
    );
    await completeJob(request, msgJob);
    const timerJob = await activateSingleJob(
      request,
      fixture.timerJobType,
      instance.processInstanceKey,
    );
    await completeJob(request, timerJob);

    await expectProcessState(
      request,
      instance.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectNoIncidents(request, instance.processInstanceKey);
  });

  // The element-state reads inside searchElementInstanceByElementIdAndState are
  // the assertions here; the rule cannot see them.
  // eslint-disable-next-line playwright/expect-expect
  test('A message survives a suspension but a signal broadcast during it does not', async ({
    request,
  }) => {
    const fixture = await deployAsymProcess(uniquePrefixedId('sr-cont'));
    const asymKey = uniquePrefixedId('sr-cont-asym');
    const instance = await createInstanceOnceDeployed(
      fixture.processDefinitionId,
      1,
      {asymKey},
    );
    track(instance.processInstanceKey);
    await searchElementInstanceByElementIdAndState(
      request,
      instance.processInstanceKey,
      'signalCatch',
      'ACTIVE',
    );

    await suspendAndExpectSuspended(request, instance.processInstanceKey);
    await assertStatusCode(
      await publishMessage(request, fixture.messageName, asymKey),
      200,
    );
    await assertStatusCode(
      await broadcastSignal(request, fixture.signalName),
      200,
    );
    await assertStatusCode(
      await resumeProcessInstance(request, instance.processInstanceKey),
      204,
    );

    // Messages are buffered for their TTL; signals are not buffered at all, by
    // the decision in #59875. The signal branch therefore never completes, so
    // this reads element states rather than the instance's.
    await searchElementInstanceByElementIdAndState(
      request,
      instance.processInstanceKey,
      'messageArrived',
      'COMPLETED',
    );
    await searchElementInstanceByElementIdAndState(
      request,
      instance.processInstanceKey,
      'signalCatch',
      'ACTIVE',
    );
    await expectNoIncidents(request, instance.processInstanceKey);
  });

  test('A signal subscription still catches a broadcast made after the resume', async ({
    request,
  }) => {
    const fixture = await deploySignalCatchProcess(uniquePrefixedId('sr-cont'));
    const instance = await createInstanceOnceDeployed(
      fixture.processDefinitionId,
      1,
    );
    track(instance.processInstanceKey);
    await searchElementInstanceByElementIdAndState(
      request,
      instance.processInstanceKey,
      'catchSignal',
      'ACTIVE',
    );

    await suspendAndExpectSuspended(request, instance.processInstanceKey);
    await assertStatusCode(
      await resumeProcessInstance(request, instance.processInstanceKey),
      204,
    );

    // Without this, a dropped broadcast and a dead subscription look the same.
    await assertStatusCode(
      await broadcastSignal(request, fixture.signalName),
      200,
    );
    const jobKey = await activateSingleJob(
      request,
      fixture.jobType,
      instance.processInstanceKey,
    );
    await completeJob(request, jobKey);
    await expectProcessState(
      request,
      instance.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectNoIncidents(request, instance.processInstanceKey);
  });

  test('Every multi-instance task survives a suspension exactly once', async ({
    request,
  }) => {
    test.setTimeout(8 * 60 * 1000);
    const ITEMS = 10;
    const fixture = await deployMultiInstanceProcess(
      uniquePrefixedId('sr-cont'),
    );
    const instance = await createInstanceOnceDeployed(
      fixture.processDefinitionId,
      1,
      {test: Array.from({length: ITEMS}, (_, i) => i + 1)},
    );
    track(instance.processInstanceKey);

    const userTaskKeys: string[] = [];
    await expect(async () => {
      const res = await request.post(buildUrl('/user-tasks/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {processInstanceKey: instance.processInstanceKey},
          page: {limit: 50},
        },
      });
      await assertStatusCode(res, 200);
      const items = (await res.json()).items ?? [];
      expect(items).toHaveLength(ITEMS);
      userTaskKeys.length = 0;
      userTaskKeys.push(
        ...items.map((i: {userTaskKey: string}) => String(i.userTaskKey)),
      );
    }).toPass(extendedAssertionOptions);

    await suspendAndExpectSuspended(request, instance.processInstanceKey);
    await assertStatusCode(
      await resumeProcessInstance(request, instance.processInstanceKey),
      204,
    );

    for (const userTaskKey of userTaskKeys) {
      await assertStatusCode(await completeUserTask(request, userTaskKey), 204);
    }

    // None lost, none duplicated: one downstream job per item.
    await expectJobCount(
      request,
      instance.processInstanceKey,
      fixture.jobType,
      ITEMS,
    );
    const jobs = await activateJobsByType(
      request,
      fixture.jobType,
      instance.processInstanceKey,
      [],
      ITEMS,
    );
    expect(jobs).toHaveLength(ITEMS);
    for (const job of jobs) {
      await completeJob(request, job.jobKey);
    }

    await expectProcessState(
      request,
      instance.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectNoIncidents(request, instance.processInstanceKey);
  });

  test('A child instance keeps running while its parent is suspended, and the parent finishes after', async ({
    request,
  }) => {
    const prefix = uniquePrefixedId('sr-cont-parent');
    const {parentId, childId, childJobType} =
      await deployCallActivityPair(prefix);
    const parent = await createInstanceOnceDeployed(parentId, 1);
    track(parent.processInstanceKey);

    let childKey = '';
    await expect(async () => {
      const res = await request.post(buildUrl('/process-instances/search'), {
        headers: jsonHeaders(),
        data: {filter: {processDefinitionId: childId}},
      });
      await assertStatusCode(res, 200);
      const items = (await res.json()).items ?? [];
      expect(items).toHaveLength(1);
      childKey = items[0].processInstanceKey;
    }).toPass(extendedAssertionOptions);

    await suspendAndExpectSuspended(request, parent.processInstanceKey);

    // Suspension does not cascade, so the child's work is still handed out and
    // the child reaches its own terminal state while the parent is frozen.
    const childJob = await activateSingleJob(request, childJobType, childKey);
    await completeJob(request, childJob);
    await expectProcessState(
      request,
      childKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectProcessState(
      request,
      parent.processInstanceKey,
      'SUSPENDED',
      extendedAssertionOptions,
    );

    await assertStatusCode(
      await resumeProcessInstance(request, parent.processInstanceKey),
      204,
    );
    await expectProcessState(
      request,
      parent.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectNoIncidents(request, parent.processInstanceKey);
  });
});
