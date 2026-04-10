/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  jsonHeaders,
  buildUrl,
  assertBadRequest,
  assertUnauthorizedRequest,
  assertStatusCode,
  assertInvalidArgument,
  encode,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from 'json-body-assertions';
import {cleanupUsers} from 'utils/usersCleanup';
import {grantUserResourceAuthorization} from 'utils/requestHelpers/authorization-requestHelpers';
import {createUser} from 'utils/requestHelpers/user-requestHelpers';

const AUDIT_LOG_SEARCH_ENDPOINT = '/audit-logs/search';

type SortOrder = 'ASC' | 'DESC';
const sortTestCases: Array<{
  description: string;
  sort: {field: string; order: SortOrder};
}> = [
  {
    description: 'Search Audit Logs - Sort by actorId DESC',
    sort: {field: 'actorId', order: 'DESC'},
  },
  {
    description: 'Search Audit Logs - Sort by timestamp ASC',
    sort: {field: 'timestamp', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by timestamp DESC',
    sort: {field: 'timestamp', order: 'DESC'},
  },
  {
    description: 'Search Audit Logs - Sort by operationType ASC',
    sort: {field: 'operationType', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by category ASC',
    sort: {field: 'category', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by entityType ASC',
    sort: {field: 'entityType', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by result ASC',
    sort: {field: 'result', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by auditLogKey ASC',
    sort: {field: 'auditLogKey', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by entityKey ASC',
    sort: {field: 'entityKey', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by actorType ASC',
    sort: {field: 'actorType', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by processDefinitionKey ASC',
    sort: {field: 'processDefinitionKey', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by processDefinitionId ASC',
    sort: {field: 'processDefinitionId', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by processInstanceKey ASC',
    sort: {field: 'processInstanceKey', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by tenantId ASC',
    sort: {field: 'tenantId', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by batchOperationType ASC',
    sort: {field: 'batchOperationType', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by batchOperationKey ASC',
    sort: {field: 'batchOperationKey', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by elementInstanceKey ASC',
    sort: {field: 'elementInstanceKey', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by jobKey ASC',
    sort: {field: 'jobKey', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by userTaskKey ASC',
    sort: {field: 'userTaskKey', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by decisionDefinitionKey ASC',
    sort: {field: 'decisionDefinitionKey', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by decisionDefinitionId ASC',
    sort: {field: 'decisionDefinitionId', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by decisionRequirementsKey ASC',
    sort: {field: 'decisionRequirementsKey', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by decisionRequirementsId ASC',
    sort: {field: 'decisionRequirementsId', order: 'ASC'},
  },
  {
    description: 'Search Audit Logs - Sort by decisionEvaluationKey ASC',
    sort: {field: 'decisionEvaluationKey', order: 'ASC'},
  },
];

for (const {description, sort} of sortTestCases) {
  test(description, async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          sort: [sort],
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUDIT_LOG_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      const values = body.items.map(
        (item: Record<string, unknown>) => item[sort.field],
      ) as Array<string | number>;
      const sorted = [...values].sort();
      if (sort.order === 'DESC') {
        sorted.reverse();
      }
      expect(values).toEqual(sorted);
    }).toPass(defaultAssertionOptions);
  });
}

const filterTestCases: Array<{
  description: string;
  filter: Record<string, string>;
}> = [
  {
    description: 'Search Audit Logs - Filter by operationType',
    filter: {operationType: 'CREATE'},
  },
  {
    description: 'Search Audit Logs - Filter by result SUCCESS',
    filter: {result: 'SUCCESS'},
  },
  {
    description: 'Search Audit Logs - Filter by actorType USER',
    filter: {actorType: 'USER'},
  },
  {
    description: 'Search Audit Logs - Filter by category',
    filter: {category: 'ADMIN'},
  },
  {
    description: 'Search Audit Logs - Filter by entityType',
    filter: {entityType: 'PROCESS_INSTANCE'},
  },
  {
    description: 'Search Audit Logs - Filter by actorId',
    filter: {actorId: 'demo'},
  },
  {
    description: 'Search Audit Logs - Filter by operationType and result',
    filter: {operationType: 'CREATE', result: 'SUCCESS'},
  },
  {
    description: 'Search Audit Logs - Filter by actorType and category',
    filter: {actorType: 'USER', category: 'ADMIN'},
  },
  {
    description:
      'Search Audit Logs - Filter by actorId, operationType, and result',
    filter: {actorId: 'demo', operationType: 'CREATE', result: 'SUCCESS'},
  },
];
for (const {description, filter} of filterTestCases) {
  test(description, async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter,
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUDIT_LOG_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      body.items.forEach((item: Record<string, unknown>) => {
        for (const [key, value] of Object.entries(filter)) {
          expect(item[key]).toBe(value);
        }
      });
    }).toPass(defaultAssertionOptions);
  });
}

test.describe.parallel('Search Audit Logs API Tests', () => {
  test('Search Audit Logs Success', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUDIT_LOG_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Audit Logs - Filter by actorId - Empty result', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            actorId: 'NonExistingActorId',
          },
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUDIT_LOG_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.items.length).toEqual(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Audit Logs with page limit', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          page: {
            limit: 1,
          },
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUDIT_LOG_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.items.length).toEqual(1);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Audit Logs - Filter by operationType and sort by timestamp DESC', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            operationType: 'CREATE',
          },
          sort: [
            {
              field: 'timestamp',
              order: 'DESC',
            },
          ],
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUDIT_LOG_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.operationType).toBe('CREATE');
      });
      const timestamps = body.items.map(
        (item: Record<string, unknown>) => item.timestamp,
      ) as string[];
      const sorted = [...timestamps].sort().reverse();
      expect(timestamps).toEqual(sorted);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Audit Logs - Filter by actorType, sort by actorId, with page limit', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            actorType: 'USER',
          },
          sort: [
            {
              field: 'actorId',
              order: 'ASC',
            },
          ],
          page: {
            limit: 5,
          },
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUDIT_LOG_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.items.length).toBeLessThanOrEqual(5);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.actorType).toBe('USER');
      });
      const values = body.items.map(
        (item: Record<string, unknown>) => item.actorId,
      ) as string[];
      console.log('Extracted actorIds:', values);
      const sorted = [...values].sort();
      expect(values).toEqual(sorted);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Audit Logs - Invalid sort field', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: 'invalidField',
              order: 'ASC',
            },
          ],
        },
      });

      await assertBadRequest(
        res,
        "Unexpected value 'invalidField' for enum field 'field'. Use any of the following values: [actorId, actorType, auditLogKey, batchOperationKey, batchOperationType, category, decisionDefinitionId, decisionDefinitionKey, decisionEvaluationKey, decisionRequirementsId, decisionRequirementsKey, elementInstanceKey, entityKey, entityType, jobKey, operationType, processDefinitionId, processDefinitionKey, processInstanceKey, result, tenantId, timestamp, userTaskKey]",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Audit Logs - Null sort field', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              order: 'DESC',
            },
          ],
        },
      });

      await assertInvalidArgument(res, 400, 'Sort field must not be null.');
    }).toPass(defaultAssertionOptions);
  });

  test('Search Audit Logs - Invalid filter field', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            invalidField: 'someValue',
          },
        },
      });

      await assertBadRequest(
        res,
        'Request property [filter.invalidField] cannot be parsed',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Audit Logs - Unauthorized', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
        headers: {},
        data: {},
      });

      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Audit Logs with negative page limit - Bad Request', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          page: {
            limit: -1,
          },
        },
      });

      await assertInvalidArgument(
        res,
        400,
        "The value for page.limit is '-1' but must be a non-negative number.",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Audit Logs - No granted permissions', async ({request}) => {
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

    await test.step('Setup - Create test user with Resource Authorization', async () => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
    });

    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );

    await test.step('Search Audit Logs with user having only Resource Authorization - Expect Empty', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl(AUDIT_LOG_SEARCH_ENDPOINT), {
          headers: jsonHeaders(token), // overrides default demo:demo
          data: {},
        });
        await validateResponse(
          {
            path: AUDIT_LOG_SEARCH_ENDPOINT,
            method: 'POST',
            status: '200',
          },
          res,
        );
        const body = await res.json();
        expect(body.page.totalItems).toEqual(0);
        expect(body.items.length).toEqual(0);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Cleanup', async () => {
      await cleanupUsers(request, [
        userWithResourcesAuthorizationToSendRequest.username,
      ]);
    });
  });
});
