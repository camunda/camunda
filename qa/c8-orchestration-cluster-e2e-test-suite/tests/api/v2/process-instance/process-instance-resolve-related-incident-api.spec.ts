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
  assertBadRequest,
  assertForbiddenRequest,
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

test.describe.parallel('Resolve related incidents API Tests', () => {
  let processInstanceKeyWithIncidentToResolve: string = '';
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
    //**
    // * Deplyed and instanciated process instance has two incidents related to it.
    // * During test only one incident is fixed and actually resolved.
    // * Use of the same process instance in unhappy paths tests does not cause any issue,
    // * as process instance will always have at least one active incident.
    // **
    await test.step('Setup - Deploy process and create instance to delete', async () => {
      await deploy(['./resources/MultipleErrorTypesProcess.bpmn']);
      const createdInstance = await createSingleInstance(
        'MultipleErrorTypesProcess',
        1,
      );
      processInstanceKeyWithIncidentToResolve =
        createdInstance.processInstanceKey;
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
      await cancelProcessInstance(processInstanceKeyWithIncidentToResolve);
    });
  });

  test('Resolve related incidents of a process instance - Success', async ({
    request,
  }) => {
    let elementInstanceKey: string = '';
    let batchOperationKey: string = '';
    let incidentKeys: string[] = [];

    await test.step('Search for incidents related to created process instance', async () => {
      await expect(async () => {
        incidentKeys = [];
        elementInstanceKey = '';
        const incidents = await request.post(
          buildUrl(
            `/process-instances/${processInstanceKeyWithIncidentToResolve}/incidents/search`,
          ),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(incidents, 200);
        await validateResponse(
          {
            path: '/process-instances/{processInstanceKey}/incidents/search',
            method: 'POST',
            status: '200',
          },
          incidents,
        );
        const body = await incidents.json();
        expect(body.page.totalItems).toEqual(2);
        expect(body.items.length).toEqual(2);
        for (const incident of body.items) {
          incidentKeys.push(incident.incidentKey);
          expect(incident.state).toBe('ACTIVE');
          if (incident.errorType === 'EXTRACT_VALUE_ERROR') {
            elementInstanceKey = incident.elementInstanceKey;
          }
        }
        expect(elementInstanceKey).not.toEqual('');
        expect(incidentKeys.length).toEqual(2);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Update element instance variables', async () => {
      const updateRes = await request.put(
        buildUrl(`/element-instances/${elementInstanceKey}/variables`),
        {
          headers: jsonHeaders(),
          data: {
            variables: {
              goUp: 6,
            },
          },
        },
      );
      await assertStatusCode(updateRes, 204);
    });

    await test.step('Resolve incidents', async () => {
      const resolveRes = await request.post(
        buildUrl(
          `/process-instances/${processInstanceKeyWithIncidentToResolve}/incident-resolution`,
        ),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(resolveRes, 200);
      await validateResponse(
        {
          path: `/process-instances/{processInstanceKey}/incident-resolution`,
          method: 'POST',
          status: '200',
        },
        resolveRes,
      );
      const body = await resolveRes.json();
      batchOperationKey = body.batchOperationKey;
      expect(body.batchOperationType).toBe('RESOLVE_INCIDENT');
    });

    await test.step('Poll batch operation until completion', async () => {
      await expect(async () => {
        const statusRes = await request.get(
          buildUrl(`/batch-operations/${batchOperationKey}`),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(statusRes, 200);
        await validateResponse(
          {
            path: '/batch-operations/{batchOperationKey}',
            method: 'GET',
            status: '200',
          },
          statusRes,
        );
        const body = await statusRes.json();
        expect(body.state).toBe('COMPLETED');
        expect(body.operationsTotalCount).toBeGreaterThanOrEqual(2);
      }).toPass({
        intervals: [5_000, 10_000, 15_000, 25_000, 35_000],
        timeout: 120_000,
      });
    });

    await test.step('Search for incidents and verify the state is resolved', async () => {
      await expect(async () => {
        const searchRes = await request.post(buildUrl('/incidents/search'), {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: processInstanceKeyWithIncidentToResolve,
            },
          },
        });

        await assertStatusCode(searchRes, 200);
        await validateResponse(
          {
            path: '/incidents/search',
            method: 'POST',
            status: '200',
          },
          searchRes,
        );

        const body = await searchRes.json();
        expect(body.page.totalItems).toBeGreaterThanOrEqual(3);
        expect(body.items.length).toBeGreaterThan(0);
        for (const incident of body.items) {
          if (incidentKeys.includes(incident.incidentKey)) {
            expect(incident.state).toBe('RESOLVED');
          } else {
            expect(incident.state).toBe('ACTIVE');
          }
        }
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Resolve related incidents with process instance key string value - Bad Request', async ({
    request,
  }) => {
    const invalidProcessInstanceKey = 'meow';
    const resolveRes = await request.post(
      buildUrl(
        `/process-instances/${invalidProcessInstanceKey}/incident-resolution`,
      ),
      {
        headers: jsonHeaders(),
      },
    );
    await assertBadRequest(
      resolveRes,
      `Failed to convert 'processInstanceKey' with value: '${invalidProcessInstanceKey}'`,
    );
  });

  test('Resolve related incidents of a process instance - Unauthorized', async ({
    request,
  }) => {
    const someNotExistingProcessInstanceKey = '123456789';
    const resolveRes = await request.post(
      buildUrl(
        `/process-instances/${someNotExistingProcessInstanceKey}/incident-resolution`,
      ),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(resolveRes);
  });

  test('Resolve related incidents of a not existing process instance - Not found', async ({
    request,
  }) => {
    const someNotExistingProcessInstanceKey = '123456789';
    const resolveRes = await request.post(
      buildUrl(
        `/process-instances/${someNotExistingProcessInstanceKey}/incident-resolution`,
      ),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      resolveRes,
      `Process Instance with key '${someNotExistingProcessInstanceKey}' not found`,
    );
  });

  test('Resolve related incidents of a process instance without permissions - Forbidden', async ({
    request,
  }) => {
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );
    const resolveRes = await request.post(
      buildUrl(
        `/process-instances/${processInstanceKeyWithIncidentToResolve}/incident-resolution`,
      ),
      {
        headers: jsonHeaders(token), // overrides default demo:demo
      },
    );
    await assertForbiddenRequest(
      resolveRes,
      "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource",
    );
  });
});
