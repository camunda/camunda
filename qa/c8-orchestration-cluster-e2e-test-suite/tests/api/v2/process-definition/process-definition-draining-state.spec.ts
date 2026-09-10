/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test, type APIRequestContext} from '@playwright/test';
import {cancelProcessInstance} from '../../../../utils/zeebeClient';
import {
  createInstanceOnceDeployed,
  deployUserTaskProcess,
  drainProcessDefinition,
  expectProcessDefinitionDeleted,
  findUserTask,
  searchProcessDefinitionItems,
  searchProcessDefinitions,
} from '@requestHelpers';
import {
  defaultAssertionOptions,
  uniquePrefixedId,
  extendedAssertionOptions,
} from '../../../../utils/constants';

async function expectDefinitionKeysForState(
  request: APIRequestContext,
  processDefinitionId: string,
  state: string,
  expectedKeys: string[],
  assertionOptions = defaultAssertionOptions,
) {
  await expect(async () => {
    const items = await searchProcessDefinitionItems(request, {
      filter: {processDefinitionId, state},
    });
    expect(items.map((item) => item.processDefinitionKey).sort()).toEqual(
      [...expectedKeys].sort(),
    );
  }).toPass(assertionOptions);
}

/* eslint-disable playwright/expect-expect */
test.describe('Process Definition Draining State', () => {
  let instancesToCancel: string[] = [];

  test.beforeEach(() => {
    instancesToCancel = [];
  });

  // A failed assertion mid-drain would leave a definition DRAINING for the rest
  // of the run, blocking every later test that resolves it.
  test.afterEach(async () => {
    for (const processInstanceKey of instancesToCancel.filter(Boolean)) {
      await cancelProcessInstance(processInstanceKey, {ignoreNotFound: true});
    }
  });

  test('The state filter moves the definition from ACTIVE to DRAINING to DELETED', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-state');
    const {processDefinitionKey} =
      await deployUserTaskProcess(processDefinitionId);
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1);
    instancesToCancel.push(instance.processInstanceKey);

    await expectDefinitionKeysForState(request, processDefinitionId, 'ACTIVE', [
      processDefinitionKey,
    ]);

    await findUserTask(request, instance.processInstanceKey, 'CREATED');
    await drainProcessDefinition(request, processDefinitionKey);

    await expectDefinitionKeysForState(
      request,
      processDefinitionId,
      'DRAINING',
      [processDefinitionKey],
    );
    await expectDefinitionKeysForState(
      request,
      processDefinitionId,
      'ACTIVE',
      [],
    );

    await cancelProcessInstance(instance.processInstanceKey);

    await expectDefinitionKeysForState(
      request,
      processDefinitionId,
      'DELETED',
      [processDefinitionKey],
      extendedAssertionOptions,
    );
  });

  test('The state filter rejects a value outside the enum', async ({
    request,
  }) => {
    const res = await searchProcessDefinitions(request, {
      filter: {state: 'BOGUS'},
    });

    // A silently ignored filter would return every definition instead.
    expect(res.status()).toBe(400);
    expect((await res.json()).detail).toBe(
      "Unexpected value 'BOGUS' for enum field 'state'. " +
        'Use any of the following values: [ACTIVE, DRAINING, DELETED]',
    );
  });

  test('isLatestVersion keeps returning the draining version rather than an older ACTIVE one', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-latest');
    const v1 = await deployUserTaskProcess(processDefinitionId);
    const v2 = await deployUserTaskProcess(processDefinitionId, '-v2');
    const instance = await createInstanceOnceDeployed(
      processDefinitionId,
      v2.processDefinitionVersion,
    );
    instancesToCancel.push(instance.processInstanceKey);

    await findUserTask(request, instance.processInstanceKey, 'CREATED');
    await drainProcessDefinition(request, v2.processDefinitionKey);

    await expect(async () => {
      const items = await searchProcessDefinitionItems(request, {
        filter: {processDefinitionId, isLatestVersion: true},
      });
      expect(items).toHaveLength(1);
      expect(items[0]!.processDefinitionKey).toBe(v2.processDefinitionKey);
      expect(items[0]!.processDefinitionKey).not.toBe(v1.processDefinitionKey);
      expect(items[0]!.state).toBe('DRAINING');
    }).toPass(defaultAssertionOptions);

    await cancelProcessInstance(instance.processInstanceKey);
    await expectProcessDefinitionDeleted(request, v2.processDefinitionKey);
  });
});
