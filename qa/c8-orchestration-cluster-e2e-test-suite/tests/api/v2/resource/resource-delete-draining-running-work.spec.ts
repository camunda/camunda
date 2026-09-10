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
  deployWithSubstitutions,
  setVariables,
} from '../../../../utils/zeebeClient';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {
  activateSingleJob,
  completeJob,
  completeUserTask,
  createInstanceOnceDeployed,
  deployUserTaskProcess,
  drainProcessDefinition,
  expectProcessDefinitionDeleted,
  expectProcessDefinitionState,
  expectProcessState,
  findUserTask,
  searchIncidentByPIK,
  searchVariableByNameAndProcessInstanceKey,
} from '@requestHelpers';
import {
  uniquePrefixedId,
  extendedAssertionOptions,
} from '../../../../utils/constants';

/**
 * Job type is scoped to the calling test so no other test's worker can steal
 * the job and end the drain before it is asserted.
 */
async function deployJobProcess(processDefinitionId: string, jobType: string) {
  const deployment = await deployWithSubstitutions(
    './resources/incidentGeneratorProcess.bpmn',
    {
      incidentGeneratorProcess: processDefinitionId,
      'type="incidentGenerator"': `type="${jobType}"`,
    },
  );
  return deployment.processes[0];
}

/**
 * Message name and correlation key are per test, so a publish only ever
 * reaches this test's instance.
 */
async function deployMessageCatchProcess(
  processDefinitionId: string,
  messageName: string,
) {
  const deployment = await deployWithSubstitutions(
    './resources/messageCatchEvent1.bpmn',
    {
      messageCatchEvent1: processDefinitionId,
      'name="Message_143t419"': `name="${messageName}"`,
      'correlationKey="=143419"': 'correlationKey="=corrId"',
    },
  );
  return deployment.processes[0];
}

/**
 * The wait duration comes from a variable, so the caller can pick a window
 * long enough to observe DRAINING before the timer fires.
 */
async function deployTimerProcess(processDefinitionId: string) {
  const deployment = await deployWithSubstitutions(
    './resources/timerIntermediateCatchEvent.bpmn',
    {
      timerIntermediateCatchEventProcess: processDefinitionId,
    },
  );
  return deployment.processes[0];
}

/* eslint-disable playwright/expect-expect */
test.describe('Process Definition Draining Deletion — work already in flight', () => {
  let instancesToCancel: string[] = [];

  test.beforeEach(() => {
    instancesToCancel = [];
  });

  test.afterEach(async () => {
    for (const processInstanceKey of instancesToCancel.filter(Boolean)) {
      await cancelProcessInstance(processInstanceKey);
    }
  });

  test('A job of a draining definition can still be failed, resolved and completed', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-job');
    const jobType = uniquePrefixedId('draining-jobtype');
    const {processDefinitionKey} = await deployJobProcess(
      processDefinitionId,
      jobType,
    );
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1);
    instancesToCancel.push(instance.processInstanceKey);

    const jobKey = await activateSingleJob(
      request,
      jobType,
      instance.processInstanceKey,
    );

    await drainProcessDefinition(request, processDefinitionKey);

    // Draining stops new work from starting; it must not stop a worker reporting
    // on work already in flight, including failing it into an incident.
    await assertStatusCode(
      await request.post(buildUrl('/jobs/{jobKey}/failure', {jobKey}), {
        headers: jsonHeaders(),
        data: {retries: 0, errorMessage: 'draining-coverage failure'},
      }),
      204,
    );

    const incidents = await searchIncidentByPIK(request, {
      processInstanceKey: instance.processInstanceKey,
    });
    expect(incidents).toHaveLength(1);
    const incidentKey = incidents[0]!.incidentKey;

    // Without this a failing instance could never reach an end state and the
    // drain would be stuck for good.
    await assertStatusCode(
      await request.patch(buildUrl('/jobs/{jobKey}', {jobKey}), {
        headers: jsonHeaders(),
        data: {changeset: {retries: 2}},
      }),
      204,
    );
    await assertStatusCode(
      await request.post(
        buildUrl('/incidents/{incidentKey}/resolution', {
          incidentKey,
        }),
        {headers: jsonHeaders()},
      ),
      204,
    );

    const retriedJobKey = await activateSingleJob(
      request,
      jobType,
      instance.processInstanceKey,
    );
    await completeJob(request, retriedJobKey);

    await expectProcessDefinitionDeleted(request, processDefinitionKey);
  });

  test('A message can still be correlated to a running instance of a draining definition', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-correlate');
    const messageName = uniquePrefixedId('draining-catchmsg');
    const correlationKey = uniquePrefixedId('draining-corr');
    const {processDefinitionKey} = await deployMessageCatchProcess(
      processDefinitionId,
      messageName,
    );

    const instance = await createInstanceOnceDeployed(processDefinitionId, 1, {
      corrId: correlationKey,
    });
    instancesToCancel.push(instance.processInstanceKey);
    await expectProcessState(request, instance.processInstanceKey, 'ACTIVE');

    await drainProcessDefinition(request, processDefinitionKey);

    // The waiting instance has to receive the message — the drain's only route
    // to completion here.
    await assertStatusCode(
      await request.post(buildUrl('/messages/publication'), {
        headers: jsonHeaders(),
        data: {name: messageName, correlationKey, messageId: randomUUID()},
      }),
      200,
    );

    await expectProcessState(request, instance.processInstanceKey, 'COMPLETED');
    await expectProcessDefinitionDeleted(request, processDefinitionKey);
  });

  test('Variables can still be set on an instance of a draining definition', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-variables');
    const jobType = uniquePrefixedId('draining-vartype');
    const {processDefinitionKey} = await deployJobProcess(
      processDefinitionId,
      jobType,
    );
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1);
    instancesToCancel.push(instance.processInstanceKey);
    await activateSingleJob(request, jobType, instance.processInstanceKey);

    await drainProcessDefinition(request, processDefinitionKey);

    const variableName = 'drainingCoverageVariable';
    await setVariables(instance.processInstanceKey, {
      [variableName]: 'set-while-draining',
    });

    const variable = await searchVariableByNameAndProcessInstanceKey(request, {
      processInstanceKey: instance.processInstanceKey,
      name: variableName,
    });
    expect(variable.value).toBe('"set-while-draining"');
  });

  test('A firing timer completes the last instance and finalizes the drain', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-timer');
    const {processDefinitionKey} =
      await deployTimerProcess(processDefinitionId);

    const instance = await createInstanceOnceDeployed(processDefinitionId, 1, {
      duration: 'PT30S',
    });
    instancesToCancel.push(instance.processInstanceKey);
    await expectProcessState(request, instance.processInstanceKey, 'ACTIVE');

    await drainProcessDefinition(request, processDefinitionKey);

    // No cancel, no worker, no message: the timer alone has to carry the instance
    // to its end event while the definition drains.
    await expectProcessState(
      request,
      instance.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );

    await expectProcessDefinitionDeleted(request, processDefinitionKey);
  });

  test('A suspended instance holds the drain open until it is resumed and completed', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-suspend');
    const {processDefinitionKey} =
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

    await drainProcessDefinition(request, processDefinitionKey);

    // Suspension acts on the instance, not the definition, so a drain in progress
    // must not gate it.
    await assertStatusCode(
      await request.post(
        buildUrl('/process-instances/{processInstanceKey}/suspension', {
          processInstanceKey: instance.processInstanceKey,
        }),
        {headers: jsonHeaders()},
      ),
      204,
    );

    // A suspended instance never ends on its own, so the drain must not finalize
    // as if the instance were gone.
    await expectProcessDefinitionState(
      request,
      processDefinitionKey,
      'DRAINING',
    );

    await assertStatusCode(
      await request.post(
        buildUrl('/process-instances/{processInstanceKey}/resumption', {
          processInstanceKey: instance.processInstanceKey,
        }),
        {headers: jsonHeaders()},
      ),
      204,
    );

    await assertStatusCode(await completeUserTask(request, userTaskKey), 204);
    await expectProcessDefinitionDeleted(request, processDefinitionKey);
  });
});
