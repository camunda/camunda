/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {APIRequestContext} from 'playwright-core';
import {randomUUID} from 'node:crypto';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  cancelProcessInstance,
  createInstances,
  deploy,
} from '../../../../utils/zeebeClient';
import {
  activateJobWithLease,
  resolveAdHocSubProcessInstanceKey,
} from '@requestHelpers';
import {validateResponse} from '../../../../json-body-assertions';

// The ad-hoc sub-process is one of the element types the engine accepts as the
// owner of an agent instance (see AgentInstanceCreateProcessor#SUPPORTED_ELEMENT_TYPES:
// AD_HOC_SUB_PROCESS, SERVICE_TASK). It also carries a zeebe:taskDefinition, so entering
// it produces an activatable job — CREATE/UPDATE must be attributed to the active job
// that produced them (jobKey + jobLeaseToken), so a real activation is required.
const PROCESS_DEFINITION_ID = 'AgentInstance_AdHocSubProcess_API_Test';
const AGENT_ELEMENT_ID = 'AdHoc_Subprocess';
const AGENT_JOB_TYPE = 'agent-instance-api-test';

// A well-formed but never-allocated key on partition 1 (single-partition test
// stack). Used for not-found assertions on both element-instance and
// agent-instance lookups.
const NON_EXISTENT_KEY = '2251799813700001';

const CREATE_ENDPOINT = '/agent-instances';
const GET_ENDPOINT = '/agent-instances/{agentInstanceKey}';
const SEARCH_ENDPOINT = '/agent-instances/search';

type AgentInstance = {
  agentInstanceKey: string;
  elementInstanceKey: string;
  processInstanceKey: string;
  jobKey: number;
  jobLeaseToken: string;
};

const state: {
  processInstanceKeysToCleanup: string[];
  minimal?: AgentInstance;
  withLimits?: AgentInstance;
  extra?: AgentInstance;
} = {processInstanceKeysToCleanup: []};

// Builds the sole history item CREATE accepts a definition through: a CONFIGURATION
// item carrying model/provider/systemPrompt (and, optionally, limits/tools). See
// AgentInstanceRequestValidator#validateConfigurationEstablishesDefinition.
function configurationHistoryItem(overrides: Record<string, unknown> = {}) {
  return {
    historyItemId: randomUUID(),
    loopIteration: 1,
    role: 'CONFIGURATION',
    content: [{contentType: 'TEXT', text: 'configuration'}],
    producedAt: new Date().toISOString(),
    model: 'gpt-4o',
    provider: 'openai',
    systemPrompt: [{contentType: 'TEXT', text: 'You are a helpful assistant.'}],
    ...overrides,
  };
}

async function createAgentInstance(
  request: APIRequestContext,
  elementInstanceKey: string,
  configurationOverrides: Record<string, unknown> = {},
): Promise<{agentInstanceKey: string; jobKey: number; jobLeaseToken: string}> {
  const {jobKey, jobLeaseToken} = await activateJobWithLease(
    request,
    AGENT_JOB_TYPE,
  );
  const res = await request.post(buildUrl(CREATE_ENDPOINT), {
    headers: jsonHeaders(),
    data: {
      elementInstanceKey,
      jobKey,
      jobLeaseToken,
      history: [configurationHistoryItem(configurationOverrides)],
    },
  });
  await assertStatusCode(res, 200);
  await validateResponse(
    {path: CREATE_ENDPOINT, method: 'POST', status: '200'},
    res,
  );
  const body = await res.json();
  expect(body.agentInstanceKey).toBeDefined();
  return {
    agentInstanceKey: body.agentInstanceKey as string,
    jobKey,
    jobLeaseToken,
  };
}

/**
 * Reads are eventually consistent (the agent instance is exported to secondary
 * storage after the CREATE/UPDATE command is acknowledged). Poll GET until the
 * instance is visible, optionally until it reaches an expected status.
 */
async function waitForAgentInstance(
  request: APIRequestContext,
  agentInstanceKey: string,
  expectedStatus?: string,
): Promise<Record<string, unknown>> {
  const result: {body?: Record<string, unknown>} = {};
  await expect(async () => {
    const res = await request.get(buildUrl(GET_ENDPOINT, {agentInstanceKey}), {
      headers: jsonHeaders(),
    });
    await assertStatusCode(res, 200);
    const body = await res.json();
    if (expectedStatus !== undefined) {
      expect(body.status).toBe(expectedStatus);
    }
    result.body = body;
  }).toPass(defaultAssertionOptions);
  return result.body!;
}

/* eslint-disable playwright/expect-expect */
test.describe.serial('Agent Instance API', () => {
  test.beforeAll(async ({request}) => {
    await test.step('Deploy ad-hoc sub-process resource', async () => {
      await deploy([
        './resources/agent_instance_ad_hoc_sub_process_api_test.bpmn',
      ]);
    });

    await test.step('Seed agent instances against active ad-hoc sub-processes', async () => {
      const instances = await createInstances(PROCESS_DEFINITION_ID, 1, 3);
      state.processInstanceKeysToCleanup = instances.map(
        (i) => i.processInstanceKey as string,
      );

      // minimal: required fields only (no limits, no tools)
      const minimalPiKey = instances[0].processInstanceKey as string;
      const minimalEiKey = await resolveAdHocSubProcessInstanceKey(
        request,
        minimalPiKey,
      );
      state.minimal = {
        processInstanceKey: minimalPiKey,
        elementInstanceKey: minimalEiKey,
        ...(await createAgentInstance(request, minimalEiKey)),
      };

      // withLimits: created with limits, later updated to THINKING with tools + metrics
      const limitsPiKey = instances[1].processInstanceKey as string;
      const limitsEiKey = await resolveAdHocSubProcessInstanceKey(
        request,
        limitsPiKey,
      );
      state.withLimits = {
        processInstanceKey: limitsPiKey,
        elementInstanceKey: limitsEiKey,
        ...(await createAgentInstance(request, limitsEiKey, {
          limits: {maxModelCalls: 10, maxToolCalls: 20, maxTokens: 5000},
        })),
      };

      // extra: a second minimal instance so multi-item searches are meaningful
      const extraPiKey = instances[2].processInstanceKey as string;
      const extraEiKey = await resolveAdHocSubProcessInstanceKey(
        request,
        extraPiKey,
      );
      state.extra = {
        processInstanceKey: extraPiKey,
        elementInstanceKey: extraEiKey,
        ...(await createAgentInstance(request, extraEiKey)),
      };
    });
  });

  test.afterAll(async () => {
    for (const key of state.processInstanceKeysToCleanup) {
      await cancelProcessInstance(key);
    }
  });

  test('Create agent instance succeeds and returns key', async ({request}) => {
    const instances = await createInstances(PROCESS_DEFINITION_ID, 1, 1);
    const piKey = instances[0].processInstanceKey as string;
    state.processInstanceKeysToCleanup.push(piKey);
    const eiKey = await resolveAdHocSubProcessInstanceKey(request, piKey);
    const {jobKey, jobLeaseToken} = await activateJobWithLease(
      request,
      AGENT_JOB_TYPE,
    );

    const res = await request.post(buildUrl(CREATE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        elementInstanceKey: eiKey,
        jobKey,
        jobLeaseToken,
        history: [
          configurationHistoryItem({
            model: 'claude-3-5-sonnet',
            provider: 'anthropic',
            systemPrompt: [
              {contentType: 'TEXT', text: 'You are a support agent.'},
            ],
          }),
        ],
      },
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {path: CREATE_ENDPOINT, method: 'POST', status: '200'},
      res,
    );
    const body = await res.json();
    expect(body.agentInstanceKey).toBeDefined();
    expect(String(body.agentInstanceKey)).toMatch(/^\d+$/);
  });

  test('Get agent instance returns required properties for a minimal instance', async ({
    request,
  }) => {
    const {agentInstanceKey, processInstanceKey} = state.minimal!;
    const body = await waitForAgentInstance(request, agentInstanceKey);

    await validateResponse(
      {path: GET_ENDPOINT, method: 'GET', status: '200'},
      // re-fetch so the validator sees a fresh response object
      await request.get(buildUrl(GET_ENDPOINT, {agentInstanceKey}), {
        headers: jsonHeaders(),
      }),
    );

    expect(String(body.agentInstanceKey)).toBe(String(agentInstanceKey));
    expect(body.elementId).toBe(AGENT_ELEMENT_ID);
    expect(String(body.processInstanceKey)).toBe(String(processInstanceKey));
    expect(body.processDefinitionId).toBe(PROCESS_DEFINITION_ID);
    expect(body.completionDate).toBeNull();

    const definition = body.definition as Record<string, unknown>;
    expect(definition.model).toBe('gpt-4o');
    expect(definition.provider).toBe('openai');
    expect(definition.systemPrompt).toBe('You are a helpful assistant.');

    const metrics = body.metrics as Record<string, number>;
    expect(metrics.inputTokens).toBe(0);
    expect(metrics.outputTokens).toBe(0);
    expect(metrics.modelCalls).toBe(0);
    expect(metrics.toolCalls).toBe(0);

    // No limits supplied on creation -> engine defaults every limit to -1.
    const limits = body.limits as Record<string, number>;
    expect(limits.maxModelCalls).toBe(-1);
    expect(limits.maxToolCalls).toBe(-1);
    expect(limits.maxTokens).toBe(-1);

    expect(body.tools).toEqual([]);
  });

  test('Update agent instance applies status, metric deltas, and tools', async ({
    request,
  }) => {
    const {agentInstanceKey, elementInstanceKey, jobKey, jobLeaseToken} =
      state.withLimits!;

    // modelCalls counts ASSISTANT items, so 3 distinct ASSISTANT items are needed to
    // land modelCalls=3; toolCalls and token metrics sum across those same items.
    // The tool list itself is only ever set via a CONFIGURATION item.
    const assistantItem = (overrides: Record<string, unknown>) => ({
      historyItemId: randomUUID(),
      role: 'ASSISTANT',
      content: [{contentType: 'TEXT', text: 'Working on it.'}],
      producedAt: new Date().toISOString(),
      metrics: {inputTokens: 50, outputTokens: 100},
      ...overrides,
    });

    const updateRes = await request.patch(
      buildUrl(GET_ENDPOINT, {agentInstanceKey}),
      {
        headers: jsonHeaders(),
        data: {
          elementInstanceKey,
          status: 'THINKING',
          jobKey,
          jobLeaseToken,
          history: [
            // Only the tool list changes here; model/provider/systemPrompt are
            // omitted to leave them as previously configured at creation.
            {
              historyItemId: randomUUID(),
              loopIteration: 1,
              role: 'CONFIGURATION',
              content: [{contentType: 'TEXT', text: 'tools configured'}],
              producedAt: new Date().toISOString(),
              tools: [
                {
                  name: 'search',
                  description: 'Search the web',
                  elementId: 'Activity_A',
                },
                {name: 'summarize', description: null, elementId: null},
              ],
            },
            assistantItem({
              loopIteration: 1,
              toolCalls: [
                {
                  toolCallId: randomUUID(),
                  toolName: 'search',
                  elementId: 'Activity_A',
                },
              ],
            }),
            assistantItem({
              loopIteration: 2,
              toolCalls: [{toolCallId: randomUUID(), toolName: 'summarize'}],
            }),
            assistantItem({loopIteration: 3}),
          ],
        },
      },
    );
    await assertStatusCode(updateRes, 204);

    const body = await waitForAgentInstance(
      request,
      agentInstanceKey,
      'THINKING',
    );

    // Metrics are applied as deltas and accumulated onto the aggregate counters.
    const metrics = body.metrics as Record<string, number>;
    expect(metrics.inputTokens).toBe(150);
    expect(metrics.outputTokens).toBe(300);
    expect(metrics.modelCalls).toBe(3);
    expect(metrics.toolCalls).toBe(2);

    // Limits were set once at creation and remain unchanged by the update.
    const limits = body.limits as Record<string, number>;
    expect(limits.maxModelCalls).toBe(10);
    expect(limits.maxToolCalls).toBe(20);
    expect(limits.maxTokens).toBe(5000);

    const tools = body.tools as Array<Record<string, unknown>>;
    expect(tools).toHaveLength(2);
    expect(tools.map((t) => t.name).sort()).toEqual(['search', 'summarize']);
  });

  test('Search agent instances returns the seeded instances', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl(SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {filter: {processDefinitionId: PROCESS_DEFINITION_ID}},
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {path: SEARCH_ENDPOINT, method: 'POST', status: '200'},
        res,
      );
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(3);
      expect(body.items.length).toBeGreaterThanOrEqual(3);
    }).toPass(defaultAssertionOptions);
  });

  test('Search agent instances by agentInstanceKey returns exactly one', async ({
    request,
  }) => {
    const {agentInstanceKey} = state.minimal!;
    await expect(async () => {
      const res = await request.post(buildUrl(SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {filter: {agentInstanceKey}},
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.items).toHaveLength(1);
      expect(String(body.items[0].agentInstanceKey)).toBe(
        String(agentInstanceKey),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search agent instances by processInstanceKey returns exactly one', async ({
    request,
  }) => {
    const {agentInstanceKey, processInstanceKey} = state.withLimits!;
    await expect(async () => {
      const res = await request.post(buildUrl(SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {filter: {processInstanceKey}},
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.items).toHaveLength(1);
      expect(String(body.items[0].agentInstanceKey)).toBe(
        String(agentInstanceKey),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search agent instances by status returns the updated instance', async ({
    request,
  }) => {
    const {agentInstanceKey} = state.withLimits!;
    // Ensure the THINKING status is indexed before asserting on the filter.
    await waitForAgentInstance(request, agentInstanceKey, 'THINKING');

    await expect(async () => {
      const res = await request.post(buildUrl(SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionId: PROCESS_DEFINITION_ID,
            status: 'THINKING',
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      const keys = body.items.map((i: {agentInstanceKey: string}) =>
        String(i.agentInstanceKey),
      );
      expect(keys).toContain(String(agentInstanceKey));
      body.items.forEach((i: {status: string}) =>
        expect(i.status).toBe('THINKING'),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search agent instances by elementId matches the ad-hoc sub-process', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl(SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionId: PROCESS_DEFINITION_ID,
            elementId: AGENT_ELEMENT_ID,
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.items.length).toBeGreaterThanOrEqual(3);
      body.items.forEach((i: {elementId: string}) =>
        expect(i.elementId).toBe(AGENT_ELEMENT_ID),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search agent instances sorted by creationDate is monotonic', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl(SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {processDefinitionId: PROCESS_DEFINITION_ID},
          sort: [{field: 'creationDate', order: 'ASC'}],
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      const dates = body.items.map((i: {creationDate: string}) =>
        new Date(i.creationDate).getTime(),
      );
      const sorted = [...dates].sort((a, b) => a - b);
      expect(dates).toEqual(sorted);
    }).toPass(defaultAssertionOptions);
  });

  test('Search agent instances honours pagination limit', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {processDefinitionId: PROCESS_DEFINITION_ID},
          page: {limit: 1},
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.items).toHaveLength(1);
      expect(body.page.totalItems).toBeGreaterThanOrEqual(3);
    }).toPass(defaultAssertionOptions);
  });

  test('Create agent instance for unknown element instance returns 404', async ({
    request,
  }) => {
    const res = await request.post(buildUrl(CREATE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        elementInstanceKey: NON_EXISTENT_KEY,
        // The element-instance lookup rejects before the job/lease is ever
        // validated, so these only need to satisfy REST-level format checks.
        jobKey: '1',
        jobLeaseToken: 'unchecked-lease',
        history: [configurationHistoryItem()],
      },
    });
    await assertNotFoundRequest(res, NON_EXISTENT_KEY);
  });

  test('Create agent instance without history returns 400', async ({
    request,
  }) => {
    const {elementInstanceKey} = state.minimal!;
    const res = await request.post(buildUrl(CREATE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {elementInstanceKey, jobKey: '1', jobLeaseToken: 'unchecked-lease'},
    });
    await assertBadRequest(res, 'No history provided', 'INVALID_ARGUMENT');
  });

  test('Get unknown agent instance returns 404', async ({request}) => {
    const res = await request.get(
      buildUrl(GET_ENDPOINT, {agentInstanceKey: NON_EXISTENT_KEY}),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(res, NON_EXISTENT_KEY);
  });

  test('Get agent instance without authentication returns 401', async ({
    request,
  }) => {
    const {agentInstanceKey} = state.minimal!;
    const res = await request.get(buildUrl(GET_ENDPOINT, {agentInstanceKey}), {
      headers: jsonHeaders(''),
    });
    await assertUnauthorizedRequest(res);
  });

  test('Search agent instances without authentication returns 401', async ({
    request,
  }) => {
    const res = await request.post(buildUrl(SEARCH_ENDPOINT), {
      headers: jsonHeaders(''),
      data: {},
    });
    await assertUnauthorizedRequest(res);
  });
});
