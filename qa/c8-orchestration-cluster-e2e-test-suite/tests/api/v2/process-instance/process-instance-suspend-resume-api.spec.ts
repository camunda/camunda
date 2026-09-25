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
import {
  assertInvalidState,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {
  activateJobsByType,
  activateSingleJob,
  completeJob,
  completeUserTask,
  createInstanceOnceDeployed,
  deployCallActivityPair,
  deployMessageCatchProcess,
  deployServiceTaskProcess,
  deployUserTaskProcess,
  expectJobsByType,
  expectNoIncidents,
  expectProcessState,
  expectSuspendedDate,
  failJob,
  findUserTask,
  getProcessInstance,
  resumeProcessInstance,
  searchElementInstanceByElementIdAndState,
  searchIncidentByPIK,
  suspendAndExpectSuspended,
  suspendProcessInstance,
} from '@requestHelpers';
import {
  extendedAssertionOptions,
  uniquePrefixedId,
} from '../../../../utils/constants';

const instancesToCancel: string[] = [];

async function deployIncidentProcess(
  processDefinitionId: string,
  jobType: string,
) {
  const deployment = await deployWithSubstitutions(
    './resources/processWithAnIncident.bpmn',
    {
      'id="processWithAnIncident"': `id="${processDefinitionId}"`,
      'type="alwaysFailingTask"': `type="${jobType}"`,
    },
  );
  return deployment.processes[0];
}

const MI_JOB_COUNT = 8;

/**
 * One instance that holds many jobs and several message subscriptions at once.
 * Suspension closes subscriptions and parks jobs in a single processor, so a
 * model carrying only one of the two cannot show the order they run in.
 */
async function deployJobsAndMessagesProcess(prefix: string) {
  const processDefinitionId = `${prefix}-jobs-msgs`;
  const jobType = `${prefix}-job`;
  const messageName = `${prefix}-MSG`;
  const cancelMessageName = `${prefix}-CANCEL`;
  await deployWithSubstitutions(
    './resources/parallel_jobs_and_message_waits_process.bpmn',
    {
      'id="sr_jobs_and_messages"': `id="${processDefinitionId}"`,
      'type="sr-jm-job"': `type="${jobType}"`,
      'name="sr-jm-msg"': `name="${messageName}"`,
      'name="sr-jm-cancel"': `name="${cancelMessageName}"`,
    },
  );
  return {processDefinitionId, jobType, messageName, cancelMessageName};
}

function correlateMessage(
  request: APIRequestContext,
  name: string,
  correlationKey: string,
) {
  return request.post(buildUrl('/messages/correlation'), {
    headers: jsonHeaders(),
    data: {name, correlationKey, variables: {}},
  });
}

/**
 * Reads the subscriptions of one instance out of secondary storage. Suspension
 * closes them in the engine but reopens them under the same keys, so what is
 * exported has to stay unchanged — a closure that leaked out would show here as
 * a DELETED row or a missing one.
 */
async function expectOpenSubscriptions(
  request: APIRequestContext,
  processInstanceKey: string,
  expected: number,
) {
  await expect(async () => {
    const res = await request.post(buildUrl('/message-subscriptions/search'), {
      headers: jsonHeaders(),
      data: {filter: {processInstanceKey}},
    });
    await assertStatusCode(res, 200);
    const items: Array<{messageSubscriptionState: string}> =
      (await res.json()).items ?? [];
    expect(items).toHaveLength(expected);
    expect(items.map((item) => item.messageSubscriptionState)).toEqual(
      Array(expected).fill('CREATED'),
    );
  }, `Expected ${expected} open subscriptions for ${processInstanceKey}`).toPass(
    extendedAssertionOptions,
  );
}

async function suspend(
  request: APIRequestContext,
  processInstanceKey: string,
  data?: Record<string, unknown>,
) {
  return suspendProcessInstance(request, processInstanceKey, data);
}

async function resume(
  request: APIRequestContext,
  processInstanceKey: string,
  data?: Record<string, unknown>,
) {
  return resumeProcessInstance(request, processInstanceKey, data);
}

/** A job must already exist, or "no job handed out" passes for the wrong reason. */
async function expectJobExists(
  request: APIRequestContext,
  processInstanceKey: string,
  type: string,
) {
  await expect(async () => {
    const res = await request.post(buildUrl('/jobs/search'), {
      headers: jsonHeaders(),
      data: {filter: {processInstanceKey, type}},
    });
    await assertStatusCode(res, 200);
    expect((await res.json()).items ?? []).toHaveLength(1);
  }).toPass(extendedAssertionOptions);
}

async function startServiceTaskInstance(prefix: string) {
  const processDefinitionId = uniquePrefixedId(prefix);
  const jobType = uniquePrefixedId(`${prefix}-job`);
  await deployServiceTaskProcess(processDefinitionId, jobType);
  const instance = await createInstanceOnceDeployed(processDefinitionId, 1);
  instancesToCancel.push(instance.processInstanceKey);
  return {
    processDefinitionId,
    jobType,
    processInstanceKey: instance.processInstanceKey,
  };
}

test.describe('Process Instance Suspend and Resume API', () => {
  test.afterAll(async ({request}) => {
    for (const processInstanceKey of instancesToCancel) {
      try {
        await cancelProcessInstance(processInstanceKey);
      } catch {
        // Already terminal — the test completed or cancelled it on purpose.
      }
    }
    instancesToCancel.length = 0;
    void request;
  });

  test('A suspended instance completes as if it had never been suspended', async ({
    request,
  }) => {
    const control = await startServiceTaskInstance('sr-control');
    const subject = await startServiceTaskInstance('sr-subject');

    await suspendAndExpectSuspended(request, subject.processInstanceKey);

    await assertStatusCode(
      await resume(request, subject.processInstanceKey),
      204,
    );
    await expectProcessState(
      request,
      subject.processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );
    await expectSuspendedDate(request, subject.processInstanceKey, false);

    for (const instance of [control, subject]) {
      const jobKey = await activateSingleJob(
        request,
        instance.jobType,
        instance.processInstanceKey,
      );
      await completeJob(request, jobKey);
      await expectProcessState(
        request,
        instance.processInstanceKey,
        'COMPLETED',
        extendedAssertionOptions,
      );
    }

    await expectNoIncidents(request, subject.processInstanceKey);
  });

  test('No job is handed out while the instance is suspended', async ({
    request,
  }) => {
    const {jobType, processInstanceKey} =
      await startServiceTaskInstance('sr-nojob');
    await expectJobExists(request, processInstanceKey, jobType);
    await suspendAndExpectSuspended(request, processInstanceKey);

    for (let attempt = 0; attempt < 3; attempt++) {
      expect(
        await activateJobsByType(
          request,
          jobType,
          processInstanceKey,
          [],
          10,
          2000,
        ),
      ).toHaveLength(0);
    }

    await assertStatusCode(await resume(request, processInstanceKey), 204);
    const jobKey = await activateSingleJob(
      request,
      jobType,
      processInstanceKey,
    );
    await completeJob(request, jobKey);
    await expectProcessState(
      request,
      processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('Suspending an already suspended instance is rejected and leaves the state untouched', async ({
    request,
  }) => {
    const {jobType, processInstanceKey} =
      await startServiceTaskInstance('sr-double');
    await suspendAndExpectSuspended(request, processInstanceKey);
    const before = await getProcessInstance(request, processInstanceKey);

    await assertInvalidState(await suspend(request, processInstanceKey));

    const after = await getProcessInstance(request, processInstanceKey);
    expect(after.state).toBe('SUSPENDED');
    expect(after.suspendedDate).toBe(before.suspendedDate);

    await assertStatusCode(await resume(request, processInstanceKey), 204);
    const jobKey = await activateSingleJob(
      request,
      jobType,
      processInstanceKey,
    );
    await completeJob(request, jobKey);
    await expectProcessState(
      request,
      processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('Resuming an instance that is not suspended is rejected', async ({
    request,
  }) => {
    const {jobType, processInstanceKey} =
      await startServiceTaskInstance('sr-notsusp');

    await assertInvalidState(await resume(request, processInstanceKey));

    await expectProcessState(
      request,
      processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );
    const jobKey = await activateSingleJob(
      request,
      jobType,
      processInstanceKey,
    );
    await completeJob(request, jobKey);
    await expectProcessState(
      request,
      processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  // Both assertions live inside assertNotFoundRequest, which the rule cannot see.
  // eslint-disable-next-line playwright/expect-expect
  test('An unknown process instance key is not found on either endpoint', async ({
    request,
  }) => {
    // A hardcoded key is not safe here: on a busy cluster it can belong to a
    // real instance, which answers 409 instead of 404. Offsetting a freshly
    // created key keeps the same partition and lands far past its sequence.
    const {processInstanceKey} = await startServiceTaskInstance('sr-unknown');
    const unknownKey = (BigInt(processInstanceKey) + 1_000_000n).toString();

    await assertNotFoundRequest(await suspend(request, unknownKey), unknownKey);
    await assertNotFoundRequest(await resume(request, unknownKey), unknownKey);
  });

  test('A terminal instance cannot be suspended', async ({request}) => {
    const completed = await startServiceTaskInstance('sr-completed');
    const jobKey = await activateSingleJob(
      request,
      completed.jobType,
      completed.processInstanceKey,
    );
    await completeJob(request, jobKey);
    await expectProcessState(
      request,
      completed.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );

    const terminated = await startServiceTaskInstance('sr-terminated');
    await cancelProcessInstance(terminated.processInstanceKey);
    await expectProcessState(
      request,
      terminated.processInstanceKey,
      'TERMINATED',
      extendedAssertionOptions,
    );

    for (const processInstanceKey of [
      completed.processInstanceKey,
      terminated.processInstanceKey,
    ]) {
      await assertNotFoundRequest(
        await suspend(request, processInstanceKey),
        processInstanceKey,
      );
    }
  });

  test('A call activity child instance is suspended on its own, without the parent', async ({
    request,
  }) => {
    const prefix = uniquePrefixedId('sr-child');
    const {parentId, childId, childJobType} =
      await deployCallActivityPair(prefix);
    const parent = await createInstanceOnceDeployed(parentId, 1);
    instancesToCancel.push(parent.processInstanceKey);

    let childKey = '';
    await expect(async () => {
      const res = await request.post(buildUrl('/process-instances/search'), {
        headers: jsonHeaders(),
        data: {filter: {processDefinitionId: childId}},
      });
      await assertStatusCode(res, 200);
      const items = (await res.json()).items ?? [];
      expect(items).toHaveLength(1);
      expect(items[0].parentProcessInstanceKey).toBe(parent.processInstanceKey);
      childKey = items[0].processInstanceKey;
    }).toPass(extendedAssertionOptions);

    // Suspension does not cascade, so a child is eligible on its own — the
    // root-only rule was a docs error (#60625, removed by #60657).
    await suspendAndExpectSuspended(request, childKey);
    await expectProcessState(
      request,
      parent.processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );
    await expectSuspendedDate(request, parent.processInstanceKey, false);

    // Cancel does cascade, so it stays root-only. Asserting both here keeps the
    // asymmetry from being "fixed" into consistency later.
    const cancelRes = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/cancellation', {
        processInstanceKey: childKey,
      }),
      {headers: jsonHeaders()},
    );
    await assertInvalidState(cancelRes);
    expect(JSON.stringify(await cancelRes.json())).toContain(
      parent.processInstanceKey,
    );

    await assertStatusCode(await resume(request, childKey), 204);
    const childJobKey = await activateSingleJob(
      request,
      childJobType,
      childKey,
    );
    await completeJob(request, childJobKey);
    await expectProcessState(
      request,
      parent.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('Cancelling a suspended instance clears the suspension for good', async ({
    request,
  }) => {
    const {processInstanceKey} = await startServiceTaskInstance('sr-cancel');
    await suspendAndExpectSuspended(request, processInstanceKey);

    await cancelProcessInstance(processInstanceKey);
    await expectProcessState(
      request,
      processInstanceKey,
      'TERMINATED',
      extendedAssertionOptions,
    );
    await expectSuspendedDate(request, processInstanceKey, false);

    await assertNotFoundRequest(
      await resume(request, processInstanceKey),
      processInstanceKey,
    );
  });

  test('Suspension accepts no body and an empty body, and rejects a malformed one', async ({
    request,
  }) => {
    const first = await startServiceTaskInstance('sr-body-a');
    await assertStatusCode(
      await suspend(request, first.processInstanceKey),
      204,
    );
    await assertStatusCode(
      await resume(request, first.processInstanceKey, {}),
      204,
    );

    const second = await startServiceTaskInstance('sr-body-b');
    await assertStatusCode(
      await suspend(request, second.processInstanceKey, {}),
      204,
    );
    await assertStatusCode(
      await resume(request, second.processInstanceKey, {
        operationReference: 7730102,
      }),
      204,
    );

    const third = await startServiceTaskInstance('sr-body-c');
    for (const operationReference of [-1, 'not-a-number']) {
      await assertStatusCode(
        await suspend(request, third.processInstanceKey, {operationReference}),
        400,
      );
    }
    await expectProcessState(
      request,
      third.processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );
  });

  test('Suspension and resumption require authentication', async ({
    request,
  }) => {
    const {processInstanceKey} = await startServiceTaskInstance('sr-noauth');

    for (const path of [
      '/process-instances/{processInstanceKey}/suspension',
      '/process-instances/{processInstanceKey}/resumption',
    ] as const) {
      const res = await request.post(buildUrl(path, {processInstanceKey}), {
        headers: {'Content-Type': 'application/json'},
      });
      await assertUnauthorizedRequest(res);
    }

    await expectProcessState(
      request,
      processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );
  });

  test('Job commands are rejected while the instance is suspended', async ({
    request,
  }) => {
    const {jobType, processInstanceKey} =
      await startServiceTaskInstance('sr-jobgate');
    // Activate first, so the rejections are about suspension and not about there
    // being no job to act on.
    const jobKey = await activateSingleJob(
      request,
      jobType,
      processInstanceKey,
    );
    await suspendAndExpectSuspended(request, processInstanceKey);

    await assertInvalidState(
      await request.post(
        buildUrl('/jobs/{jobKey}/completion', {jobKey: String(jobKey)}),
        {
          headers: jsonHeaders(),
          data: {},
        },
      ),
    );
    await assertInvalidState(
      await request.post(
        buildUrl('/jobs/{jobKey}/failure', {jobKey: String(jobKey)}),
        {
          headers: jsonHeaders(),
          data: {retries: 1, errorMessage: 'suspended'},
        },
      ),
    );
    await assertInvalidState(
      await request.post(
        buildUrl('/jobs/{jobKey}/error', {jobKey: String(jobKey)}),
        {
          headers: jsonHeaders(),
          data: {errorCode: 'suspended-error'},
        },
      ),
    );

    await assertStatusCode(await resume(request, processInstanceKey), 204);
    const resumedJobKey = await activateSingleJob(
      request,
      jobType,
      processInstanceKey,
    );
    await completeJob(request, resumedJobKey);
    await expectProcessState(
      request,
      processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('User task commands are rejected while the instance is suspended', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('sr-utgate');
    await deployUserTaskProcess(processDefinitionId);
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1);
    instancesToCancel.push(instance.processInstanceKey);
    const userTaskKey = await findUserTask(
      request,
      instance.processInstanceKey,
      'CREATED',
      undefined,
      extendedAssertionOptions,
    );

    await suspendAndExpectSuspended(request, instance.processInstanceKey);

    await assertInvalidState(await completeUserTask(request, userTaskKey));
    await assertInvalidState(
      await request.post(
        buildUrl('/user-tasks/{userTaskKey}/assignment', {userTaskKey}),
        {headers: jsonHeaders(), data: {assignee: 'demo'}},
      ),
    );
    await assertInvalidState(
      await request.delete(
        buildUrl('/user-tasks/{userTaskKey}/assignee', {userTaskKey}),
        {headers: jsonHeaders()},
      ),
    );
    await assertInvalidState(
      await request.patch(
        buildUrl('/user-tasks/{userTaskKey}', {userTaskKey}),
        {
          headers: jsonHeaders(),
          data: {changeset: {priority: 60}},
        },
      ),
    );

    await assertStatusCode(
      await resume(request, instance.processInstanceKey),
      204,
    );
    await assertStatusCode(await completeUserTask(request, userTaskKey), 204);
    await expectProcessState(
      request,
      instance.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('Modification is rejected while the instance is suspended', async ({
    request,
  }) => {
    const {jobType, processInstanceKey} =
      await startServiceTaskInstance('sr-modify');
    await suspendAndExpectSuspended(request, processInstanceKey);

    await assertInvalidState(
      await request.post(
        buildUrl('/process-instances/{processInstanceKey}/modification', {
          processInstanceKey,
        }),
        {
          headers: jsonHeaders(),
          data: {
            terminateInstructions: [{elementInstanceKey: processInstanceKey}],
          },
        },
      ),
    );

    await assertStatusCode(await resume(request, processInstanceKey), 204);
    const jobKey = await activateSingleJob(
      request,
      jobType,
      processInstanceKey,
    );
    await completeJob(request, jobKey);
    await expectProcessState(
      request,
      processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('Incident resolution is rejected while the instance is suspended', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('sr-incident');
    const jobType = uniquePrefixedId('sr-incident-job');
    await deployIncidentProcess(processDefinitionId, jobType);
    // Without these variables the model raises three further incidents, leaving
    // the target one ambiguous.
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1, {
      goUp: 1,
      clientId: 'sr-incident-client',
      orderId: 'sr-incident-order',
    });
    instancesToCancel.push(instance.processInstanceKey);

    const jobKey = await activateSingleJob(
      request,
      jobType,
      instance.processInstanceKey,
    );
    await failJob(request, String(jobKey), 0);
    const incidents = (await searchIncidentByPIK(request, {
      processInstanceKey: instance.processInstanceKey,
    })) as Record<string, unknown>[];
    const jobIncidents = incidents.filter(
      (i) => i['errorType'] === 'JOB_NO_RETRIES',
    );
    expect(jobIncidents).toHaveLength(1);
    const incidentKey = String(jobIncidents[0]['incidentKey']);

    await suspendAndExpectSuspended(request, instance.processInstanceKey);

    await assertInvalidState(
      await request.post(
        buildUrl('/incidents/{incidentKey}/resolution', {incidentKey}),
        {headers: jsonHeaders()},
      ),
    );

    await assertStatusCode(
      await resume(request, instance.processInstanceKey),
      204,
    );

    // A JOB_NO_RETRIES incident needs its job's retries back before it can be
    // resolved at all — the engine answers 409 and says so.
    await assertStatusCode(
      await request.patch(
        buildUrl('/jobs/{jobKey}', {jobKey: String(jobKey)}),
        {
          headers: jsonHeaders(),
          data: {changeset: {retries: 2}},
        },
      ),
      204,
    );
    await assertStatusCode(
      await request.post(
        buildUrl('/incidents/{incidentKey}/resolution', {incidentKey}),
        {headers: jsonHeaders()},
      ),
      204,
    );
  });

  test('Variable edits on a suspended instance follow the element scope', async ({
    request,
  }) => {
    const {jobType, processInstanceKey} =
      await startServiceTaskInstance('sr-vars');
    // Left unactivated so the element instance stays ACTIVE and addressable.
    const serviceTaskScope = await searchElementInstanceByElementIdAndState(
      request,
      processInstanceKey,
      'task',
      'ACTIVE',
    );
    await suspendAndExpectSuspended(request, processInstanceKey);

    for (const elementInstanceKey of [
      processInstanceKey,
      String(serviceTaskScope),
    ]) {
      await assertStatusCode(
        await request.put(
          buildUrl('/element-instances/{elementInstanceKey}/variables', {
            elementInstanceKey,
          }),
          {
            headers: jsonHeaders(),
            data: {variables: {[`edited_${elementInstanceKey}`]: 'yes'}},
          },
        ),
        204,
      );
    }

    await expect(async () => {
      const res = await request.post(buildUrl('/variables/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {processInstanceKey, name: `edited_${processInstanceKey}`},
        },
      });
      await assertStatusCode(res, 200);
      expect((await res.json()).items ?? []).toHaveLength(1);
    }).toPass(extendedAssertionOptions);

    await assertStatusCode(await resume(request, processInstanceKey), 204);
    const jobKey = await activateSingleJob(
      request,
      jobType,
      processInstanceKey,
    );
    await completeJob(request, jobKey);
    await expectProcessState(
      request,
      processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('A user task scope rejects variable edits while the instance is suspended', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('sr-vars-ut');
    await deployUserTaskProcess(processDefinitionId);
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1);
    instancesToCancel.push(instance.processInstanceKey);
    const userTaskKey = await findUserTask(
      request,
      instance.processInstanceKey,
      'CREATED',
      undefined,
      extendedAssertionOptions,
    );
    const userTaskScope = await searchElementInstanceByElementIdAndState(
      request,
      instance.processInstanceKey,
      'Activity_1xqonra',
      'ACTIVE',
    );

    await suspendAndExpectSuspended(request, instance.processInstanceKey);

    // This scope alone is refused: the write would trigger an UPDATING task
    // listener, which cannot be handed out while suspended (#60873).
    await assertInvalidState(
      await request.put(
        buildUrl('/element-instances/{elementInstanceKey}/variables', {
          elementInstanceKey: String(userTaskScope),
        }),
        {headers: jsonHeaders(), data: {variables: {blocked: 'yes'}}},
      ),
    );
    await assertStatusCode(
      await request.put(
        buildUrl('/element-instances/{elementInstanceKey}/variables', {
          elementInstanceKey: instance.processInstanceKey,
        }),
        {headers: jsonHeaders(), data: {variables: {allowed: 'yes'}}},
      ),
      204,
    );

    await assertStatusCode(
      await resume(request, instance.processInstanceKey),
      204,
    );
    await assertStatusCode(await completeUserTask(request, userTaskKey), 204);
    await expectProcessState(
      request,
      instance.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('Migration onto a suspended instance is rejected', async ({request}) => {
    const sourceId = uniquePrefixedId('sr-migrate-src');
    const sourceJobType = uniquePrefixedId('sr-migrate-src-job');
    await deployServiceTaskProcess(sourceId, sourceJobType);
    const targetId = uniquePrefixedId('sr-migrate-tgt');
    const targetJobType = uniquePrefixedId('sr-migrate-tgt-job');
    const target = await deployServiceTaskProcess(targetId, targetJobType);

    const migration = {
      targetProcessDefinitionKey: String(target.processDefinitionKey),
      mappingInstructions: [{sourceElementId: 'task', targetElementId: 'task'}],
    };

    // Proven valid against an active instance first: a malformed migration is
    // rejected with 400 before the suspension check runs.
    const control = await createInstanceOnceDeployed(sourceId, 1);
    instancesToCancel.push(control.processInstanceKey);
    await assertStatusCode(
      await request.post(
        buildUrl('/process-instances/{processInstanceKey}/migration', {
          processInstanceKey: control.processInstanceKey,
        }),
        {headers: jsonHeaders(), data: migration},
      ),
      204,
    );

    const subject = await createInstanceOnceDeployed(sourceId, 1);
    instancesToCancel.push(subject.processInstanceKey);
    await suspendAndExpectSuspended(request, subject.processInstanceKey);

    await assertInvalidState(
      await request.post(
        buildUrl('/process-instances/{processInstanceKey}/migration', {
          processInstanceKey: subject.processInstanceKey,
        }),
        {headers: jsonHeaders(), data: migration},
      ),
    );

    await assertStatusCode(
      await resume(request, subject.processInstanceKey),
      204,
    );
    const jobKey = await activateSingleJob(
      request,
      sourceJobType,
      subject.processInstanceKey,
    );
    await completeJob(request, jobKey);
    await expectProcessState(
      request,
      subject.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('Message correlation to a suspended instance is refused promptly', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('sr-msg');
    const messageName = uniquePrefixedId('sr-msg-name');
    const correlationKey = uniquePrefixedId('sr-msg-key');
    await deployMessageCatchProcess(processDefinitionId, messageName);
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1, {
      corrId: correlationKey,
    });
    instancesToCancel.push(instance.processInstanceKey);
    await searchElementInstanceByElementIdAndState(
      request,
      instance.processInstanceKey,
      'Event_1idbbd5',
      'ACTIVE',
    );

    // Control: the same message and key correlate on a running instance, so a
    // refusal below cannot be "there was no subscription to correlate to".
    const controlKey = uniquePrefixedId('sr-msg-key');
    const control = await createInstanceOnceDeployed(processDefinitionId, 1, {
      corrId: controlKey,
    });
    instancesToCancel.push(control.processInstanceKey);
    await searchElementInstanceByElementIdAndState(
      request,
      control.processInstanceKey,
      'Event_1idbbd5',
      'ACTIVE',
    );
    await assertStatusCode(
      await request.post(buildUrl('/messages/correlation'), {
        headers: jsonHeaders(),
        data: {name: messageName, correlationKey: controlKey, variables: {}},
      }),
      200,
    );

    await suspendAndExpectSuspended(request, instance.processInstanceKey);

    // #60648 was the shape of the refusal, not the refusal: it hung to the
    // gateway deadline and answered 504. Any prompt code is fine; 504 is not.
    const correlateRes = await request.post(buildUrl('/messages/correlation'), {
      headers: jsonHeaders(),
      data: {name: messageName, correlationKey, variables: {}},
    });
    expect([404, 409]).toContain(correlateRes.status());

    await expectProcessState(
      request,
      instance.processInstanceKey,
      'SUSPENDED',
      extendedAssertionOptions,
    );

    await assertStatusCode(
      await resume(request, instance.processInstanceKey),
      204,
    );
    await assertStatusCode(
      await request.post(buildUrl('/messages/publication'), {
        headers: jsonHeaders(),
        data: {
          name: messageName,
          correlationKey,
          timeToLive: 600000,
          variables: {},
        },
      }),
      200,
    );
    await expectProcessState(
      request,
      instance.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('Many jobs and several message subscriptions are all closed by one suspension', async ({
    request,
  }) => {
    const prefix = uniquePrefixedId('sr-jm');
    const {processDefinitionId, jobType, messageName, cancelMessageName} =
      await deployJobsAndMessagesProcess(prefix);
    const correlationKey = uniquePrefixedId('sr-jm-key');
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1, {
      corrId: correlationKey,
      items: Array.from({length: MI_JOB_COUNT}, (_, index) => index + 1),
    });
    instancesToCancel.push(instance.processInstanceKey);

    await expectJobsByType(
      request,
      instance.processInstanceKey,
      jobType,
      MI_JOB_COUNT,
      extendedAssertionOptions,
    );
    // The catch event and the boundary event on the multi-instance task.
    await expectOpenSubscriptions(request, instance.processInstanceKey, 2);

    await suspendAndExpectSuspended(request, instance.processInstanceKey);

    expect(
      await activateJobsByType(
        request,
        jobType,
        instance.processInstanceKey,
        [],
        MI_JOB_COUNT,
        1_000,
      ),
    ).toHaveLength(0);
    // Both subscriptions, on two different scopes, have to be closed — the
    // boundary event is the one that hangs off the task holding the jobs.
    for (const name of [messageName, cancelMessageName]) {
      expect([404, 409]).toContain(
        (await correlateMessage(request, name, correlationKey)).status(),
      );
    }
    // Closing subscriptions is engine-internal bookkeeping; it must not reach
    // secondary storage, where a consumer would read it as unsubscribed.
    await expectOpenSubscriptions(request, instance.processInstanceKey, 2);

    await assertStatusCode(
      await resume(request, instance.processInstanceKey),
      204,
    );

    const jobs = await activateJobsByType(
      request,
      jobType,
      instance.processInstanceKey,
      [],
      MI_JOB_COUNT,
    );
    expect(jobs).toHaveLength(MI_JOB_COUNT);
    for (const job of jobs) {
      await completeJob(request, job.jobKey);
    }
    await assertStatusCode(
      await correlateMessage(request, messageName, correlationKey),
      200,
    );

    await expectProcessState(
      request,
      instance.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectNoIncidents(request, instance.processInstanceKey);
  });

  test('Suspended instances are searchable by state and by suspension date', async ({
    request,
  }) => {
    const {jobType, processInstanceKey} =
      await startServiceTaskInstance('sr-search');
    await suspendAndExpectSuspended(request, processInstanceKey);

    await expect(async () => {
      const res = await request.post(buildUrl('/process-instances/search'), {
        headers: jsonHeaders(),
        data: {filter: {processInstanceKey, state: {$eq: 'SUSPENDED'}}},
      });
      await assertStatusCode(res, 200);
      expect((await res.json()).items ?? []).toHaveLength(1);
    }).toPass(extendedAssertionOptions);

    const activeRes = await request.post(
      buildUrl('/process-instances/search'),
      {
        headers: jsonHeaders(),
        data: {filter: {processInstanceKey, state: {$eq: 'ACTIVE'}}},
      },
    );
    await assertStatusCode(activeRes, 200);
    expect((await activeRes.json()).items ?? []).toHaveLength(0);

    const existsRes = await request.post(
      buildUrl('/process-instances/search'),
      {
        headers: jsonHeaders(),
        data: {filter: {processInstanceKey, suspendedDate: {$exists: true}}},
      },
    );
    await assertStatusCode(existsRes, 200);
    expect((await existsRes.json()).items ?? []).toHaveLength(1);

    const firstSuspendedDate = (
      await getProcessInstance(request, processInstanceKey)
    ).suspendedDate;

    await assertStatusCode(await resume(request, processInstanceKey), 204);
    await expectSuspendedDate(request, processInstanceKey, false);

    await suspendAndExpectSuspended(request, processInstanceKey);
    const secondSuspendedDate = (
      await getProcessInstance(request, processInstanceKey)
    ).suspendedDate;
    expect(new Date(String(secondSuspendedDate)).getTime()).toBeGreaterThan(
      new Date(String(firstSuspendedDate)).getTime(),
    );

    await assertStatusCode(await resume(request, processInstanceKey), 204);
    const jobKey = await activateSingleJob(
      request,
      jobType,
      processInstanceKey,
    );
    await completeJob(request, jobKey);
    await expectProcessState(
      request,
      processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('Suspended instances can be sorted by suspension date', async ({
    request,
  }) => {
    const first = await startServiceTaskInstance('sr-sort-a');
    await suspendAndExpectSuspended(request, first.processInstanceKey);
    const second = await startServiceTaskInstance('sr-sort-b');
    await suspendAndExpectSuspended(request, second.processInstanceKey);

    await expect(async () => {
      const res = await request.post(buildUrl('/process-instances/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: {
              $in: [first.processInstanceKey, second.processInstanceKey],
            },
          },
          sort: [{field: 'suspendedDate', order: 'DESC'}],
        },
      });
      await assertStatusCode(res, 200);
      const items = (await res.json()).items ?? [];
      expect(items).toHaveLength(2);
      expect(items[0].processInstanceKey).toBe(second.processInstanceKey);
    }).toPass(extendedAssertionOptions);

    for (const instance of [first, second]) {
      await assertStatusCode(
        await resume(request, instance.processInstanceKey),
        204,
      );
      const jobKey = await activateSingleJob(
        request,
        instance.jobType,
        instance.processInstanceKey,
      );
      await completeJob(request, jobKey);
      await expectProcessState(
        request,
        instance.processInstanceKey,
        'COMPLETED',
        extendedAssertionOptions,
      );
    }
  });
});
