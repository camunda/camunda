/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  expect,
  type APIRequestContext,
  type APIResponse,
} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from '../http';
import {createSingleInstance, deployWithSubstitutions} from '../zeebeClient';
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types';
import {sleep} from '../sleep';
import {defaultAssertionOptions, extendedAssertionOptions} from '../constants';
import {validateResponse} from 'json-body-assertions';
import {deleteResource} from './resource-requestHelpers';

const PROCESS_DEFINITION_SEARCH_ENDPOINT = '/process-definitions/search';

export type ProcessDefinitionState = 'ACTIVE' | 'DRAINING' | 'DELETED';

export type ProcessDefinitionItem = {
  processDefinitionKey: string;
  processDefinitionId: string;
  version: number;
  state: ProcessDefinitionState;
};

export function searchProcessDefinitions(
  request: APIRequestContext,
  body: Record<string, unknown>,
): Promise<APIResponse> {
  return request.post(buildUrl(PROCESS_DEFINITION_SEARCH_ENDPOINT), {
    headers: jsonHeaders(),
    data: body,
  });
}

export async function searchProcessDefinitionItems(
  request: APIRequestContext,
  body: Record<string, unknown>,
): Promise<ProcessDefinitionItem[]> {
  const res = await searchProcessDefinitions(request, body);
  await assertStatusCode(res, 200);
  await validateResponse(
    {path: PROCESS_DEFINITION_SEARCH_ENDPOINT, method: 'POST', status: '200'},
    res,
  );
  return ((await res.json()).items ?? []) as ProcessDefinitionItem[];
}

/**
 * Polls until the definition reports `expectedState`. `DRAINING` is only
 * observable while an instance still runs, so callers must start one before
 * deleting; `extendedAssertionOptions` suits the terminal `DELETED`, written
 * only once every partition has reported `FULLY_DELETED`.
 */
export async function expectProcessDefinitionState(
  request: APIRequestContext,
  processDefinitionKey: string,
  expectedState: ProcessDefinitionState,
  assertionOptions = defaultAssertionOptions,
): Promise<void> {
  await expect(async () => {
    const items = await searchProcessDefinitionItems(request, {
      filter: {processDefinitionKey},
    });
    expect(items).toHaveLength(1);
    expect(items[0]!.state).toBe(expectedState);
  }).toPass(assertionOptions);
}

export async function expectProcessDefinitionDeleted(
  request: APIRequestContext,
  processDefinitionKey: string,
): Promise<void> {
  await expectProcessDefinitionState(
    request,
    processDefinitionKey,
    'DELETED',
    extendedAssertionOptions,
  );
}

/**
 * Terminal state for a deletion with `deleteHistory: true`: the `DELETED`
 * record other deletions leave behind is itself purged, so waiting for
 * `DELETED` would only ever pass by catching the state before the purge.
 */
export async function expectProcessDefinitionPurged(
  request: APIRequestContext,
  processDefinitionKey: string,
): Promise<void> {
  await expect(async () => {
    const items = await searchProcessDefinitionItems(request, {
      filter: {processDefinitionKey},
    });
    expect(items).toHaveLength(0);
  }).toPass(extendedAssertionOptions);
}

/**
 * Returns the raw response — the same request answers 200, 404 and 409
 * depending on the definition's state.
 */
export function deleteProcessDefinition(
  request: APIRequestContext,
  processDefinitionKey: string,
  deleteHistory = false,
): Promise<APIResponse> {
  return deleteResource(request, processDefinitionKey, {data: {deleteHistory}});
}

/**
 * Deletes a definition with a running instance and waits until it is
 * observably `DRAINING`. Waiting is not cosmetic: deletion is distributed per
 * partition, so a request right after the 200 can still be served by a
 * partition that has not applied the new state.
 */
export async function drainProcessDefinition(
  request: APIRequestContext,
  processDefinitionKey: string,
  deleteHistory = false,
): Promise<void> {
  await assertStatusCode(
    await deleteProcessDefinition(request, processDefinitionKey, deleteHistory),
    200,
  );
  await expectProcessDefinitionState(request, processDefinitionKey, 'DRAINING');
}

/**
 * Starts an instance of a specific version, retrying while the create is
 * rejected `NOT_FOUND`.
 *
 * A deployment reaches the partitions asynchronously, so a create issued right
 * after one can land on a partition that has not applied it yet and be refused
 * as if the definition did not exist. Only that rejection is retried — a
 * `NOT_FOUND` from a definition already deleted would surface once the budget
 * runs out, and every other rejection is raised immediately.
 */
export async function createInstanceOnceDeployed(
  processDefinitionId: string,
  processDefinitionVersion: number,
  variables?: JSONDoc,
) {
  const deadline = Date.now() + 30_000;
  for (;;) {
    try {
      return await createSingleInstance(
        processDefinitionId,
        processDefinitionVersion,
        variables,
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (!message.includes('NOT_FOUND') || Date.now() > deadline) {
        throw error;
      }
      await sleep(500);
    }
  }
}

const USER_TASK_MODEL = './resources/Zeebe_User_Task_Process.bpmn';
const USER_TASK_MODEL_ID = 'Zeebe_User_Task_Process';

/**
 * Deploys the user-task model under the given process id — the workhorse of
 * the draining tests, which need a definition that parks on a user task.
 *
 * Redeploying the same id needs a `nameSuffix`: without a content change
 * Zeebe deduplicates the deployment and returns the existing version.
 */
export async function deployUserTaskProcess(
  processDefinitionId: string,
  nameSuffix = '',
) {
  const deployment = await deployWithSubstitutions(USER_TASK_MODEL, {
    [USER_TASK_MODEL_ID]: processDefinitionId,
    ...(nameSuffix
      ? {
          [`name="${processDefinitionId}"`]: `name="${processDefinitionId}${nameSuffix}"`,
        }
      : {}),
  });
  return deployment.processes[0];
}
