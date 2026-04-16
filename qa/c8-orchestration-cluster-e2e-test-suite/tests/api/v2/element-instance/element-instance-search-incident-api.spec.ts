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
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  expectProcessInstanceCanBeFound,
  activateJobToObtainAValidJobKey,
  grantUserResourceAuthorization,
  createUser,
} from '@requestHelpers';
import {sleep} from 'utils/sleep';
import {cleanupUsers} from 'utils/usersCleanup';

test.describe('Element Instance Incident Search API', () => {
  const state: Record<string, unknown> = {};
  const processInstanceKeys: string[] = [];
  const expectedIncidentTypes = ['JOB_NO_RETRIES', 'IO_MAPPING_ERROR'];

  test.beforeAll(async ({request}) => {
    await test.step('Deploy processes and create instance', async () => {
      const deployment1 = await deploy(['./resources/calledErrorProcess.bpmn']);
      state['processDefinitionKeyCalled'] =
        deployment1.processes[0].processDefinitionKey;
      await deploy(['./resources/callErrorProcess.bpmn']);
      await createInstances('callProcessWithAnError', 1, 1).then(
        (instances) => {
          console.log(instances[0].processInstanceKey);
          state.processInstanceKey = instances[0].processInstanceKey;
          state.processDefinitionKey = instances[0].processDefinitionKey;
          processInstanceKeys.push(instances[0].processInstanceKey);
          console.log(`State: ${JSON.stringify(state)}`);
        },
      );
      await sleep(5000);
    });

    await test.step('Verify instance is created and search by processInstanceKey and element type returns element instance', async () => {
      await expectProcessInstanceCanBeFound(
        request,
        state.processInstanceKey as string,
      );

      await expect(async () => {
        const res = await request.post(buildUrl('/element-instances/search'), {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: state.processInstanceKey,
              type: 'CALL_ACTIVITY',
            },
          },
        });
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/element-instances/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const body = await res.json();
        expect(body.page.totalItems).toEqual(1);
        expect(body.items[0].processInstanceKey).toEqual(
          state.processInstanceKey,
        );
        expect(body.items[0].type).toEqual('CALL_ACTIVITY');
        state.elementInstanceKey = body.items[0].elementInstanceKey;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Fail job on called process', async () => {
      const jobKey = await activateJobToObtainAValidJobKey(request, 'meow');
      const failRes = await request.post(buildUrl(`/jobs/${jobKey}/failure`), {
        headers: jsonHeaders(),
        data: {
          retries: 0,
          errorMessage: 'Simulated failure',
        },
      });

      await assertStatusCode(failRes, 204);
    });
  });

  test.afterAll(async () => {
    for (const instance of processInstanceKeys) {
      await cancelProcessInstance(instance as string);
    }
  });

  test('Search for incidents of a specific element instance - Multiple Results - Success', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/element-instances/${state.elementInstanceKey}/incidents/search`,
        ),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: `/element-instances/{elementInstanceKey}/incidents/search`,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.page.totalItems).toEqual(2);
      body.items.forEach((item: Record<string, string>) => {
        expect(expectedIncidentTypes).toContain(item.errorType);
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search for incidents of a specific element instance - filtered by errorType - Single Result - Success', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/element-instances/${state.elementInstanceKey}/incidents/search`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              errorType: 'IO_MAPPING_ERROR',
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: `/element-instances/{elementInstanceKey}/incidents/search`,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.page.totalItems).toEqual(1);
      expect(body.items[0].errorType).toEqual('IO_MAPPING_ERROR');
      expect(body.items[0].processDefinitionKey).toEqual(
        state.processDefinitionKeyCalled,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search for incidents of a specific element instance - filtered by processDefinitionKey and state - Single Result - Success', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/element-instances/${state.elementInstanceKey}/incidents/search`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processDefinitionKey: state.processDefinitionKeyCalled,
              state: 'ACTIVE',
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: `/element-instances/{elementInstanceKey}/incidents/search`,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.page.totalItems).toEqual(2);
      body.items.forEach((item: Record<string, string>) => {
        expect(item.processDefinitionKey).toEqual(
          state.processDefinitionKeyCalled,
        );
        expect(item.state).toEqual('ACTIVE');
      });
    }).toPass(defaultAssertionOptions);
  });

  //Skipped due to bug 46661: https://github.com/camunda/camunda/issues/46661
  test.skip('Search for incidents of a specific element instance - ascending order by errorMessage - Success', async ({
    request,
  }) => {
    const errorMessage1 =
      "Assertion failure on evaluate the expression '{meow:assert(foo, foo != null)}': The condition is not fulfilled The evaluation reported the following warnings:\n[NO_VARIABLE_FOUND] No variable found with name 'foo'\n[NO_VARIABLE_FOUND] No variable found with name 'foo'\n[ASSERT_FAILURE] The condition is not fulfilled";
    const errorMessage2 = 'Simulated failure';
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/element-instances/${state.elementInstanceKey}/incidents/search`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            sort: [
              {
                field: 'errorMessage',
                order: 'ASC',
              },
            ],
            filter: {
              processDefinitionKey: state.processDefinitionKeyCalled,
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: `/element-instances/{elementInstanceKey}/incidents/search`,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.page.totalItems).toEqual(2);
      expect(body.items[0].errorMessage).toEqual(errorMessage2);
      expect(body.items[1].errorMessage).toEqual(errorMessage1);
      body.items.forEach((item: Record<string, string>) => {
        expect(item.processDefinitionKey).toEqual(
          state.processDefinitionKeyCalled,
        );
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search for incidents of a specific element instance - Empty Result - Success', async ({
    request,
  }) => {
    const notExistingProcessInstanceKey = '9999999999999';
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/element-instances/${state.elementInstanceKey}/incidents/search`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: notExistingProcessInstanceKey,
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: `/element-instances/{elementInstanceKey}/incidents/search`,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.page.totalItems).toEqual(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search for incidents of a specific element instance - Wrong Filter Value - Bad Request', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/element-instances/${state.elementInstanceKey}/incidents/search`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: 'notANumber',
            },
          },
        },
      );
      await assertBadRequest(
        res,
        "The provided processInstanceKey 'notANumber' is not a valid key",
        'INVALID_ARGUMENT',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search for incidents of a specific element instance - Wrong Filter Field - Bad Request', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/element-instances/${state.elementInstanceKey}/incidents/search`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKeyyyy: '21312312',
            },
          },
        },
      );
      await assertBadRequest(
        res,
        'Request property [filter.processInstanceKeyyyy] cannot be parsed',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search for incidents of a specific element instance - Not Existing Element Instance Key - Not Found', async ({
    request,
  }) => {
    const notExistingElementInstanceKey = '9999999999999';
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/element-instances/${notExistingElementInstanceKey}/incidents/search`,
        ),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );
      await assertNotFoundRequest(
        res,
        `Element Instance with key '${notExistingElementInstanceKey}' not found`,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search for incidents of a specific element instance - Unauthorized', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/element-instances/${state.elementInstanceKey}/incidents/search`,
        ),
        {
          headers: {},
          data: {},
        },
      );
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Search for incidents of a specific element instance - Forbidden', async ({
    request,
  }) => {
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

    await test.step('Setup - Create user for authorization tests', async () => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
    });

    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );

    await test.step('Attempt to search element instance incidents without proper authorization', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(
            `/element-instances/${state.elementInstanceKey}/incidents/search`,
          ),
          {
            headers: jsonHeaders(token), // overrides default demo:demo
            data: {},
          },
        );
        await assertForbiddenRequest(
          res,
          "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'",
        );
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Cleanup - delete user', async () => {
      await cleanupUsers(request, [
        userWithResourcesAuthorizationToSendRequest.username,
      ]);
    });
  });

  //Skipped due to bug 39372: https://github.com/camunda/camunda/issues/39372
  test.skip('Search for incidents of a specific element instance - with invalid pagination parameters', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/element-instances/${state.elementInstanceKey}/incidents/search`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            page: {
              limit: -1,
            },
          },
        },
      );
      await assertBadRequest(
        res,
        'Sort field must not be null.',
        'INVALID_ARGUMENT',
      );
    }).toPass(defaultAssertionOptions);
  });
});
