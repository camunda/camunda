/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  buildUrl,
  jsonHeaders,
  assertEqualsForKeys,
  assertStatusCode,
  assertUnauthorizedRequest,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {createInstances, deploy} from '../../../../utils/zeebeClient';

test.beforeAll(async () => {
  await deploy(['./resources/mcpProcessAlpha.bpmn']);
  await deploy(['./resources/mcpProcessBravo.bpmn']);
  await deploy(['./resources/mcp-process-with-inputs.bpmn']);
  await deploy(['./resources/mcp-process-intermediate-event.bpmn']);
  // Deploy a modified Alpha (same processId, different BPMN content) to create version 2
  await deploy(['./resources/mcp-process-alpha-v2.bpmn']);
  // Create an instance so the PROCESS_EVENT (intermediate) subscription becomes active
  await createInstances('mcpIntermediateProcess', 1, 1);
});

test.describe('MCP Message Subscription Search API Tests', () => {
  test('Search MCP Subscriptions Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/message-subscriptions/search'), {
      headers: {},
      data: {filter: {messageSubscriptionType: 'START_EVENT'}},
    });
    await assertUnauthorizedRequest(res);
  });

  test('MCP Message Subscription Search Flow', async ({request}) => {
    await test.step('SC-API-01 — Filter by messageSubscriptionType START_EVENT', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/message-subscriptions/search'),
          {
            headers: jsonHeaders(),
            data: {filter: {messageSubscriptionType: 'START_EVENT'}},
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/message-subscriptions/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.page.totalItems).toBeGreaterThanOrEqual(3);

        const mcpIds = json.items.map(
          (it: {processDefinitionId: string}) => it.processDefinitionId,
        );
        expect(mcpIds).toContain('mcpProcessAlpha');
        expect(mcpIds).toContain('mcpProcessBravo');
        expect(mcpIds).toContain('mcpProcessWithInputs');
        expect(mcpIds).not.toContain('mcpIntermediateProcess');

        json.items.forEach((it: {messageSubscriptionType: string}) => {
          expect(it.messageSubscriptionType).toBe('START_EVENT');
        });
      }).toPass({
        intervals: [5_000, 10_000, 15_000],
        timeout: 30_000,
      });
    });

    await test.step('SC-API-02 — Filter by messageSubscriptionType PROCESS_EVENT', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/message-subscriptions/search'),
          {
            headers: jsonHeaders(),
            data: {filter: {messageSubscriptionType: 'PROCESS_EVENT'}},
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/message-subscriptions/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.page.totalItems).toBeGreaterThanOrEqual(1);

        const processIds = json.items.map(
          (it: {processDefinitionId: string}) => it.processDefinitionId,
        );
        expect(processIds).toContain('mcpIntermediateProcess');
        expect(processIds).not.toContain('mcpProcessAlpha');

        json.items.forEach((it: {messageSubscriptionType: string}) => {
          expect(it.messageSubscriptionType).toBe('PROCESS_EVENT');
        });
      }).toPass({
        intervals: [5_000, 10_000, 15_000],
        timeout: 30_000,
      });
    });

    await test.step('SC-API-03 — No type filter returns both START_EVENT and PROCESS_EVENT', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {page: {limit: 100}},
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      const types = json.items.map(
        (it: {messageSubscriptionType: string}) => it.messageSubscriptionType,
      );
      expect(types).toContain('START_EVENT');
      expect(types).toContain('PROCESS_EVENT');
    });

    await test.step('SC-API-04 — extensionProperties contains tool metadata', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processDefinitionId: 'mcpProcessAlpha',
              messageSubscriptionType: 'START_EVENT',
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);

      json.items.forEach(
        (it: {extensionProperties: Record<string, string>}) => {
          expect(it.extensionProperties).toBeDefined();
          expect(it.extensionProperties['io.camunda.tool:name']).toBe(
            'alpha-tool-name',
          );
          expect(
            it.extensionProperties['io.camunda.tool:purpose'],
          ).toBeDefined();
        },
      );
    });

    await test.step('SC-API-05 — processDefinitionName and processDefinitionVersion returned', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processDefinitionId: 'mcpProcessBravo',
              messageSubscriptionType: 'START_EVENT',
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);

      json.items.forEach(
        (it: {
          processDefinitionName: string;
          processDefinitionVersion: number;
        }) => {
          assertEqualsForKeys(
            it,
            {
              processDefinitionName: 'MCP Process Bravo',
              messageName: 'bravo-tool-name',
              tenantId: '<default>',
            },
            ['processDefinitionName', 'messageName', 'tenantId'],
          );
          expect(it.processDefinitionVersion).toBeGreaterThanOrEqual(1);
        },
      );
    });

    await test.step('SC-API-06 — processInstanceKey and elementInstanceKey are null for start events', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              messageSubscriptionType: 'START_EVENT',
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);

      json.items.forEach(
        (it: {processInstanceKey: unknown; elementInstanceKey: unknown}) => {
          expect(it.processInstanceKey).toBeNull();
          expect(it.elementInstanceKey).toBeNull();
        },
      );
    });

    await test.step('SC-API-07 — Filter by toolName', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              toolName: 'alpha-tool-name',
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);

      json.items.forEach(
        (it: {
          processDefinitionId: string;
          toolName: string;
          extensionProperties: Record<string, string>;
        }) => {
          expect(it.processDefinitionId).toBe('mcpProcessAlpha');
          expect(it.toolName).toBe('alpha-tool-name');
          expect(it.extensionProperties['io.camunda.tool:name']).toBe(
            'alpha-tool-name',
          );
        },
      );
    });

    await test.step('SC-API-08 — Filter by processDefinitionId for with-inputs process includes extensionProperties with input metadata', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processDefinitionId: 'mcpProcessWithInputs',
              messageSubscriptionType: 'START_EVENT',
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);

      json.items.forEach(
        (it: {extensionProperties: Record<string, string>}) => {
          assertEqualsForKeys(
            it,
            {
              processDefinitionId: 'mcpProcessWithInputs',
              messageSubscriptionType: 'START_EVENT',
              tenantId: '<default>',
            },
            ['processDefinitionId', 'messageSubscriptionType', 'tenantId'],
          );
          expect(it.extensionProperties['io.camunda.tool:input_1_name']).toBe(
            'firstName',
          );
          expect(it.extensionProperties['io.camunda.tool:input_1_type']).toBe(
            'string',
          );
          expect(it.extensionProperties['io.camunda.tool:input_2_name']).toBe(
            'amount',
          );
          expect(
            it.extensionProperties['io.camunda.tool:input_2_required'],
          ).toBe('true');
        },
      );
    });

    await test.step('SC-API-09 — Sort by processDefinitionName ascending', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {messageSubscriptionType: 'START_EVENT'},
            sort: [{field: 'processDefinitionName', order: 'ASC'}],
          },
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(2);

      const names: (string | null)[] = json.items
        .map(
          (it: {processDefinitionName: string | null}) =>
            it.processDefinitionName,
        )
        .filter((n: string | null) => n !== null);

      for (let i = 0; i < names.length - 1; i++) {
        expect(
          (names[i] as string).localeCompare(names[i + 1] as string),
        ).toBeLessThanOrEqual(0);
      }
    });

    await test.step('SC-API-10 — Sort by processDefinitionVersion descending', async () => {
      // Deploying mcpProcessAlpha twice yields version 2; all other processes are at version 1.
      // Sorting all START_EVENT subscriptions DESC by version must place Alpha (v2) first.
      await expect(async () => {
        const res = await request.post(
          buildUrl('/message-subscriptions/search'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {messageSubscriptionType: 'START_EVENT'},
              sort: [{field: 'processDefinitionVersion', order: 'DESC'}],
              page: {limit: 100},
            },
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/message-subscriptions/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.page.totalItems).toBeGreaterThanOrEqual(2);

        const versions: number[] = json.items
          .map(
            (it: {processDefinitionVersion: number | null}) =>
              it.processDefinitionVersion,
          )
          .filter((v: number | null) => v !== null);

        for (let i = 0; i < versions.length - 1; i++) {
          expect(versions[i]).toBeGreaterThanOrEqual(versions[i + 1]);
        }

        // mcpProcessAlpha v2 must appear first
        expect(versions[0]).toBeGreaterThanOrEqual(2);
        expect(json.items[0].processDefinitionId).toBe('mcpProcessAlpha');
      }).toPass({
        intervals: [5_000, 10_000, 15_000],
        timeout: 30_000,
      });
    });
  });
});
