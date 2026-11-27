/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {cancelProcessInstance} from '../../../../utils/zeebeClient';
import {assertNotFoundRequest, assertStatusCode} from '../../../../utils/http';
import {
  completeServiceTaskInstance,
  createInstanceOnceDeployed,
  createInstancesUntilEveryPartition,
  createInstancesUntilOn,
  createProcessInstanceRequest,
  deleteProcessDefinition,
  deployServiceTaskProcess,
  deployUserTaskProcess,
  deployUserTaskProcessVersions,
  drainProcessDefinition,
  expectCreationRejectedAsSuspended,
  expectCreationRejectedWhileProbesReachEveryPartition,
  expectProcessDefinitionDeleted,
  expectProcessDefinitionKeys,
  expectProcessDefinitionState,
  expectProcessDefinitionStateByKey,
  expectProcessInstanceCount,
  expectProcessState,
  findUserTask,
  partitionOf,
  partitionsCount,
  resumeAndListOutcomes,
  resumeDefinitionAndWait,
  resumeProcessDefinitions,
  searchProcessDefinitionItems,
  suspendAndListOutcomes,
  suspendDefinitionAndWait,
  suspendProcessDefinitions,
} from '@requestHelpers';
import {
  extendedAssertionOptions,
  uniquePrefixedId,
} from '../../../../utils/constants';

const USER_TASK_ELEMENT_ID = 'Activity_1xqonra';
const UNKNOWN_KEY = '2251799813999999';

test.describe('Process Definition Suspension API', () => {
  let instancesToCancel: string[] = [];

  test.beforeEach(() => {
    instancesToCancel = [];
  });

  test.afterEach(async () => {
    for (const processInstanceKey of instancesToCancel.filter(Boolean)) {
      await cancelProcessInstance(processInstanceKey);
    }
  });

  test('Suspends and resumes a single version by key', async ({request}) => {
    const processDefinitionId = uniquePrefixedId('pd-suspend-key');
    const [v1] = await deployUserTaskProcessVersions(processDefinitionId, 1);

    const suspended = await suspendAndListOutcomes(request, {
      processDefinitionKey: v1!.processDefinitionKey,
    });
    const resumed = await resumeAndListOutcomes(request, {
      processDefinitionKey: v1!.processDefinitionKey,
    });

    expect(suspended).toEqual([[1, 'SUSPENDED']]);
    expect(resumed).toEqual([[1, 'RESUMED']]);
  });

  test('Suspends one version by id and version and leaves the other versions active', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('pd-suspend-id-version');
    const [v1] = await deployUserTaskProcessVersions(processDefinitionId, 2);

    const outcomes = await suspendAndListOutcomes(request, {
      processDefinitionId,
      processDefinitionVersion: 2,
    });

    expect(outcomes).toEqual([[2, 'SUSPENDED']]);
    const res = await createProcessInstanceRequest(request, {
      processDefinitionKey: v1!.processDefinitionKey,
    });
    await assertStatusCode(res, 200);
    instancesToCancel.push((await res.json()).processInstanceKey);
  });

  test('Suspends and resumes every version by id and reports an outcome per version', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('pd-suspend-all');
    const [v1] = await deployUserTaskProcessVersions(processDefinitionId, 3);
    await suspendAndListOutcomes(request, {
      processDefinitionKey: v1!.processDefinitionKey,
    });

    const suspended = await suspendAndListOutcomes(request, {
      processDefinitionId,
    });
    const resumedV2 = await resumeAndListOutcomes(request, {
      processDefinitionId,
      processDefinitionVersion: 2,
    });
    const resumedAll = await resumeAndListOutcomes(request, {
      processDefinitionId,
    });

    expect(suspended).toEqual([
      [1, 'ALREADY_SUSPENDED'],
      [2, 'SUSPENDED'],
      [3, 'SUSPENDED'],
    ]);
    expect(resumedV2).toEqual([[2, 'RESUMED']]);
    expect(resumedAll).toEqual([
      [1, 'RESUMED'],
      [2, 'ALREADY_RESUMED'],
      [3, 'RESUMED'],
    ]);
  });

  test('Repeated suspend and resume requests report ALREADY_SUSPENDED and ALREADY_RESUMED', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('pd-suspend-repeat');
    const [v1] = await deployUserTaskProcessVersions(processDefinitionId, 1);
    const selector = {processDefinitionKey: v1!.processDefinitionKey};

    const outcomes = [
      await resumeAndListOutcomes(request, selector),
      await suspendAndListOutcomes(request, selector),
      await suspendAndListOutcomes(request, selector),
      await resumeAndListOutcomes(request, selector),
      await resumeAndListOutcomes(request, selector),
    ];

    expect(outcomes.map((o) => o[0]![1])).toEqual([
      'ALREADY_RESUMED',
      'SUSPENDED',
      'ALREADY_SUSPENDED',
      'RESUMED',
      'ALREADY_RESUMED',
    ]);
  });

  // eslint-disable-next-line playwright/expect-expect -- asserted through the request helpers
  test('A version deployed after a suspension by id starts active', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('pd-suspend-new-version');
    await deployUserTaskProcessVersions(processDefinitionId, 2);
    await suspendAndListOutcomes(request, {processDefinitionId});

    const v3 = await deployUserTaskProcess(processDefinitionId, '-v3');

    await expectProcessDefinitionState(
      request,
      v3.processDefinitionKey,
      'ACTIVE',
    );
    const res = await createProcessInstanceRequest(request, {
      processDefinitionKey: v3.processDefinitionKey,
    });
    await assertStatusCode(res, 200);
    instancesToCancel.push((await res.json()).processInstanceKey);
  });

  // eslint-disable-next-line playwright/expect-expect -- asserted through the request helpers
  test('Unknown keys, ids and versions are rejected with 404', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('pd-suspend-unknown');
    await deployUserTaskProcessVersions(processDefinitionId, 1);

    const unknownKey = await suspendProcessDefinitions(request, {
      processDefinitionKey: UNKNOWN_KEY,
    });
    const unknownKeyResume = await resumeProcessDefinitions(request, {
      processDefinitionKey: UNKNOWN_KEY,
    });
    const unknownId = await suspendProcessDefinitions(request, {
      processDefinitionId: uniquePrefixedId('pd-suspend-missing'),
    });
    const unknownVersion = await suspendProcessDefinitions(request, {
      processDefinitionId,
      processDefinitionVersion: 9,
    });

    await assertNotFoundRequest(unknownKey, `with key '${UNKNOWN_KEY}'`);
    await assertNotFoundRequest(unknownKeyResume, `with key '${UNKNOWN_KEY}'`);
    await assertNotFoundRequest(unknownId, 'no such process definition');
    await assertNotFoundRequest(unknownVersion, "and version '9'");
  });

  test.describe('Instance creation', () => {
    // eslint-disable-next-line playwright/expect-expect -- asserted through the request helpers
    test('Creation of a suspended version is rejected by key, by id and version, with awaitCompletion and with start instructions', async ({
      request,
    }) => {
      const processDefinitionId = uniquePrefixedId('pd-suspend-create');
      const [v1] = await deployUserTaskProcessVersions(processDefinitionId, 1);
      const processDefinitionKey = v1!.processDefinitionKey;
      await suspendDefinitionAndWait(request, processDefinitionKey);

      const attempts = [
        {processDefinitionKey},
        {processDefinitionId, processDefinitionVersion: 1},
        {processDefinitionKey, awaitCompletion: true, requestTimeout: 20_000},
        {
          processDefinitionKey,
          startInstructions: [{elementId: USER_TASK_ELEMENT_ID}],
        },
      ];

      for (const data of attempts) {
        await expectCreationRejectedAsSuspended(
          await createProcessInstanceRequest(request, data),
          processDefinitionId,
          1,
        );
      }
      await expectProcessInstanceCount(request, {processDefinitionKey}, 0);
    });

    test('Creation by id only starts the latest active version and is rejected once every version is suspended', async ({
      request,
    }) => {
      const processDefinitionId = uniquePrefixedId('pd-suspend-latest-active');
      const [v1, v2] = await deployUserTaskProcessVersions(
        processDefinitionId,
        2,
      );
      await suspendDefinitionAndWait(request, v2!.processDefinitionKey);

      const onLatestActive = await createProcessInstanceRequest(request, {
        processDefinitionId,
      });
      await assertStatusCode(onLatestActive, 200);
      const instance = await onLatestActive.json();
      instancesToCancel.push(instance.processInstanceKey);
      expect(instance.processDefinitionKey).toBe(v1!.processDefinitionKey);
      expect(instance.processDefinitionVersion).toBe(1);

      await suspendDefinitionAndWait(request, v1!.processDefinitionKey);
      const allSuspended = await createProcessInstanceRequest(request, {
        processDefinitionId,
      });

      // The message wording is due to change (#70544, question 3), so only the id is asserted.
      await assertStatusCode(allSuspended, 404);
      expect((await allSuspended.json()).detail).toContain(
        `'${processDefinitionId}'`,
      );
    });

    test('Creation works again after resume and rejected creations are not replayed', async ({
      request,
    }) => {
      const processDefinitionId = uniquePrefixedId('pd-suspend-resume-create');
      const [v1, v2] = await deployUserTaskProcessVersions(
        processDefinitionId,
        2,
      );
      await suspendAndListOutcomes(request, {processDefinitionId});
      await expectProcessDefinitionState(
        request,
        v2!.processDefinitionKey,
        'SUSPENDED',
      );
      for (const definition of [v1!, v2!]) {
        await expectCreationRejectedAsSuspended(
          await createProcessInstanceRequest(request, {
            processDefinitionKey: definition.processDefinitionKey,
          }),
          processDefinitionId,
          definition.processDefinitionVersion,
        );
      }

      await resumeAndListOutcomes(request, {processDefinitionId});
      await expectProcessDefinitionState(
        request,
        v2!.processDefinitionKey,
        'ACTIVE',
      );
      const byKey = await createProcessInstanceRequest(request, {
        processDefinitionKey: v1!.processDefinitionKey,
      });
      await assertStatusCode(byKey, 200);
      instancesToCancel.push((await byKey.json()).processInstanceKey);

      const byIdOnly = await createProcessInstanceRequest(request, {
        processDefinitionId,
      });
      await assertStatusCode(byIdOnly, 200);
      const createdById = await byIdOnly.json();
      instancesToCancel.push(createdById.processInstanceKey);
      expect(createdById.processDefinitionVersion).toBe(2);
      await expectProcessInstanceCount(request, {processDefinitionId}, 2);
    });

    test('A running instance of a suspended version stays active and runs to completion', async ({
      request,
    }) => {
      const processDefinitionId = uniquePrefixedId('pd-suspend-running');
      const jobType = uniquePrefixedId('pd-suspend-running-job');
      const {processDefinitionKey} = await deployServiceTaskProcess(
        processDefinitionId,
        jobType,
      );
      const instance = await createInstanceOnceDeployed(processDefinitionId, 1);
      instancesToCancel.push(instance.processInstanceKey);

      await suspendDefinitionAndWait(request, processDefinitionKey);

      await expectProcessState(request, instance.processInstanceKey, 'ACTIVE');
      await completeServiceTaskInstance(
        request,
        jobType,
        instance.processInstanceKey,
      );
    });
  });

  test.describe('Search', () => {
    // eslint-disable-next-line playwright/expect-expect -- asserted through the request helpers
    test('Get and search report SUSPENDED for the suspended version only, and ACTIVE again after resume', async ({
      request,
    }) => {
      const processDefinitionId = uniquePrefixedId('pd-suspend-search');
      const [v1, v2, v3] = await deployUserTaskProcessVersions(
        processDefinitionId,
        3,
      );
      const keys = [v1!, v2!, v3!].map((v) => v.processDefinitionKey);

      await suspendDefinitionAndWait(request, keys[1]!);

      await expectProcessDefinitionStateByKey(request, keys[1]!, 'SUSPENDED');
      await expectProcessDefinitionStateByKey(request, keys[0]!, 'ACTIVE');
      await expectProcessDefinitionKeys(
        request,
        {processDefinitionId, state: 'SUSPENDED'},
        [keys[1]!],
      );
      await expectProcessDefinitionKeys(
        request,
        {processDefinitionId, state: 'ACTIVE'},
        [keys[0]!, keys[2]!],
      );

      await resumeDefinitionAndWait(request, keys[1]!);

      await expectProcessDefinitionStateByKey(request, keys[1]!, 'ACTIVE');
      await expectProcessDefinitionKeys(
        request,
        {processDefinitionId, state: 'SUSPENDED'},
        [],
      );
    });

    test('isLatestVersion with state ACTIVE returns the latest version that is not suspended', async ({
      request,
    }) => {
      const processDefinitionId = uniquePrefixedId('pd-suspend-latest');
      const [v1, v2] = await deployUserTaskProcessVersions(
        processDefinitionId,
        2,
      );

      await suspendDefinitionAndWait(request, v2!.processDefinitionKey);

      await expectProcessDefinitionKeys(
        request,
        {processDefinitionId, isLatestVersion: true, state: 'ACTIVE'},
        [v1!.processDefinitionKey],
      );
      const latest = await searchProcessDefinitionItems(request, {
        filter: {processDefinitionId, isLatestVersion: true},
      });
      expect(latest.map((i) => [i.version, i.state])).toEqual([
        [2, 'SUSPENDED'],
      ]);
    });
  });

  test.describe('Deployment and deletion', () => {
    test('Deploying while the latest version is suspended creates a new active version, also for an identical resource', async ({
      request,
    }) => {
      const processDefinitionId = uniquePrefixedId('pd-suspend-redeploy');
      const [v1] = await deployUserTaskProcessVersions(processDefinitionId, 1);
      const duplicate = await deployUserTaskProcess(processDefinitionId);
      expect(duplicate.processDefinitionKey).toBe(v1!.processDefinitionKey);
      await suspendDefinitionAndWait(request, v1!.processDefinitionKey);

      const identical = await deployUserTaskProcess(processDefinitionId);

      expect(identical.processDefinitionVersion).toBe(2);
      await expectProcessDefinitionState(
        request,
        identical.processDefinitionKey,
        'ACTIVE',
      );
      const res = await createProcessInstanceRequest(request, {
        processDefinitionId,
      });
      await assertStatusCode(res, 200);
      const instance = await res.json();
      instancesToCancel.push(instance.processInstanceKey);
      expect(instance.processDefinitionVersion).toBe(2);
    });

    test('Deleting a suspended version with a running instance moves it to DRAINING and it can no longer be suspended or resumed', async ({
      request,
    }) => {
      const processDefinitionId = uniquePrefixedId('pd-suspend-drain');
      const [v1] = await deployUserTaskProcessVersions(processDefinitionId, 1);
      const processDefinitionKey = v1!.processDefinitionKey;
      // Suspend and resume resolve the version on partition 1; only an instance
      // there keeps the version DRAINING for them instead of NOT_FOUND.
      const instances = await createInstancesUntilOn(
        processDefinitionId,
        1,
        20,
      );
      instancesToCancel.push(...instances.map((i) => i.processInstanceKey));
      const onDeploymentPartition = instances[instances.length - 1]!;
      expect(partitionOf(onDeploymentPartition.processInstanceKey)).toBe(1);
      await findUserTask(
        request,
        onDeploymentPartition.processInstanceKey,
        'CREATED',
      );
      await suspendDefinitionAndWait(request, processDefinitionKey);

      await drainProcessDefinition(request, processDefinitionKey);
      const resumeByKey = await resumeProcessDefinitions(request, {
        processDefinitionKey,
      });
      const suspendByKey = await suspendProcessDefinitions(request, {
        processDefinitionKey,
      });
      const suspendByVersion = await suspendProcessDefinitions(request, {
        processDefinitionId,
        processDefinitionVersion: 1,
      });

      for (const res of [resumeByKey, suspendByKey, suspendByVersion]) {
        await assertStatusCode(res, 409);
        expect((await res.json()).detail).toContain('it is being deleted');
      }
      await expectProcessDefinitionKeys(
        request,
        {processDefinitionId, state: 'SUSPENDED'},
        [],
      );

      const v2 = await deployUserTaskProcess(processDefinitionId, '-v2');
      const outcomes = await suspendAndListOutcomes(request, {
        processDefinitionId,
      });

      expect(outcomes).toEqual([
        [1, 'DRAINING'],
        [2, 'SUSPENDED'],
      ]);
      await expectProcessDefinitionState(
        request,
        v2.processDefinitionKey,
        'SUSPENDED',
      );
    });

    // eslint-disable-next-line playwright/expect-expect -- asserted through the request helpers
    test('Deleting a suspended version without instances deletes it', async ({
      request,
    }) => {
      const processDefinitionId = uniquePrefixedId('pd-suspend-delete');
      const [v1] = await deployUserTaskProcessVersions(processDefinitionId, 1);
      await suspendDefinitionAndWait(request, v1!.processDefinitionKey);

      await assertStatusCode(
        await deleteProcessDefinition(request, v1!.processDefinitionKey),
        200,
      );

      await expectProcessDefinitionDeleted(request, v1!.processDefinitionKey);
      await assertNotFoundRequest(
        await suspendProcessDefinitions(request, {
          processDefinitionKey: v1!.processDefinitionKey,
        }),
        'no such process definition',
      );
    });
  });

  test.describe('Partitions', () => {
    test('Creation is rejected on every partition after suspend and allowed on every partition after resume', async ({
      request,
    }) => {
      const partitions = await partitionsCount(request);
      const processDefinitionId = uniquePrefixedId('pd-suspend-partitions');
      const [v1] = await deployUserTaskProcessVersions(processDefinitionId, 1);
      const processDefinitionKey = v1!.processDefinitionKey;
      const probe = await deployUserTaskProcess(
        uniquePrefixedId('pd-suspend-partitions-probe'),
      );
      const attempts = partitions * 10;

      await suspendDefinitionAndWait(request, processDefinitionKey);

      const rotatedThrough =
        await expectCreationRejectedWhileProbesReachEveryPartition(
          request,
          {
            processDefinitionKey,
            processDefinitionId,
            processDefinitionVersion: 1,
          },
          probe.processDefinitionKey,
          partitions,
          attempts,
          (key) => instancesToCancel.push(key),
        );
      expect(rotatedThrough.size).toBe(partitions);

      await resumeDefinitionAndWait(request, processDefinitionKey);

      const reached = await createInstancesUntilEveryPartition(
        request,
        processDefinitionKey,
        partitions,
        attempts,
        (key) => instancesToCancel.push(key),
      );
      expect(reached.size).toBe(partitions);
    });

    test('Suspend directly followed by resume leaves the version active on every partition', async ({
      request,
    }) => {
      const processDefinitionId = uniquePrefixedId('pd-suspend-back-to-back');
      const [v1] = await deployUserTaskProcessVersions(processDefinitionId, 1);
      const processDefinitionKey = v1!.processDefinitionKey;

      await suspendAndListOutcomes(request, {processDefinitionKey});
      await resumeAndListOutcomes(request, {processDefinitionKey});

      await expectProcessDefinitionState(
        request,
        processDefinitionKey,
        'ACTIVE',
        extendedAssertionOptions,
      );
      const partitions = await partitionsCount(request);
      const reached = await createInstancesUntilEveryPartition(
        request,
        processDefinitionKey,
        partitions,
        partitions * 10,
        (key) => instancesToCancel.push(key),
      );
      expect(reached.size).toBe(partitions);
    });
  });
});
