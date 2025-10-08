/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  CREATE_NEW_GROUP,
  CREATE_NEW_MAPPING_RULE,
  CREATE_NEW_ROLE,
  CREATE_NEW_TENANT,
  CREATE_NEW_USER,
  authorizedComponentRequiredFields,
  groupRequiredFields,
  roleRequiredFields,
  tenantRequiredFields,
  userRequiredFields,
  decisionDefinitionRequiredFields,
  userTaskSearchPageResponseRequiredFields,
} from './beans/requestBeans';
import {
  assertEqualsForKeys,
  assertRequiredFields,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
  paginatedResponseFields,
} from './http';
import {expect} from '@playwright/test';
import type {APIRequestContext} from 'playwright-core';
import {defaultAssertionOptions, generateUniqueId} from './constants';
import {Serializable} from 'playwright-core/types/structs';
import {cancelProcessInstance, createInstances, deploy} from './zeebeClient';
import {DecisionDeployment} from '@camunda8/sdk/dist/c8/lib/C8Dto';
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types.js';

export async function createGroupAndStoreResponseFields(
  request: APIRequestContext,
  numberOfGroups: number,
  state: Record<string, unknown>,
  key?: string,
) {
  for (let i = 1; i <= numberOfGroups; i++) {
    const requestBody = CREATE_NEW_GROUP();
    const res = await request.post(buildUrl('/groups'), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    expect(res.status()).toBe(201);
    const json = await res.json();
    assertRequiredFields(json, groupRequiredFields);
    const arrayKey = key ? `${key}${i}` : `${i}`;
    state[`groupId${arrayKey}`] = json.groupId;
    state[`name${arrayKey}`] = json.name;
    state[`description${arrayKey}`] = json.description;
  }
}

export async function assignClientToGroup(
  request: APIRequestContext,
  numberOfClients: number,
  groupId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfClients; i++) {
    const clientId = `test-client` + generateUniqueId();
    const p = {
      groupId: groupId as string,
      clientId: clientId as string,
    };
    await expect(async () => {
      const res = await request.put(
        buildUrl('/groups/{groupId}/clients/{clientId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
      state[`clientId${groupId}${i}`] = clientId;
    }).toPass(defaultAssertionOptions);
  }
}

export async function createRoleAndStoreResponseFields(
  request: APIRequestContext,
  numberOfRoles: number,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfRoles; i++) {
    await createRole(request, state, `${i}`);
  }
}

export async function createUsersAndStoreResponseFields(
  request: APIRequestContext,
  numberOfUsers: number,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfUsers; i++) {
    await createUser(request, state, `${i}`);
  }
}

export async function createTenantAndStoreResponseFields(
  request: APIRequestContext,
  numberOfTenants: number,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfTenants; i++) {
    await createTenant(request, state, `${i}`);
  }
}

export async function deployDecisionAndStoreResponse(
  state: Record<string, unknown>,
  key: string,
  file: string,
) {
  const response = await deploy([file]);
  expect(response.decisions.length).toBe(1);
  state[`decisionDefinition${key}`] = response.decisions[0];
}

export function DECISION_DEFINITION_RESPONSE_FROM_DEPLOYMENT(
  decision: DecisionDeployment,
) {
  return {
    decisionDefinitionKey: decision.decisionDefinitionKey,
    decisionRequirementsKey: decision.decisionRequirementsKey,
    decisionRequirementsId: decision.decisionRequirementsId,
    decisionDefinitionId: decision.decisionDefinitionId,
    name: decision.name,
    version: decision.version,
    tenantId: decision.tenantId,
  };
}

export async function assignMappingToGroup(
  request: APIRequestContext,
  numberOfMappings: number,
  groupId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfMappings; i++) {
    const mappingRule = await createMappingRule(
      request,
      state,
      `${groupId}${i}`,
    );
    const p = {
      groupId: groupId as string,
      mappingRuleId: mappingRule.mappingRuleId as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/groups/{groupId}/mapping-rules/{mappingRuleId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  }
}

export async function assignRoleToGroups(
  request: APIRequestContext,
  numberOfRoles: number,
  groupId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfRoles; i++) {
    const role = await createRole(request, state, `${groupId}${i}`);
    const p = {
      groupId: groupId as string,
      roleId: role.roleId as string,
    };
    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/groups/{groupId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  }
}

export async function assignGroupsToRole(
  request: APIRequestContext,
  numberOfGroups: number,
  roleIdKey: string,
  state: Record<string, unknown>,
) {
  const roleId = state[roleIdKey] as string;
  await createGroupAndStoreResponseFields(
    request,
    numberOfGroups,
    state,
    roleId,
  );
  for (let i = 1; i <= numberOfGroups; i++) {
    const p = {
      groupId: groupIdFromState(roleIdKey, state, i) as string,
      roleId: roleId as string,
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/groups/{groupId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    expect(res.status()).toBe(204);
  }
}

export async function assignGroupsToTenant(
  request: APIRequestContext,
  numberOfGroups: number,
  tenantIdKey: string,
  state: Record<string, unknown>,
) {
  const tenantId = state[tenantIdKey] as string;
  await createGroupAndStoreResponseFields(
    request,
    numberOfGroups,
    state,
    tenantId,
  );
  for (let i = 1; i <= numberOfGroups; i++) {
    const p = {
      groupId: groupIdFromState(tenantIdKey, state, i) as string,
      tenantId: tenantId as string,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/groups/{groupId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    expect(res.status()).toBe(204);
  }
}

export async function assignRolesToMappingRules(
  request: APIRequestContext,
  numberOfRules: number,
  roleId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfRules; i++) {
    const rule = await createMappingRule(request, state, `${roleId}${i}`);
    const p = {
      roleId: roleId as string,
      mappingRuleId: rule.mappingRuleId as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/mapping-rules/{mappingRuleId}', p),
        {headers: jsonHeaders()},
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  }
}

export async function assignRoleToUsers(
  request: APIRequestContext,
  numberOfUsers: number,
  roleId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfUsers; i++) {
    const user = 'user' + generateUniqueId();
    const p = {
      userId: user,
      roleId: roleId,
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/users/{userId}', p),
      {headers: jsonHeaders()},
    );
    expect(res.status()).toBe(204);
    state[`username${roleId}${i}`] = user;
  }
}

export async function assignUsersToTenant(
  request: APIRequestContext,
  numberOfUsers: number,
  tenantId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfUsers; i++) {
    const user = 'user' + generateUniqueId();
    const p = {
      userId: user,
      tenantId: tenantId,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/users/{userId}', p),
      {headers: jsonHeaders()},
    );
    expect(res.status()).toBe(204);
    state[`username${tenantId}${i}`] = user;
  }
}

export async function assignClientsToRole(
  request: APIRequestContext,
  numberOfClients: number,
  roleId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfClients; i++) {
    const clientId = 'client' + generateUniqueId();
    const p = {
      roleId: roleId,
      clientId: clientId,
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/clients/{clientId}', p),
      {headers: jsonHeaders()},
    );
    expect(res.status()).toBe(204);
    state[`client${roleId}${i}`] = clientId;
  }
}

export async function assignClientsToTenant(
  request: APIRequestContext,
  numberOfClients: number,
  tenantId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfClients; i++) {
    const clientId = 'client' + generateUniqueId();
    const p = {
      tenantId: tenantId,
      clientId: clientId,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/clients/{clientId}', p),
      {headers: jsonHeaders()},
    );
    expect(res.status()).toBe(204);
    state[`client${tenantId}${i}`] = clientId;
  }
}

export async function assignUsersToGroup(
  request: APIRequestContext,
  numberOfUsers: number,
  groupId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfUsers; i++) {
    const user = 'test-user' + generateUniqueId();
    const stateParams: Record<string, string> = {
      groupId: groupId,
      username: user,
    };
    const res = await request.put(
      buildUrl('/groups/{groupId}/users/{username}', stateParams),
      {
        headers: jsonHeaders(),
      },
    );
    expect(res.status()).toBe(204);
    state[`username${groupId}${i}`] = user;
  }
}

export async function createMappingRulesAndStoreResponseFields(
  request: APIRequestContext,
  numberOfRules: number,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfRules; i++) {
    await createMappingRule(request, state, `${i}`);
  }
}

export async function createMappingRule(
  request: APIRequestContext,
  state?: Record<string, unknown>,
  key?: string,
) {
  const body = CREATE_NEW_MAPPING_RULE();
  await expect(async () => {
    const res = await request.post(buildUrl('/mapping-rules'), {
      headers: jsonHeaders(),
      data: body,
    });
    expect(res.status()).toBe(201);
    if (state && key) {
      const json = await res.json();
      state[`mappingRuleId${key}`] = json.mappingRuleId;
      state[`claimName${key}`] = json.claimName;
      state[`claimValue${key}`] = json.claimValue;
      state[`name${key}`] = json.name;
    }
  }).toPass(defaultAssertionOptions);
  return body;
}

export async function createRole(
  request: APIRequestContext,
  state?: Record<string, unknown>,
  key?: string,
) {
  const body = CREATE_NEW_ROLE();

  const res = await request.post(buildUrl('/roles'), {
    headers: jsonHeaders(),
    data: body,
  });

  expect(res.status()).toBe(201);
  const json = await res.json();
  assertRequiredFields(json, roleRequiredFields);
  if (state && key) {
    state[`roleId${key}`] = json.roleId;
    state[`roleName${key}`] = json.name;
    state[`roleDescription${key}`] = json.description;
  }
  return body;
}

export async function createComponentAuthorization(
  request: APIRequestContext,
  body: Serializable,
) {
  const res = await request.post(buildUrl('/authorizations'), {
    headers: jsonHeaders(),
    data: body,
  });

  expect(res.status()).toBe(201);
  const json = await res.json();
  assertRequiredFields(json, authorizedComponentRequiredFields);
}

export async function createUser(
  request: APIRequestContext,
  state?: Record<string, unknown>,
  key?: string,
) {
  const body = CREATE_NEW_USER();

  const res = await request.post(buildUrl('/users'), {
    headers: jsonHeaders(),
    data: body,
  });

  expect(res.status()).toBe(201);
  const json = await res.json();
  assertRequiredFields(json, userRequiredFields);
  if (state && key) {
    state[`username${key}`] = json.username;
    state[`name${key}`] = json.name;
    state[`email${key}`] = json.email;
    state[`password${key}`] = body.password;
  }
  return body;
}

export async function createTenant(
  request: APIRequestContext,
  state?: Record<string, unknown>,
  key?: string,
) {
  const body = CREATE_NEW_TENANT();

  const res = await request.post(buildUrl('/tenants'), {
    headers: jsonHeaders(),
    data: body,
  });

  expect(res.status()).toBe(201);
  const json = await res.json();
  assertRequiredFields(json, tenantRequiredFields);
  if (state && key) {
    state[`tenantId${key}`] = json.tenantId;
    state[`tenantName${key}`] = json.name;
    state[`tenantDescription${key}`] = json.description;
  }
  return body;
}

export function assertUserNameInResponse(json: Serializable, user: string) {
  const matchingItem = json.items.find(
    (it: {username: string}) => it.username === user,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, ['username']);
  assertEqualsForKeys(matchingItem, {username: user}, ['username']);
}

export function assertUserInResponse(
  json: Serializable,
  expectedBody: Serializable,
  user: string,
) {
  const matchingItem = json.items.find(
    (it: {username: string}) => it.username === user,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, userRequiredFields);
  assertEqualsForKeys(matchingItem, expectedBody, userRequiredFields);
}

export function assertClientsInResponse(json: Serializable, client: string) {
  const matchingItem = json.items.find(
    (it: {clientId: string}) => it.clientId === client,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, ['clientId']);
  assertEqualsForKeys(matchingItem, {clientId: client}, ['clientId']);
}

export function assertDecisionDefinitionInResponse(
  json: Serializable,
  expectedBody: Serializable,
  decisionDefinitionKey: string,
) {
  const matchingItem = json.items.find(
    (it: {decisionDefinitionKey: string}) =>
      it.decisionDefinitionKey === decisionDefinitionKey,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, decisionDefinitionRequiredFields);
  assertEqualsForKeys(
    matchingItem,
    expectedBody,
    decisionDefinitionRequiredFields,
  );
}

export function assertRoleInResponse(
  json: Serializable,
  expectedBody: Serializable,
  role: string,
) {
  const matchingItem = json.items.find(
    (it: {roleId: string}) => it.roleId === role,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, roleRequiredFields);
  assertEqualsForKeys(matchingItem, expectedBody, roleRequiredFields);
}

export function assertTenantInResponse(
  json: Serializable,
  expectedBody: Serializable,
  role: string,
) {
  const matchingItem = json.items.find(
    (it: {tenantId: string}) => it.tenantId === role,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, tenantRequiredFields);
  assertEqualsForKeys(matchingItem, expectedBody, tenantRequiredFields);
}

export function assertGroupsInResponse(
  json: Serializable,
  expectedBody: Serializable,
  group: string,
) {
  const matchingItem = json.items.find(
    (it: {groupId: string}) => it.groupId === group,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, ['groupId']);
  assertEqualsForKeys(matchingItem, expectedBody, ['groupId']);
}

export function groupIdFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`groupId${state[key]}${nth}`] as string;
}

export function groupNameFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`name${state[key]}${nth}`] as string;
}

export function roleIdValueUsingCount(
  key: string,
  state: Record<string, unknown>,
): string {
  return state[`roleId${key}`] as string;
}

export function roleIdValueUsingKey(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`roleId${state[key]}${nth}`] as string;
}

export function clientIdFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`clientId${state[key]}${nth}`] as string;
}

export function mappingRuleIdFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`mappingRuleId${state[key]}${nth}`] as string;
}

export function mappingRuleIdFromKey(
  key: string,
  state: Record<string, unknown>,
): string {
  return state[`mappingRuleId${key}`] as string;
}

export function mappingRuleNameFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`name${state[key]}${nth}`] as string;
}

export function mappingRuleClaimNameFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`claimName${state[key]}${nth}`] as string;
}

export function mappingRuleClaimValueFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`claimValue${state[key]}${nth}`] as string;
}

export function userFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`username${state[key]}${nth}`] as string;
}

export function clientFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`client${state[key]}${nth}`] as string;
}

export function roleNameFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`roleName${state[key]}${nth}`] as string;
}

export function roleDescriptionFromState(
  group: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`roleDescription${state[group]}${nth}`] as string;
}

export async function activateJobToObtainAValidJobKey(
  request: APIRequestContext,
  jobType: string,
): Promise<number> {
  const activateRes = await request.post(buildUrl('/jobs/activation'), {
    headers: jsonHeaders(),
    data: {
      type: jobType,
      timeout: 10000,
      maxJobsToActivate: 1,
    },
  });
  await assertStatusCode(activateRes, 200);
  const activateJson = await activateRes.json();
  expect(activateJson.jobs.length).toBe(1);
  return activateJson.jobs[0].jobKey;
}

export function setupProcessInstanceTests(
  processFileName: string,
  processName?: string,
  variables?: JSONDoc,
) {
  const state: Record<string, unknown> = {};

  return {
    state,
    beforeAll: async () => {
      await deploy([`./resources/${processFileName}.bpmn`]);
    },
    beforeEach: async () => {
      const processInstance = await createInstances(
        processName ? processName : processFileName,
        1,
        1,
        variables,
      );
      state['processInstanceKey'] = processInstance[0].processInstanceKey;
    },
    afterEach: async () => {
      if (!state['processCompleted']) {
        await cancelProcessInstance(state['processInstanceKey'] as string);
      }
    },
  };
}

export async function findUserTask(
  request: APIRequestContext,
  procKey: string,
  state: string,
) {
  const localState: Record<string, unknown> = {};
  await expect(async () => {
    const searchRes = await request.post(buildUrl('/user-tasks/search'), {
      headers: jsonHeaders(),
      data: {filter: {processInstanceKey: procKey}},
    });
    await assertStatusCode(searchRes, 200);
    const searchJson = await searchRes.json();

    assertRequiredFields(searchJson, paginatedResponseFields);
    assertRequiredFields(
      searchJson.page,
      userTaskSearchPageResponseRequiredFields,
    );
    expect(searchJson.page.totalItems).toBe(1);
    expect(searchJson.items.length).toBe(1);
    expect(searchJson.items[0].state).toBe(state);
    localState['userTaskKey'] = searchJson.items[0].userTaskKey;
  }).toPass(defaultAssertionOptions);
  return localState['userTaskKey'] as string;
}

export async function completeUserTask(
  request: APIRequestContext,
  userTaskKey: string,
  payload: unknown = {},
) {
  return await request.post(buildUrl(`/user-tasks/${userTaskKey}/completion`), {
    headers: jsonHeaders(),
    data: payload,
  });
}

export async function getProcessDefinitionKey(
  request: APIRequestContext,
  processDefinitionId: string,
) {
  const res = await request.post(buildUrl('/process-instances'), {
    headers: jsonHeaders(),
    data: {
      processDefinitionId: processDefinitionId,
    },
  });
  await assertStatusCode(res, 200);
  const json = await res.json();
  await cancelProcessInstance(json.processInstanceKey);
  return json.processDefinitionKey;
}

export async function searchJobKeysForProcessInstance(
  request: APIRequestContext,
  processInstanceKey: string,
) {
  const localState: Record<string, unknown> = {};
  await expect(async () => {
    const res = await request.post(buildUrl('/jobs/search'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          processInstanceKey: processInstanceKey,
        },
      },
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.page.totalItems).toBeGreaterThan(0);
    localState['jobKeys'] = json.items.map(
      (job: {jobKey: number}) => job.jobKey,
    );
  }).toPass(defaultAssertionOptions);
  return localState['jobKeys'] as Array<string>;
}

export async function failJob(
  request: APIRequestContext,
  jobKey: string,
  retries = 0,
  errorMessage = 'Simulated failure',
) {
  const failRes = await request.post(buildUrl(`/jobs/${jobKey}/failure`), {
    headers: jsonHeaders(),
    data: {
      retries: retries,
      errorMessage: errorMessage,
    },
  });
  await assertStatusCode(failRes, 204);
}

export async function throwErrorForJob(
  request: APIRequestContext,
  jobKey: string,
  errorCode: string,
  errorMessage = 'Simulated error',
) {
  const throwRes = await request.post(buildUrl(`/jobs/${jobKey}/error`), {
    headers: jsonHeaders(),
    data: {
      errorCode: errorCode,
      errorMessage: errorMessage,
    },
  });
  await assertStatusCode(throwRes, 204);
}

export async function verifyIncidentsForProcessInstance(
  request: APIRequestContext,
  processInstanceKey: string,
  expectedIncidentCount: number,
) {
  return await expect(async () => {
    const res = await request.post(
      buildUrl(`/process-instances/${processInstanceKey}/incidents/search`),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(
      json.page.totalItems,
      `Unexpected number of incident items. Found: ${JSON.stringify(json)}`,
    ).toBe(expectedIncidentCount);
  }).toPass(defaultAssertionOptions);
}
