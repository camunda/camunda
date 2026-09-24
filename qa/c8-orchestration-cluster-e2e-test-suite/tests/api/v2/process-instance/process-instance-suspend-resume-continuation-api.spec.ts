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
  await deployWithSubstitutions(
    './resources/suspend_resume_parallel_join_process.bpmn',
    {
      'id="sr_long_suspend"': `id="${processDefinitionId}"`,
      'name="sr-long-msg"': `name="${messageName}"`,
      'type="sr-long-msg-done"': `type="${msgJobType}"`,
      'type="sr-long-timer-done"': `type="${timerJobType}"`,
      '<timeDuration>PT5M</timeDuration>': '<timeDuration>PT40S</timeDuration>',
    },
  );
  return {processDefinitionId, messageName, msgJobType, timerJobType};
}

async function deployAsymProcess(prefix: string) {
  const processDefinitionId = `${prefix}-asym`;
  const signalName = `${prefix}-Signal`;
  const messageName = `${prefix}-asym-msg`;
  await deployWithSubstitutions(
    './resources/signal_and_message_parallel_wait_process.bpmn',
    {
      'id="sr_asym_signal_vs_message"': `id="${processDefinitionId}"`,
      'name="SrAsymSignal"': `name="${signalName}"`,
      'name="sr-asym-msg"': `name="${messageName}"`,
    },
  );
  return {processDefinitionId, signalName, messageName};
}

async function deploySignalCatchProcess(prefix: string) {
  const processDefinitionId = `${prefix}-signal`;
  const signalName = `${prefix}-Signal`;
  const jobType = `${prefix}-signal-done`;
  await deployWithSubstitutions(
    './resources/signal_catch_with_job_process.bpmn',
    {
      'id="sr_p3_signal"': `id="${processDefinitionId}"`,
      'name="SrP3Signal"': `name="${signalName}"`,
      'type="sr-p3-signal-done"': `type="${jobType}"`,
    },
  );
  return {processDefinitionId, signalName, jobType};
}

async function deployMultiInstanceProcess(prefix: string) {
  const processDefinitionId = `${prefix}-mi`;
  const jobType = `${prefix}-item-job`;
  // The user task's form has to exist before the task can be created.
  await deploy(['./resources/multi_instance_review_item_form.form']);
  await deployWithSubstitutions('./resources/multi_instance_sub_process.bpmn', {
    'id="Process_MultiInstanceSubprocess"': `id="${processDefinitionId}"`,
    'type="tesz"': `type="${jobType}"`,
  });
  return {processDefinitionId, jobType};
}

/**
 * The customer-style order model: a message start event, an interrupting
 * CANCELED boundary on the multi-instance body, a non-interrupting
 * duplicate-ORDER event subprocess, and one subscription per item. It has no
 * job types — messages and PT1S timers drive it — so these tests assert element
 * states rather than job counts.
 */
async function deployOrderProcess(prefix: string) {
  const processDefinitionId = `${prefix}-order`;
  const msg = {
    order: `${prefix}-ORDER`,
    intransit: `${prefix}-INTRANSIT`,
    delivered: `${prefix}-DELIVERED`,
    notdelivered: `${prefix}-NOTDELIVERED`,
    canceled: `${prefix}-CANCELED`,
  };
  await deployWithSubstitutions(
    './resources/order_process_with_message_events.bpmn',
    {
      'id="order_process"': `id="${processDefinitionId}"`,
      'name="ORDER"': `name="${msg.order}"`,
      'name="INTRANSIT"': `name="${msg.intransit}"`,
      'name="DELIVERED"': `name="${msg.delivered}"`,
      'name="NOTDELIVERED"': `name="${msg.notdelivered}"`,
      'name="CANCELED"': `name="${msg.canceled}"`,
    },
  );
  return {processDefinitionId, msg};
}

async function listInstanceKeys(
  request: APIRequestContext,
  processDefinitionId: string,
): Promise<string[]> {
  const res = await request.post(buildUrl('/process-instances/search'), {
    headers: jsonHeaders(),
    data: {filter: {processDefinitionId}, page: {limit: 50}},
  });
  await assertStatusCode(res, 200);
  return ((await res.json()).items ?? []).map(
    (i: {processInstanceKey: string}) => String(i.processInstanceKey),
  );
}

/** The model has no none start event: publishing the order message creates it. */
async function startOrderInstance(
  request: APIRequestContext,
  fixture: {processDefinitionId: string; msg: {order: string}},
  orderId: string,
  items: string[],
): Promise<string> {
  const before = new Set(
    await listInstanceKeys(request, fixture.processDefinitionId),
  );
  await assertStatusCode(
    await publishMessage(request, fixture.msg.order, orderId, {items, orderId}),
    200,
  );
  let created = '';
  await expect(async () => {
    const after = await listInstanceKeys(request, fixture.processDefinitionId);
    const fresh = after.filter((key) => !before.has(key));
    expect(fresh).toHaveLength(1);
    created = fresh[0];
  }).toPass(extendedAssertionOptions);
  return track(created);
}

async function countElementInstances(
  request: APIRequestContext,
  processInstanceKey: string,
  elementId: string,
  state: string,
): Promise<number> {
  const res = await request.post(buildUrl('/element-instances/search'), {
    headers: jsonHeaders(),
    data: {filter: {processInstanceKey, elementId, state}, page: {limit: 50}},
  });
  await assertStatusCode(res, 200);
  return ((await res.json()).items ?? []).length;
}

async function expectElementInstanceCount(
  request: APIRequestContext,
  processInstanceKey: string,
  elementId: string,
  state: string,
  expected: number,
) {
  await expect(async () => {
    expect(
      await countElementInstances(
        request,
        processInstanceKey,
        elementId,
        state,
      ),
    ).toBe(expected);
  }).toPass(extendedAssertionOptions);
}

/**
 * The model races a PT1M timer against a signal on an event-based gateway, with
 * a second branch parked on a user task and an inclusive join after both.
 */
async function deployEventGatewayProcess(prefix: string) {
  const processDefinitionId = `${prefix}-race`;
  const signalName = `${prefix}-Signal`;
  await deploy([
    './resources/event_based_gateway_form_b.form',
    './resources/event_based_gateway_form_c.form',
  ]);
  await deployWithSubstitutions(
    './resources/event_based_gateway_timer_signal_process.bpmn',
    {
      'id="Process_0uj1r9h"': `id="${processDefinitionId}"`,
      'name="Signal1"': `name="${signalName}"`,
    },
  );
  return {processDefinitionId, signalName};
}

async function completeAllUserTasks(
  request: APIRequestContext,
  processInstanceKey: string,
  expected: number,
) {
  const keys: string[] = [];
  await expect(async () => {
    const res = await request.post(buildUrl('/user-tasks/search'), {
      headers: jsonHeaders(),
      data: {
        filter: {processInstanceKey, state: 'CREATED'},
        page: {limit: 50},
      },
    });
    await assertStatusCode(res, 200);
    const items = (await res.json()).items ?? [];
    expect(items).toHaveLength(expected);
    keys.length = 0;
    keys.push(
      ...items.map((i: {userTaskKey: string}) => String(i.userTaskKey)),
    );
  }).toPass(extendedAssertionOptions);
  for (const userTaskKey of keys) {
    await assertStatusCode(await completeUserTask(request, userTaskKey), 204);
  }
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

  test('Every item of a suspended order receives its own message after the resume', async ({
    request,
  }) => {
    test.setTimeout(6 * 60 * 1000);
    const prefix = uniquePrefixedId('sr-cont-order');
    const fixture = await deployOrderProcess(prefix);
    const items = [`${prefix}-p`, `${prefix}-q`, `${prefix}-r`];
    const instanceKey = await startOrderInstance(
      request,
      fixture,
      `${prefix}-oid`,
      items,
    );
    await expectElementInstanceCount(
      request,
      instanceKey,
      'Event_0vxlyed',
      'ACTIVE',
      items.length,
    );

    await suspendAndExpectSuspended(request, instanceKey);
    for (const item of items) {
      await assertStatusCode(
        await publishMessage(request, fixture.msg.intransit, item),
        200,
      );
    }
    await hold(10);
    expect(
      await countElementInstances(
        request,
        instanceKey,
        'Event_0vxlyed',
        'ACTIVE',
      ),
    ).toBe(items.length);

    await assertStatusCode(
      await resumeProcessInstance(request, instanceKey),
      204,
    );

    // All three advance, so none of the per-item subscriptions was lost.
    await expectElementInstanceCount(
      request,
      instanceKey,
      'Event_11lmoe2',
      'ACTIVE',
      items.length,
    );
    for (const item of items) {
      await assertStatusCode(
        await publishMessage(request, fixture.msg.delivered, item),
        200,
      );
    }
    await expectProcessState(
      request,
      instanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectNoIncidents(request, instanceKey);
  });

  test('An interrupting boundary message waits for the resume and then cancels the order', async ({
    request,
  }) => {
    test.setTimeout(6 * 60 * 1000);
    const prefix = uniquePrefixedId('sr-cont-cancel');
    const fixture = await deployOrderProcess(prefix);
    const orderId = `${prefix}-oid`;
    const instanceKey = await startOrderInstance(request, fixture, orderId, [
      `${prefix}-p`,
      `${prefix}-q`,
    ]);
    await expectElementInstanceCount(
      request,
      instanceKey,
      'Event_0vxlyed',
      'ACTIVE',
      2,
    );

    await suspendAndExpectSuspended(request, instanceKey);
    await assertStatusCode(
      await publishMessage(request, fixture.msg.canceled, orderId),
      200,
    );
    await hold(10);
    expect(
      await countElementInstances(request, instanceKey, 'orderItem', 'ACTIVE'),
    ).toBeGreaterThan(0);

    await assertStatusCode(
      await resumeProcessInstance(request, instanceKey),
      204,
    );

    await expectElementInstanceCount(
      request,
      instanceKey,
      'Event_1x1zr5w',
      'COMPLETED',
      1,
    );
    await expectElementInstanceCount(
      request,
      instanceKey,
      'orderItem',
      'ACTIVE',
      0,
    );
    await expectProcessState(
      request,
      instanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectNoIncidents(request, instanceKey);
  });

  test('A non-interrupting event subprocess runs on resume and leaves the order running', async ({
    request,
  }) => {
    test.setTimeout(6 * 60 * 1000);
    const prefix = uniquePrefixedId('sr-cont-dup');
    const fixture = await deployOrderProcess(prefix);
    const orderId = `${prefix}-oid`;
    const items = [`${prefix}-p`, `${prefix}-q`];
    const instanceKey = await startOrderInstance(
      request,
      fixture,
      orderId,
      items,
    );
    await expectElementInstanceCount(
      request,
      instanceKey,
      'Event_0vxlyed',
      'ACTIVE',
      items.length,
    );

    await suspendAndExpectSuspended(request, instanceKey);
    // A duplicate order correlates to this instance's event subprocess rather
    // than starting a second instance, because the orderId subscription matches.
    await assertStatusCode(
      await publishMessage(request, fixture.msg.order, orderId, {
        items,
        orderId,
      }),
      200,
    );
    await hold(10);
    expect(
      await countElementInstances(
        request,
        instanceKey,
        'Activity_03jfohc',
        'COMPLETED',
      ),
    ).toBe(0);

    await assertStatusCode(
      await resumeProcessInstance(request, instanceKey),
      204,
    );

    await expectElementInstanceCount(
      request,
      instanceKey,
      'Activity_03jfohc',
      'COMPLETED',
      1,
    );
    // Non-interrupting: the items are still waiting where they were.
    await expectElementInstanceCount(
      request,
      instanceKey,
      'Event_0vxlyed',
      'ACTIVE',
      items.length,
    );

    for (const item of items) {
      await assertStatusCode(
        await publishMessage(request, fixture.msg.intransit, item),
        200,
      );
    }
    for (const item of items) {
      await assertStatusCode(
        await publishMessage(request, fixture.msg.delivered, item),
        200,
      );
    }
    await expectProcessState(
      request,
      instanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectNoIncidents(request, instanceKey);
  });

  test('A message start event still creates instances while a sibling is suspended', async ({
    request,
  }) => {
    test.setTimeout(6 * 60 * 1000);
    const prefix = uniquePrefixedId('sr-cont-start');
    const fixture = await deployOrderProcess(prefix);
    const suspendedKey = await startOrderInstance(
      request,
      fixture,
      `${prefix}-oid-a`,
      [`${prefix}-a1`],
    );
    await suspendAndExpectSuspended(request, suspendedKey);

    // A different orderId, so this cannot correlate to the suspended instance.
    const freshItems = [`${prefix}-b1`];
    const freshKey = await startOrderInstance(
      request,
      fixture,
      `${prefix}-oid-b`,
      freshItems,
    );
    expect(freshKey).not.toBe(suspendedKey);

    for (const item of freshItems) {
      await assertStatusCode(
        await publishMessage(request, fixture.msg.intransit, item),
        200,
      );
      await assertStatusCode(
        await publishMessage(request, fixture.msg.delivered, item),
        200,
      );
    }
    await expectProcessState(
      request,
      freshKey,
      'COMPLETED',
      extendedAssertionOptions,
    );

    // Untouched by its sibling's whole lifecycle.
    await expectProcessState(
      request,
      suspendedKey,
      'SUSPENDED',
      extendedAssertionOptions,
    );
    await assertStatusCode(
      await resumeProcessInstance(request, suspendedKey),
      204,
    );
    await assertStatusCode(
      await publishMessage(request, fixture.msg.intransit, `${prefix}-a1`),
      200,
    );
    await assertStatusCode(
      await publishMessage(request, fixture.msg.delivered, `${prefix}-a1`),
      200,
    );
    await expectProcessState(
      request,
      suspendedKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectNoIncidents(request, suspendedKey);
  });

  test('An event-based gateway resolves once when its timer came due during a suspension', async ({
    request,
  }) => {
    test.setTimeout(8 * 60 * 1000);
    const fixture = await deployEventGatewayProcess(
      uniquePrefixedId('sr-cont-race'),
    );
    // The exclusive gateway after both forms reads `proceed`; without it the
    // instance raises an EXTRACT_VALUE_ERROR instead of reaching its end event.
    const instance = await createInstanceOnceDeployed(
      fixture.processDefinitionId,
      1,
      {proceed: false},
    );
    track(instance.processInstanceKey);
    await searchElementInstanceByElementIdAndState(
      request,
      instance.processInstanceKey,
      'Gateway_17vhv3u',
      'ACTIVE',
    );

    await suspendAndExpectSuspended(request, instance.processInstanceKey);
    await hold(75);

    // Past the PT1M due date: the gateway has not resolved, so neither branch
    // of the race has produced its follow-up task.
    expect(
      await countElementInstances(
        request,
        instance.processInstanceKey,
        'Activity_1iaguca',
        'ACTIVE',
      ),
    ).toBe(0);

    await assertStatusCode(
      await resumeProcessInstance(request, instance.processInstanceKey),
      204,
    );

    // The buffered trigger resolves the race exactly once. A second fire would
    // activate the timer path twice and leave the inclusive join short.
    await expectElementInstanceCount(
      request,
      instance.processInstanceKey,
      'Event_1s61blt',
      'COMPLETED',
      1,
    );
    await expectElementInstanceCount(
      request,
      instance.processInstanceKey,
      'Activity_1iaguca',
      'ACTIVE',
      1,
    );

    // Form B from the parallel branch, and Form C from the timer path.
    await completeAllUserTasks(request, instance.processInstanceKey, 2);
    await expectProcessState(
      request,
      instance.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectNoIncidents(request, instance.processInstanceKey);
  });
});
