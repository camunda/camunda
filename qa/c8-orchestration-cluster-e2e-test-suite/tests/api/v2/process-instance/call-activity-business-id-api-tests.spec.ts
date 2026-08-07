/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, expect, test} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {
  cancelProcessInstance,
  deploy,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';

const PROCESS_INSTANCE_ENDPOINT = '/process-instances';
const PROCESS_INSTANCE_SEARCH_ENDPOINT = '/process-instances/search';
const PARENT_RESOURCE = './resources/call_activity_business_id_parent.bpmn';
const CHILD_PROCESS_ID = 'child_business_id_process';

async function deployParent(calledElementBusinessIdAttr: string) {
  const processId = `call_activity_bizid_parent_${generateUniqueId()}`;
  await deployWithSubstitutions(PARENT_RESOURCE, {
    PARENT_PROCESS_ID_PLACEHOLDER: processId,
    CALLED_ELEMENT_BUSINESS_ID_ATTR: calledElementBusinessIdAttr,
  });
  return processId;
}

async function startParent(
  request: APIRequestContext,
  processId: string,
  businessId?: string,
) {
  const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
    headers: jsonHeaders(),
    data: {
      processDefinitionId: processId,
      ...(businessId !== undefined ? {businessId} : {}),
    },
  });
  await assertStatusCode(res, 200);
  return (await res.json()).processInstanceKey as string;
}

async function findChildInstance(
  request: APIRequestContext,
  parentProcessInstanceKey: string,
) {
  let child: Record<string, unknown> = {};
  await expect(async () => {
    const res = await request.post(buildUrl(PROCESS_INSTANCE_SEARCH_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        filter: {
          parentProcessInstanceKey,
          processDefinitionId: CHILD_PROCESS_ID,
        },
      },
    });
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.page.totalItems).toBe(1);
    child = json.items[0];
  }).toPass(defaultAssertionOptions);
  return child;
}

test.describe.serial('Call Activity Business ID Propagation API', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/child_business_id_process.bpmn']);
  });

  test('Call activity overrides the child Business ID with a literal', async ({
    request,
  }) => {
    const childBusinessId = `child-literal-${generateUniqueId()}`;
    const parentProcessId = await deployParent(
      `businessId="${childBusinessId}"`,
    );

    const parentKey = await startParent(request, parentProcessId);

    const child = await findChildInstance(request, parentKey);
    expect(child.businessId).toBe(childBusinessId);

    await cancelProcessInstance(parentKey);
  });

  test('Child inherits the parent Business ID when the call activity has no attribute', async ({
    request,
  }) => {
    const parentBusinessId = `parent-inherit-${generateUniqueId()}`;
    const parentProcessId = await deployParent('');

    const parentKey = await startParent(
      request,
      parentProcessId,
      parentBusinessId,
    );

    const child = await findChildInstance(request, parentKey);
    expect(child.businessId).toBe(parentBusinessId);

    await cancelProcessInstance(parentKey);
  });

  test('Child Business ID resolves to null when the call activity clears it', async ({
    request,
  }) => {
    const parentBusinessId = `parent-empty-${generateUniqueId()}`;
    const parentProcessId = await deployParent('businessId=""');

    const parentKey = await startParent(
      request,
      parentProcessId,
      parentBusinessId,
    );

    const child = await findChildInstance(request, parentKey);
    expect(child.businessId == null).toBe(true);

    await cancelProcessInstance(parentKey);
  });
});
