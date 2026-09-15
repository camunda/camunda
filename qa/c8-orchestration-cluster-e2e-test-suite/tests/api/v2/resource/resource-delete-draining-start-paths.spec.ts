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
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {
  createInstanceOnceDeployed,
  deployUserTaskProcess,
  drainProcessDefinition,
  expectProcessInstanceCount,
  findUserTask,
  searchProcessInstances,
} from '@requestHelpers';
import {uniquePrefixedId} from '../../../../utils/constants';

const USER_TASK_MODEL_TASK_ID = 'Activity_1xqonra';

/**
 * Process id and message name are per test, so a publish can never open a
 * subscription belonging to another test running in parallel.
 */
async function deployMessageStartProcess(
  processDefinitionId: string,
  messageName: string,
  nameSuffix = '',
) {
  const deployment = await deployWithSubstitutions(
    './resources/message_start_business_id_process.bpmn',
    {
      message_start_business_id_process: processDefinitionId,
      start_business_id_msg: messageName,
      ...(nameSuffix
        ? {
            'name="Message Start Business ID Process"': `name="Message Start Business ID Process${nameSuffix}"`,
          }
        : {}),
    },
  );
  return deployment.processes[0];
}

async function publishMessage(
  request: APIRequestContext,
  name: string,
  correlationKey = '',
) {
  return request.post(buildUrl('/messages/publication'), {
    headers: jsonHeaders(),
    // A fresh messageId keeps deduplication from swallowing a second publish of
    // the same name.
    data: {name, correlationKey, messageId: randomUUID()},
  });
}

test.describe('Process Definition Draining Deletion — alternative start paths', () => {
  let instancesToCancel: string[] = [];

  test.beforeEach(() => {
    instancesToCancel = [];
  });

  // A definition left draining never finalizes, so every instance started here
  // has to be terminated.
  test.afterEach(async () => {
    for (const processInstanceKey of instancesToCancel.filter(Boolean)) {
      await cancelProcessInstance(processInstanceKey);
    }
  });

  test('Publishing the start message does not start an instance while the only version is draining', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-msgstart');
    const messageName = uniquePrefixedId('draining-msg');
    const {processDefinitionKey} = await deployMessageStartProcess(
      processDefinitionId,
      messageName,
    );

    // The control definition is the synchronisation point: once its instance
    // exists the broker has acted on both publishes, so an unchanged count on the
    // draining definition means refused rather than merely slow.
    const controlProcessDefinitionId = uniquePrefixedId(
      'draining-msgstart-control',
    );
    const controlMessageName = uniquePrefixedId('draining-msg-control');
    await deployMessageStartProcess(
      controlProcessDefinitionId,
      controlMessageName,
    );

    await assertStatusCode(await publishMessage(request, messageName), 200);
    await expectProcessInstanceCount(request, {processDefinitionId}, 1);
    const [firstInstance] = await searchProcessInstances(request, {
      processDefinitionId,
    });
    instancesToCancel.push(firstInstance!.processInstanceKey);
    await findUserTask(request, firstInstance!.processInstanceKey, 'CREATED');

    await drainProcessDefinition(request, processDefinitionKey);

    await assertStatusCode(await publishMessage(request, messageName), 200);
    await assertStatusCode(
      await publishMessage(request, controlMessageName),
      200,
    );

    await expectProcessInstanceCount(
      request,
      {processDefinitionId: controlProcessDefinitionId},
      1,
    );
    const [controlInstance] = await searchProcessInstances(request, {
      processDefinitionId: controlProcessDefinitionId,
    });
    instancesToCancel.push(controlInstance!.processInstanceKey);

    // The publish is accepted — nothing rejects a message for a definition with
    // no subscription — but it must not produce a second instance.
    expect(
      await searchProcessInstances(request, {processDefinitionId}),
    ).toHaveLength(1);
  });

  test('Publishing the start message starts an older ACTIVE version instead of the draining latest', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-msgstart-older');
    const messageName = uniquePrefixedId('draining-msg-older');
    const v1 = await deployMessageStartProcess(
      processDefinitionId,
      messageName,
    );
    const v2 = await deployMessageStartProcess(
      processDefinitionId,
      messageName,
      '-v2',
    );
    expect(v2.processDefinitionVersion).toBeGreaterThan(
      v1.processDefinitionVersion,
    );

    await assertStatusCode(await publishMessage(request, messageName), 200);
    await expectProcessInstanceCount(request, {processDefinitionId}, 1);
    const [v2Instance] = await searchProcessInstances(request, {
      processDefinitionId,
    });
    instancesToCancel.push(v2Instance!.processInstanceKey);
    expect(v2Instance!.processDefinitionKey).toBe(v2.processDefinitionKey);
    await findUserTask(request, v2Instance!.processInstanceKey, 'CREATED');

    await drainProcessDefinition(request, v2.processDefinitionKey);

    await assertStatusCode(await publishMessage(request, messageName), 200);

    // The start subscription falls back to the newest ACTIVE version, the same
    // resolution the create-by-id path performs.
    await expectProcessInstanceCount(request, {processDefinitionId}, 2);
    const instances = await searchProcessInstances(request, {
      processDefinitionId,
    });
    const started = instances.find(
      (item) => item.processInstanceKey !== v2Instance!.processInstanceKey,
    );
    instancesToCancel.push(started!.processInstanceKey);
    expect(started!.processDefinitionKey).toBe(v1.processDefinitionKey);
  });

  test('Migrating an instance onto a draining definition is rejected', async ({
    request,
  }) => {
    const sourceProcessDefinitionId = uniquePrefixedId(
      'draining-migrate-source',
    );
    const targetProcessDefinitionId = uniquePrefixedId(
      'draining-migrate-target',
    );
    await deployUserTaskProcess(sourceProcessDefinitionId);
    const target = await deployUserTaskProcess(targetProcessDefinitionId);

    const sourceInstance = await createInstanceOnceDeployed(
      sourceProcessDefinitionId,
      1,
    );
    instancesToCancel.push(sourceInstance.processInstanceKey);
    await findUserTask(request, sourceInstance.processInstanceKey, 'CREATED');

    // Without a running instance the target's deletion finalizes immediately and
    // the migration would be refused for a missing definition, not a draining one.
    const targetInstance = await createInstanceOnceDeployed(
      targetProcessDefinitionId,
      1,
    );
    instancesToCancel.push(targetInstance.processInstanceKey);
    await findUserTask(request, targetInstance.processInstanceKey, 'CREATED');

    await drainProcessDefinition(request, target.processDefinitionKey);

    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: sourceInstance.processInstanceKey,
      }),
      {
        headers: jsonHeaders(),
        data: {
          mappingInstructions: [
            {
              sourceElementId: USER_TASK_MODEL_TASK_ID,
              targetElementId: USER_TASK_MODEL_TASK_ID,
            },
          ],
          targetProcessDefinitionKey: target.processDefinitionKey,
        },
      },
    );

    // As with instance creation, the code depends on whether the serving
    // partition has already finalized its part of the deletion.
    expect([404, 409]).toContain(res.status());

    const [afterMigration] = await searchProcessInstances(request, {
      processDefinitionId: sourceProcessDefinitionId,
    });
    expect(afterMigration!.processDefinitionKey).not.toBe(
      target.processDefinitionKey,
    );
  });
});
