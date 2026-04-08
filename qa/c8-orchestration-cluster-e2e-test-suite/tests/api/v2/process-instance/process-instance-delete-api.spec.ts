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
  createSingleInstance,
  deploy,
} from '../../../../utils/zeebeClient';
import {
  assertForbiddenRequest,
  assertInvalidState,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {createUser, grantUserResourceAuthorization} from '@requestHelpers';
import {cleanupUsers} from 'utils/usersCleanup';

test.describe.parallel('Delete Single Process Instance API Tests', () => {
  let processInstanceKeyToDelete: string = '';
  let activeProcessInstanceKeyToDelete: string = '';
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
      const createdInstance = await createSingleInstance('CalledProcess', 1);
      processInstanceKeyToDelete = createdInstance.processInstanceKey;

      const createdIncidentInstance = await createSingleInstance(
        'IncidentProcess',
        1,
      );
      activeProcessInstanceKeyToDelete =
        createdIncidentInstance.processInstanceKey;
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
      await cancelProcessInstance(activeProcessInstanceKeyToDelete);
    });
  });

  test('Delete Single Process Instance - Success', async ({request}) => {
    await test.step('Verify process instance is listed and complete', async () => {
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

    await test.step('Delete the process instance', async () => {
      const response = await request.post(
        buildUrl(`/process-instances/${processInstanceKeyToDelete}/deletion`),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(response, 204);
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

  test('Delete Single Process Instance - Unauthorized', async ({request}) => {
    const someInstanceKey = '999999999999999';
    const response = await request.post(
      buildUrl(`/process-instances/${someInstanceKey}/deletion`),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(response);
  });

  test('Delete Single Process Instance - Forbidden', async ({request}) => {
    const someInstanceKey = '999999999999999';
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );
    const response = await request.post(
      buildUrl(`/process-instances/${someInstanceKey}/deletion`),
      {
        headers: jsonHeaders(token), // overrides default demo:demo
      },
    );
    await assertForbiddenRequest(
      response,
      "Insufficient permissions to perform operation 'DELETE_PROCESS_INSTANCE'",
    );
  });

  test('Delete Single Process Instance - Not Found', async ({request}) => {
    const someNotExistingInstanceKey = '999999999999999';

    await test.step('Delete the process instance', async () => {
      const response = await request.post(
        buildUrl(`/process-instances/${someNotExistingInstanceKey}/deletion`),
        {
          headers: jsonHeaders(),
        },
      );
      await assertNotFoundRequest(
        response,
        `Process Instance with key '${someNotExistingInstanceKey}' not found`,
      );
    });
  });

  test('Delete Single Process Instance - Conflict', async ({request}) => {
    await test.step('Verify process instance is listed and active', async () => {
      await expect(async () => {
        const response = await request.get(
          buildUrl(`/process-instances/${activeProcessInstanceKeyToDelete}`),
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
        expect(responseBody.state).toBe('ACTIVE');
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Try to delete active process instance', async () => {
      const response = await request.post(
        buildUrl(
          `/process-instances/${activeProcessInstanceKeyToDelete}/deletion`,
        ),
        {
          headers: jsonHeaders(),
        },
      );
      await assertInvalidState(response, 409);
    });
  });
});
