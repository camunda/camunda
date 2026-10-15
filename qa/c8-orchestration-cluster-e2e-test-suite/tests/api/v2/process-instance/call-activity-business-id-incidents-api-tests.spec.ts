/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {readFileSync} from 'node:fs';
import {APIRequestContext, expect, test} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {
  cancelProcessInstance,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {
  defaultAssertionOptions,
  generateUniqueId,
  uniqueBusinessId,
} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {deployInlineFilesRaw} from '../../../../utils/requestHelpers';

// TestRail case ids: to be allocated on first nightly sync. The suite maps each case by its
// `<spec path>.<describe › title>` automation id (see scripts/check-automation-id-length.ts),
// so keep the titles below short and stable.

const PROCESS_INSTANCE_ENDPOINT = '/process-instances';
const PROCESS_INSTANCE_SEARCH_ENDPOINT = '/process-instances/search';
const PROCESS_INSTANCE_GET_PATH = '/process-instances/{processInstanceKey}';
const INCIDENT_SEARCH_ENDPOINT = '/incidents/search';

const PARENT_RESOURCE = './resources/call_activity_business_id_parent.bpmn';
const CHILD_RESOURCE = './resources/child_business_id_process.bpmn';
const NESTED_RESOURCE = './resources/call_activity_business_id_nested.bpmn';
const MI_RESOURCE = './resources/call_activity_business_id_multiinstance.bpmn';

// Business-id uniqueness enforcement is on in this suite
// (CAMUNDA_PROCESSINSTANCECREATION_BUSINESSIDUNIQUENESSENABLED=true), so every literal
// Business ID a case relies on is made run-unique to avoid cross-run collisions.

// Root process instances started during the run. Cleaned up guardedly in afterAll — cancelling
// a root cancels its whole call-activity subtree, so children never leak. The array is
// per-worker, which lines up with Playwright's per-worker afterAll under describe.parallel.
const startedInstanceKeys: string[] = [];

interface IncidentItem {
  errorType: string;
  errorMessage: string;
  elementId: string;
  processInstanceKey: string;
}

function escapeXmlAttr(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// Mirrors deployWithSubstitutions' string replacement, but returns the rendered content instead
// of deploying, so deploy-time rejection cases can post it and assert the raw HTTP 400.
function renderResource(
  filePath: string,
  substitutions: Record<string, string>,
): string {
  let content = readFileSync(filePath, 'utf-8');
  for (const [placeholder, replacement] of Object.entries(substitutions)) {
    if (!content.includes(placeholder)) {
      throw new Error(
        `Placeholder '${placeholder}' not found in resource file '${filePath}'`,
      );
    }
    content = content.split(placeholder).join(replacement);
  }
  return content;
}

// Deploys the parametrized child then parent with a per-case businessId attribute value and
// isolated process ids. Returns the generated parent/child process ids.
async function deployParentAndChild(
  businessIdAttr: string,
): Promise<{parentProcessId: string; childProcessId: string}> {
  const suffix = generateUniqueId();
  const parentProcessId = `ca-parent-${suffix}`;
  const childProcessId = `ca-child-${suffix}`;
  await deployWithSubstitutions(CHILD_RESOURCE, {
    __CHILD_PROCESS_ID__: childProcessId,
  });
  await deployWithSubstitutions(PARENT_RESOURCE, {
    __PARENT_PROCESS_ID__: parentProcessId,
    __CHILD_PROCESS_ID__: childProcessId,
    __BUSINESS_ID_ATTR__: escapeXmlAttr(businessIdAttr),
  });
  return {parentProcessId, childProcessId};
}

async function startProcessInstance(
  request: APIRequestContext,
  processDefinitionId: string,
  businessId?: string,
): Promise<Record<string, string>> {
  const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
    headers: jsonHeaders(),
    data: businessId
      ? {processDefinitionId, businessId}
      : {processDefinitionId},
  });
  await assertStatusCode(res, 200);
  const json = await res.json();
  startedInstanceKeys.push(json.processInstanceKey);
  return json;
}

async function getProcessInstance(
  request: APIRequestContext,
  processInstanceKey: string,
): Promise<Record<string, unknown>> {
  const res = await request.get(
    buildUrl(PROCESS_INSTANCE_GET_PATH, {processInstanceKey}),
    {headers: jsonHeaders()},
  );
  await assertStatusCode(res, 200);
  await validateResponse(
    {path: PROCESS_INSTANCE_GET_PATH, method: 'GET', status: '200'},
    res,
  );
  return res.json();
}

async function searchChildInstances(
  request: APIRequestContext,
  parentProcessInstanceKey: string,
): Promise<{page: {totalItems: number}; items: Record<string, unknown>[]}> {
  const res = await request.post(buildUrl(PROCESS_INSTANCE_SEARCH_ENDPOINT), {
    headers: jsonHeaders(),
    data: {filter: {parentProcessInstanceKey}},
  });
  await assertStatusCode(res, 200);
  await validateResponse(
    {path: PROCESS_INSTANCE_SEARCH_ENDPOINT, method: 'POST', status: '200'},
    res,
  );
  return res.json();
}

async function searchIncidents(
  request: APIRequestContext,
  processInstanceKey: string,
): Promise<{page: {totalItems: number}; items: IncidentItem[]}> {
  const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
    headers: jsonHeaders(),
    data: {filter: {processInstanceKey}},
  });
  await assertStatusCode(res, 200);
  await validateResponse(
    {path: INCIDENT_SEARCH_ENDPOINT, method: 'POST', status: '200'},
    res,
  );
  return res.json();
}

test.describe.parallel('Call Activity Business ID - FEEL & Incidents', () => {
  test.afterAll(async () => {
    await Promise.allSettled(
      startedInstanceKeys.map(async (key) => {
        try {
          await cancelProcessInstance(key);
        } catch {
          // Guarded: a single failed cancellation (e.g. already-completed instance) must not
          // prevent the remaining instances from being cleaned up.
        }
      }),
    );
  });

  // Group 1 — FEEL happy path

  test('FEEL businessId inherits parent Business ID via context variable', async ({
    request,
  }) => {
    const parentBusinessId = uniqueBusinessId('feel-inherit');
    const localState: Record<string, string> = {};

    await test.step('Deploy parent whose child businessId reads the parent Business ID', async () => {
      const {parentProcessId} = await deployParentAndChild(
        '=camunda.processInstance.businessId',
      );
      localState['parentProcessId'] = parentProcessId;
    });

    await test.step('Start parent with a Business ID', async () => {
      const json = await startProcessInstance(
        request,
        localState['parentProcessId'],
        parentBusinessId,
      );
      expect(json.businessId).toBe(parentBusinessId);
      localState['parentProcessInstanceKey'] = json.processInstanceKey;
    });

    await test.step('Child inherits the parent Business ID via FEEL', async () => {
      await expect(async () => {
        const children = await searchChildInstances(
          request,
          localState['parentProcessInstanceKey'],
        );
        expect(children.page.totalItems).toBe(1);
        expect(children.items[0].businessId).toBe(parentBusinessId);
      }).toPass(defaultAssertionOptions);
    });
  });

  // Group 2 — Runtime incident matrix (parent stays ACTIVE with an incident, no child created)

  test('FEEL resolving to a non-string raises an incident', async ({
    request,
  }) => {
    const localState: Record<string, string> = {};

    await test.step('Deploy parent whose child businessId resolves to a number', async () => {
      const {parentProcessId} = await deployParentAndChild('=42 + 8');
      localState['parentProcessId'] = parentProcessId;
    });

    await test.step('Start parent', async () => {
      const json = await startProcessInstance(
        request,
        localState['parentProcessId'],
      );
      localState['parentProcessInstanceKey'] = json.processInstanceKey;
    });

    await test.step('Parent is ACTIVE with an EXTRACT_VALUE_ERROR incident and no child', async () => {
      await expect(async () => {
        const {items} = await searchIncidents(
          request,
          localState['parentProcessInstanceKey'],
        );
        const incident = items.find(
          (i) => i.errorType === 'EXTRACT_VALUE_ERROR',
        );
        expect(
          incident,
          `expected an EXTRACT_VALUE_ERROR incident, got ${JSON.stringify(items)}`,
        ).toBeDefined();
        expect(incident!.errorMessage).toMatch(/resolve to a string/i);

        const parent = await getProcessInstance(
          request,
          localState['parentProcessInstanceKey'],
        );
        expect(parent.state).toBe('ACTIVE');
        expect(parent.hasIncident).toBe(true);

        const children = await searchChildInstances(
          request,
          localState['parentProcessInstanceKey'],
        );
        expect(children.page.totalItems).toBe(0);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('FEEL referencing a missing variable raises an incident', async ({
    request,
  }) => {
    const localState: Record<string, string> = {};

    await test.step('Deploy parent whose child businessId references a missing variable', async () => {
      const {parentProcessId} = await deployParentAndChild(
        '=nonExistentVariable',
      );
      localState['parentProcessId'] = parentProcessId;
    });

    await test.step('Start parent', async () => {
      const json = await startProcessInstance(
        request,
        localState['parentProcessId'],
      );
      localState['parentProcessInstanceKey'] = json.processInstanceKey;
    });

    await test.step('Parent has an EXTRACT_VALUE_ERROR incident about the coerced null and no child', async () => {
      await expect(async () => {
        const {items} = await searchIncidents(
          request,
          localState['parentProcessInstanceKey'],
        );
        const incident = items.find(
          (i) => i.errorType === 'EXTRACT_VALUE_ERROR',
        );
        expect(
          incident,
          `expected an EXTRACT_VALUE_ERROR incident, got ${JSON.stringify(items)}`,
        ).toBeDefined();
        expect(incident!.errorMessage).toMatch(/evaluated to null/i);
        expect(incident!.errorMessage).toMatch(/NO_VARIABLE_FOUND/);

        const children = await searchChildInstances(
          request,
          localState['parentProcessInstanceKey'],
        );
        expect(children.page.totalItems).toBe(0);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('FEEL resolving beyond the max length raises an incident', async ({
    request,
  }) => {
    const localState: Record<string, string> = {};

    await test.step('Deploy parent whose child businessId resolves to 260 characters', async () => {
      const {parentProcessId} = await deployParentAndChild(
        '=string join(for i in 1..260 return "X", "")',
      );
      localState['parentProcessId'] = parentProcessId;
    });

    await test.step('Start parent', async () => {
      const json = await startProcessInstance(
        request,
        localState['parentProcessId'],
      );
      localState['parentProcessInstanceKey'] = json.processInstanceKey;
    });

    await test.step('Parent has an EXTRACT_VALUE_ERROR incident about the length and no child', async () => {
      await expect(async () => {
        const {items} = await searchIncidents(
          request,
          localState['parentProcessInstanceKey'],
        );
        const incident = items.find(
          (i) => i.errorType === 'EXTRACT_VALUE_ERROR',
        );
        expect(
          incident,
          `expected an EXTRACT_VALUE_ERROR incident, got ${JSON.stringify(items)}`,
        ).toBeDefined();
        expect(incident!.errorMessage).toMatch(
          /exceeds the max length of 256/i,
        );

        const children = await searchChildInstances(
          request,
          localState['parentProcessInstanceKey'],
        );
        expect(children.page.totalItems).toBe(0);
      }).toPass(defaultAssertionOptions);
    });
  });

  // Group 3 — Deploy-time rejection (no instance started)

  test('Invalid FEEL syntax is rejected at deployment', async ({request}) => {
    const suffix = generateUniqueId();
    const parentContent = renderResource(PARENT_RESOURCE, {
      __PARENT_PROCESS_ID__: `ca-parent-${suffix}`,
      __CHILD_PROCESS_ID__: `ca-child-${suffix}`,
      __BUSINESS_ID_ATTR__: escapeXmlAttr('=1 +'),
    });

    const res = await deployInlineFilesRaw(request, [
      {fileName: `ca-parent-${suffix}.bpmn`, content: parentContent},
    ]);

    await assertStatusCode(res, 400);
    const body = await res.json();
    expect(body.detail).toMatch(/parse|expression/i);
  });

  test('Static literal over 256 characters is rejected at deployment', async ({
    request,
  }) => {
    const suffix = generateUniqueId();
    const parentContent = renderResource(PARENT_RESOURCE, {
      __PARENT_PROCESS_ID__: `ca-parent-${suffix}`,
      __CHILD_PROCESS_ID__: `ca-child-${suffix}`,
      __BUSINESS_ID_ATTR__: escapeXmlAttr('a'.repeat(257)),
    });

    const res = await deployInlineFilesRaw(request, [
      {fileName: `ca-parent-${suffix}.bpmn`, content: parentContent},
    ]);

    await assertStatusCode(res, 400);
    const body = await res.json();
    expect(body.detail).toMatch(/no longer than 256 characters/i);
  });

  // Group 4 — Intentional-null discard (child created, no incident)

  test('Explicit FEEL null discards the child Business ID without an incident', async ({
    request,
  }) => {
    const localState: Record<string, string> = {};

    await test.step('Deploy parent whose child businessId is explicitly =null', async () => {
      const {parentProcessId} = await deployParentAndChild('=null');
      localState['parentProcessId'] = parentProcessId;
    });

    await test.step('Start parent', async () => {
      const json = await startProcessInstance(
        request,
        localState['parentProcessId'],
      );
      localState['parentProcessInstanceKey'] = json.processInstanceKey;
    });

    await test.step('Child is created with a null Business ID', async () => {
      await expect(async () => {
        const children = await searchChildInstances(
          request,
          localState['parentProcessInstanceKey'],
        );
        expect(children.page.totalItems).toBe(1);
        expect(children.items[0]).toHaveProperty('businessId', null);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Parent has no incident', async () => {
      const {page} = await searchIncidents(
        request,
        localState['parentProcessInstanceKey'],
      );
      expect(page.totalItems).toBe(0);
    });
  });

  // Group 5 — Structural

  test('Nested call activity: grandchild inherits the middle Business ID', async ({
    request,
  }) => {
    const rootBusinessId = uniqueBusinessId('nested-root');
    const middleBusinessId = uniqueBusinessId('nested-middle');
    const suffix = generateUniqueId();
    const parentProcessId = `nested-parent-${suffix}`;
    const middleProcessId = `nested-middle-${suffix}`;
    const grandchildProcessId = `nested-grandchild-${suffix}`;
    const localState: Record<string, string> = {};

    await test.step('Deploy the parent -> middle -> grandchild chain', async () => {
      await deployWithSubstitutions(NESTED_RESOURCE, {
        __NESTED_PARENT_ID__: parentProcessId,
        __NESTED_MIDDLE_ID__: middleProcessId,
        __NESTED_GRANDCHILD_ID__: grandchildProcessId,
        __NESTED_MIDDLE_BUSINESS_ID__: escapeXmlAttr(middleBusinessId),
      });
    });

    await test.step('Start parent with the root Business ID', async () => {
      const json = await startProcessInstance(
        request,
        parentProcessId,
        rootBusinessId,
      );
      localState['parentProcessInstanceKey'] = json.processInstanceKey;
    });

    await test.step('Middle inherits the overriding literal Business ID', async () => {
      await expect(async () => {
        const children = await searchChildInstances(
          request,
          localState['parentProcessInstanceKey'],
        );
        expect(children.page.totalItems).toBe(1);
        expect(children.items[0].businessId).toBe(middleBusinessId);
        localState['middleProcessInstanceKey'] = children.items[0]
          .processInstanceKey as string;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Grandchild inherits the middle Business ID, not the root', async () => {
      await expect(async () => {
        const grandchildren = await searchChildInstances(
          request,
          localState['middleProcessInstanceKey'],
        );
        expect(grandchildren.page.totalItems).toBe(1);
        expect(grandchildren.items[0].businessId).toBe(middleBusinessId);
        expect(grandchildren.items[0].businessId).not.toBe(rootBusinessId);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Multi-instance call activity derives a Business ID per element', async ({
    request,
  }) => {
    const prefix = uniqueBusinessId('mi');
    const suffix = generateUniqueId();
    const parentProcessId = `mi-parent-${suffix}`;
    const childProcessId = `mi-child-${suffix}`;
    const expectedBusinessIds = [
      `${prefix}-10`,
      `${prefix}-20`,
      `${prefix}-30`,
    ];
    const localState: Record<string, string> = {};

    await test.step('Deploy the multi-instance parent and child', async () => {
      await deployWithSubstitutions(MI_RESOURCE, {
        __MI_PARENT_ID__: parentProcessId,
        __MI_CHILD_ID__: childProcessId,
        __MI_BUSINESS_ID_EXPR__: escapeXmlAttr(`="${prefix}-" + string(item)`),
      });
    });

    await test.step('Start parent', async () => {
      const json = await startProcessInstance(request, parentProcessId);
      localState['parentProcessInstanceKey'] = json.processInstanceKey;
    });

    await test.step('Three children get the distinct derived Business IDs', async () => {
      await expect(async () => {
        const children = await searchChildInstances(
          request,
          localState['parentProcessInstanceKey'],
        );
        expect(children.page.totalItems).toBe(3);
        const businessIds = children.items
          .map((item) => item.businessId as string)
          .sort();
        expect(businessIds).toEqual([...expectedBusinessIds].sort());
      }).toPass(defaultAssertionOptions);
    });
  });
});
