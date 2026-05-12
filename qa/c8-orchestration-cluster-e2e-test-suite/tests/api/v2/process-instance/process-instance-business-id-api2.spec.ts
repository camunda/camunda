/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {cancelProcessInstance, deploy} from '../../../../utils/zeebeClient';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {
  defaultAssertionOptions,
  uniqueBusinessId,
} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';

const PROCESS_INSTANCE_ENDPOINT = '/process-instances';
const LONG_RUNNING_PROCESS_ID = 'process_with_task_listener';
const CALL_ACTIVITY_PROCESS_ID = 'call_user_task_process_process';

test.describe.parallel('Business ID - GET Response', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/process_with_task_listener.bpmn']);
  });

  test('GET process instance includes businessId when set at creation', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('get-with-id');
    const localState: Record<string, unknown> = {};

    await test.step('Start process instance with businessId', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {processDefinitionId: LONG_RUNNING_PROCESS_ID, businessId},
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      localState['processInstanceKey'] = json.processInstanceKey;
    });

    await test.step('GET process instance and verify businessId is returned', async () => {
      await expect(async () => {
        const res = await request.get(
          buildUrl(
            `${PROCESS_INSTANCE_ENDPOINT}/${localState['processInstanceKey']}`,
          ),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/process-instances/{processInstanceKey}',
            method: 'GET',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.processInstanceKey).toBe(localState['processInstanceKey']);
        expect(json.businessId).toBe(businessId);
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState['processInstanceKey'] as string);
  });

  test('GET process instance returns null businessId when not set at creation', async ({
    request,
  }) => {
    const localState: Record<string, unknown> = {};

    await test.step('Start process instance without businessId', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {processDefinitionId: LONG_RUNNING_PROCESS_ID},
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      localState['processInstanceKey'] = json.processInstanceKey;
    });

    await test.step('GET process instance and verify businessId is null', async () => {
      await expect(async () => {
        const res = await request.get(
          buildUrl(
            `${PROCESS_INSTANCE_ENDPOINT}/${localState['processInstanceKey']}`,
          ),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/process-instances/{processInstanceKey}',
            method: 'GET',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.processInstanceKey).toBe(localState['processInstanceKey']);
        expect(json).toHaveProperty('businessId', null);
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState['processInstanceKey'] as string);
  });
});

test.describe.parallel('Business ID - Search API', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/process_with_task_listener.bpmn']);
  });

  test('Search by businessId returns only the matching process instance', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('search-filter');
    const localState: Record<string, unknown> = {};

    await test.step('Start process instance with businessId', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {processDefinitionId: LONG_RUNNING_PROCESS_ID, businessId},
      });
      await assertStatusCode(res, 200);
      localState['processInstanceKey'] = (await res.json()).processInstanceKey;
    });

    await test.step('Search with businessId filter and verify exactly one result', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(`${PROCESS_INSTANCE_ENDPOINT}/search`),
          {
            headers: jsonHeaders(),
            data: {
              filter: {businessId},
            },
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/process-instances/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.page.totalItems).toBe(1);
        expect(json.items).toHaveLength(1);
        expect(json.items[0].processInstanceKey).toBe(
          localState['processInstanceKey'],
        );
        expect(json.items[0].businessId).toBe(businessId);
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState['processInstanceKey'] as string);
  });

  test('Search results include businessId field for all items', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('search-field');
    const localState: Record<string, unknown> = {};

    await test.step('Start process instance with businessId', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {processDefinitionId: LONG_RUNNING_PROCESS_ID, businessId},
      });
      await assertStatusCode(res, 200);
      localState['processInstanceKey'] = (await res.json()).processInstanceKey;
    });

    await test.step('Search and verify businessId field is present in search items', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(`${PROCESS_INSTANCE_ENDPOINT}/search`),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: localState['processInstanceKey'],
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/process-instances/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.items).toHaveLength(1);
        expect(
          Object.prototype.hasOwnProperty.call(json.items[0], 'businessId'),
        ).toBe(true);
        expect(json.items[0].businessId).toBe(businessId);
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState['processInstanceKey'] as string);
  });

  test('Search by businessId returns empty when no instance matches', async ({
    request,
  }) => {
    const nonExistentBusinessId = uniqueBusinessId('no-match');

    const res = await request.post(
      buildUrl(`${PROCESS_INSTANCE_ENDPOINT}/search`),
      {
        headers: jsonHeaders(),
        data: {
          filter: {businessId: nonExistentBusinessId},
        },
      },
    );
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/process-instances/search',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const json = await res.json();
    expect(json.page.totalItems).toBe(0);
    expect(json.items).toHaveLength(0);
  });

  test('Search results can be sorted by businessId', async ({request}) => {
    const businessId1 = `aaa-${uniqueBusinessId('sort-a')}`;
    const businessId2 = `zzz-${uniqueBusinessId('sort-z')}`;
    const localState: Record<string, unknown> = {};

    await test.step('Start two process instances with different businessIds', async () => {
      const res1 = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: LONG_RUNNING_PROCESS_ID,
          businessId: businessId1,
        },
      });
      await assertStatusCode(res1, 200);
      localState['key1'] = (await res1.json()).processInstanceKey;

      const res2 = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: LONG_RUNNING_PROCESS_ID,
          businessId: businessId2,
        },
      });
      await assertStatusCode(res2, 200);
      localState['key2'] = (await res2.json()).processInstanceKey;
    });

    await test.step('Search sorted by businessId ascending and verify order', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(`${PROCESS_INSTANCE_ENDPOINT}/search`),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: {
                  $in: [localState['key1'], localState['key2']],
                },
              },
              sort: [{field: 'businessId', order: 'asc'}],
            },
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/process-instances/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.items).toHaveLength(2);
        expect(json.items[0].businessId).toBe(businessId1);
        expect(json.items[1].businessId).toBe(businessId2);
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState['key1'] as string);
    await cancelProcessInstance(localState['key2'] as string);
  });
});

test.describe.parallel('Business ID - Call Activity Propagation', () => {
  test.beforeAll(async () => {
    await deploy([
      './resources/call_user_task_process_process.bpmn',
      './resources/process_with_task_listener.bpmn',
    ]);
  });

  test('Child process instance inherits Business ID from parent via call activity', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('call-activity-inherit');
    const localState: Record<string, unknown> = {};

    await test.step('Start parent process with businessId', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: CALL_ACTIVITY_PROCESS_ID,
          businessId,
        },
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      expect(json.businessId).toBe(businessId);
      localState['parentProcessInstanceKey'] = json.processInstanceKey;
    });

    await test.step('Search for child process instance by parent key', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(`${PROCESS_INSTANCE_ENDPOINT}/search`),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                parentProcessInstanceKey:
                  localState['parentProcessInstanceKey'],
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.page.totalItems).toBe(1);
        localState['childProcessInstanceKey'] =
          json.items[0].processInstanceKey;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('GET child process instance and verify it inherits businessId', async () => {
      await expect(async () => {
        const res = await request.get(
          buildUrl(
            `${PROCESS_INSTANCE_ENDPOINT}/${localState['childProcessInstanceKey']}`,
          ),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/process-instances/{processInstanceKey}',
            method: 'GET',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.businessId).toBe(businessId);
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(
      localState['parentProcessInstanceKey'] as string,
    );
  });

  test('Child process instance has no Business ID when parent has none', async ({
    request,
  }) => {
    const localState: Record<string, unknown> = {};

    await test.step('Start parent process without businessId', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {processDefinitionId: CALL_ACTIVITY_PROCESS_ID},
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      expect(json).toHaveProperty('businessId', null);
      localState['parentProcessInstanceKey'] = json.processInstanceKey;
    });

    await test.step('Search for child process instance by parent key', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(`${PROCESS_INSTANCE_ENDPOINT}/search`),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                parentProcessInstanceKey:
                  localState['parentProcessInstanceKey'],
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.page.totalItems).toBe(1);
        localState['childProcessInstanceKey'] =
          json.items[0].processInstanceKey;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('GET child process instance and verify businessId is null', async () => {
      await expect(async () => {
        const res = await request.get(
          buildUrl(
            `${PROCESS_INSTANCE_ENDPOINT}/${localState['childProcessInstanceKey']}`,
          ),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/process-instances/{processInstanceKey}',
            method: 'GET',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json).toHaveProperty('businessId', null);
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(
      localState['parentProcessInstanceKey'] as string,
    );
  });
});
