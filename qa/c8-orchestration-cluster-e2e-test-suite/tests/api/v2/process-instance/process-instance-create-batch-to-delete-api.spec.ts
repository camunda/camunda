/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  cancelProcessInstance,
  createInstances,
  deploy,
} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertForbiddenRequest,
  assertInvalidArgument,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {
  createUser,
  grantUserResourceAuthorization,
  expectBatchState,
} from '@requestHelpers';
import {cleanupUsers} from 'utils/usersCleanup';

test.describe.parallel('Delete Batch Process Instance API Tests', () => {
  let processInstanceKeysToDelete: string[] = [];
  let activeProcessInstanceKeysToDelete: string[] = [];
  let userWithResourcesAuthorizationToSendRequest: {
    username: string;
    name: string;
    email: string;
    password: string;
  } = {} as {
    username: string;
    name: string;
    email: string;
    password: string;
  };

  test.beforeAll(async ({request}) => {
    await test.step('Setup - Deploy process and create instance to delete', async () => {
      await deploy([
        './resources/calledProcess.bpmn',
        './resources/IncidentProcess.bpmn',
      ]);
      const createdInstances = await createInstances('CalledProcess', 1, 5);
      for (const createdInstance of createdInstances) {
        processInstanceKeysToDelete.push(createdInstance.processInstanceKey);
      }

      const createdIncidentInstance = await createInstances(
        'IncidentProcess',
        1,
        5,
      );
      for (const createdInstance of createdIncidentInstance) {
        activeProcessInstanceKeysToDelete.push(
          createdInstance.processInstanceKey,
        );
      }
    });

    await test.step('Setup - Create test user with Resource Authorization and user for granting Authorization', async () => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
    });
  });

  test.afterAll(async ({request}) => {
    await test.step('Cleanup - Delete test users', async () => {
      await cleanupUsers(request, [
        userWithResourcesAuthorizationToSendRequest.username,
      ]);
    });

    await test.step('Cleanup - Cancel created process instances', async () => {
      for (const processInstanceKey of activeProcessInstanceKeysToDelete) {
        await cancelProcessInstance(processInstanceKey);
      }
    });

    await test.step('Cleanup - Clean arrays', async () => {
      processInstanceKeysToDelete = [];
      activeProcessInstanceKeysToDelete = [];
    });
  });

  test('Delete Batch Process Instance, single Process Instance - Success', async ({
    request,
  }) => {
    const processInstanceKeyToDelete = processInstanceKeysToDelete[0];
    let batchOperationKey = '';

    await test.step('Verify Process Instance Key to Delete has state COMPLETED', async () => {
      await expect(async () => {
        const response = await request.get(
          buildUrl(`/process-instances/${processInstanceKeyToDelete}`),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(response, 200);
        await validateResponse(
          {
            path: '/process-instances/{processInstanceKey}',
            method: 'GET',
            status: '200',
          },
          response,
        );
        const responseBody = await response.json();
        expect(responseBody.state).toBe('COMPLETED');
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Delete the process instance as batch', async () => {
      const res = await request.post(buildUrl('/process-instances/deletion'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: processInstanceKeyToDelete,
          },
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/process-instances/deletion',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      batchOperationKey = json.batchOperationKey;
      expect(json.batchOperationType).toBe('DELETE_PROCESS_INSTANCE');
    });

    await test.step('Verify the batch operation is completed', async () => {
      await expectBatchState(request, batchOperationKey, 'COMPLETED');
    });

    await test.step('Verify process instance is deleted', async () => {
      await expect(async () => {
        const response = await request.get(
          buildUrl(`/process-instances/${processInstanceKeyToDelete}`),
          {
            headers: jsonHeaders(),
          },
        );
        await assertNotFoundRequest(
          response,
          `Process Instance with key '${processInstanceKeyToDelete}' not found`,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Delete Batch Process Instance, multiple Process Instances - Success', async ({
    request,
  }) => {
    const processInstancesToDelete = processInstanceKeysToDelete.slice(1);
    let batchOperationKey = '';

    await test.step('Verify Process Instance Key to Delete has state COMPLETED', async () => {
      for (const processInstanceKey of processInstancesToDelete) {
        await expect(async () => {
          const response = await request.get(
            buildUrl(`/process-instances/${processInstanceKey}`),
            {
              headers: jsonHeaders(),
            },
          );
          await assertStatusCode(response, 200);
          await validateResponse(
            {
              path: '/process-instances/{processInstanceKey}',
              method: 'GET',
              status: '200',
            },
            response,
          );
          const responseBody = await response.json();
          expect(responseBody.state).toBe('COMPLETED');
        }).toPass(defaultAssertionOptions);
      }
    });

    await test.step('Delete the process instances as batch', async () => {
      const res = await request.post(buildUrl('/process-instances/deletion'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: {$in: processInstancesToDelete},
          },
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/process-instances/deletion',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      batchOperationKey = json.batchOperationKey;
      expect(json.batchOperationType).toBe('DELETE_PROCESS_INSTANCE');
    });

    await test.step('Verify the batch operation is completed', async () => {
      await expectBatchState(request, batchOperationKey, 'COMPLETED');
    });

    await test.step('Verify process instances are deleted', async () => {
      for (const processInstanceKey of processInstancesToDelete) {
        await expect(async () => {
          const response = await request.get(
            buildUrl(`/process-instances/${processInstanceKey}`),
            {
              headers: jsonHeaders(),
            },
          );
          await assertNotFoundRequest(
            response,
            `Process Instance with key '${processInstanceKey}' not found`,
          );
        }).toPass(defaultAssertionOptions);
      }
    });
  });

  test('Delete Batch Process Instance, single Process Instance - Unauthorized', async ({
    request,
  }) => {
    const someInstanceKey = '999999999999999';
    const res = await request.post(buildUrl('/process-instances/deletion'), {
      headers: {},
      data: {
        filter: {
          processInstanceKey: someInstanceKey,
        },
      },
    });
    await assertUnauthorizedRequest(res);
  });

  test('Delete Batch Process Instance, single Process Instance - Forbidden', async ({
    request,
  }) => {
    const someInstanceKey = '999999999999999';
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );
    const res = await request.post(buildUrl('/process-instances/deletion'), {
      headers: jsonHeaders(token), // ovverrides default demo:demo
      data: {
        filter: {
          processInstanceKey: someInstanceKey,
        },
      },
    });
    await assertForbiddenRequest(
      res,
      "Insufficient permissions to perform operation 'CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE'",
    );
  });

  test('Delete Batch Process Instance, no filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/process-instances/deletion'), {
      headers: jsonHeaders(),
      data: {
        // No filter or processInstanceKeys provided
      },
    });
    await assertInvalidArgument(res, 400, 'No filter provided.');
  });

  test('Delete Batch Process Instance, invalid filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/process-instances/deletion'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          invalidField: 'invalidValue',
        },
      },
    });
    await assertBadRequest(
      res,
      'Request property [filter.invalidField] cannot be parsed',
    );
  });
});
