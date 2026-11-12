/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {readFileSync} from 'node:fs';
import {basename} from 'node:path';
import {Camunda8, HTTPError} from '@camunda8/sdk';
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types.js';
import {expect} from '@playwright/test';
import {sleep} from './sleep';

const c8 = new Camunda8({
  CAMUNDA_AUTH_STRATEGY: process.env.CAMUNDA_AUTH_STRATEGY as
    'BASIC' | 'OAUTH' | 'BEARER' | 'COOKIE' | 'NONE' | undefined,
  CAMUNDA_BASIC_AUTH_USERNAME: process.env.CAMUNDA_BASIC_AUTH_USERNAME,
  CAMUNDA_BASIC_AUTH_PASSWORD: process.env.CAMUNDA_BASIC_AUTH_PASSWORD,
  ZEEBE_REST_ADDRESS: process.env.ZEEBE_REST_ADDRESS,
  ZEEBE_GRPC_ADDRESS:
    process.env.ZEEBE_GRPC_ADDRESS || 'grpc://localhost:26500',
  zeebeGrpcSettings: {
    // Zeebe server rejects keepalive pings sent without active RPC calls
    // (permitKeepAliveWithoutCalls=false). Setting this to 0 aligns the client
    // with the server's expectation and prevents ENHANCE_YOUR_CALM / excess pings errors.
    GRPC_KEEPALIVE_PERMIT_WITHOUT_CALLS: 0,
  },
});

function generateManyVariables(): Record<string, string> {
  const variables: Record<string, string> = {};
  const alphabet = 'abcdefghijklmnopqrstuvwxyz'.split('');
  alphabet.forEach((letter1) => {
    alphabet.forEach((letter2) => {
      variables[`${letter1}${letter2}`] = `${letter1}${letter2}`;
    });
  });
  return variables;
}

const zeebe = c8.getCamundaRestClient();
const zeebeGrpc = c8.getZeebeGrpcApiClient();
const deploy = async (processFilePaths: string[]) => {
  try {
    const resources = processFilePaths.map((filePath) => ({
      content: readFileSync(filePath, 'utf-8'),
      name: basename(filePath),
    }));
    const results = await zeebe.deployResources(resources);
    return results;
  } catch (error) {
    console.error('Deployment failed:', error);
    throw error;
  }
};

const deployWithSubstitutions = async (
  filePath: string,
  substitutions: Record<string, string>,
) => {
  let content = readFileSync(filePath, 'utf-8');
  for (const [placeholder, replacement] of Object.entries(substitutions)) {
    if (!content.includes(placeholder)) {
      throw new Error(
        `Placeholder '${placeholder}' not found in resource file '${filePath}'`,
      );
    }
    content = content.split(placeholder).join(replacement);
  }
  const name = basename(filePath);
  try {
    return await zeebe.deployResources([{content, name}]);
  } catch (error) {
    console.error('Deployment failed:', error);
    throw error;
  }
};

/**
 * Creates a process instance, retrying while the create is rejected
 * `NOT_FOUND`.
 *
 * A deployment reaches the partitions asynchronously, so a create issued right
 * after one can land on a partition that has not applied it yet. Retrying only
 * that rejection lets callers create instances straight after `deploy()`
 * instead of sleeping first; every other rejection is raised immediately.
 */
const createProcessInstanceOnceDeployed = async (
  request: Parameters<typeof zeebe.createProcessInstance>[0],
  timeout: number = 30_000,
) => {
  const deadline = Date.now() + timeout;
  for (;;) {
    try {
      return await zeebe.createProcessInstance(request);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (!message.includes('NOT_FOUND') || Date.now() > deadline) {
        throw error;
      }
      await sleep(500);
    }
  }
};

const createInstances = async (
  processDefinitionId: string,
  processDefinitionVersion: number,
  numberOfInstances: number,
  variables?: JSONDoc,
) => {
  const instances = [];
  for (let i = 0; i < numberOfInstances; i++) {
    const instance = await createProcessInstanceOnceDeployed({
      processDefinitionId,
      processDefinitionVersion,
      variables: variables ?? {},
    });
    instances.push(instance);
  }
  return instances;
};

const createSingleInstance = async (
  processDefinitionId: string,
  processDefinitionVersion: number,
  variables?: JSONDoc,
) => {
  return createProcessInstanceOnceDeployed({
    processDefinitionId,
    processDefinitionVersion,
    variables: {...(variables ?? {})},
  });
};

// Defaults to swallowing 404 -- the existing behaviour every other caller in
// this suite already relies on for teardown. Pass ignoreNotFound: false when
// the cancellation itself drives the assertion (e.g. finalizing a drain), so a
// 404 there surfaces instead of letting the next assertion pass vacuously.
// Teardown can also tolerate an already-pending cancellation, but other
// INVALID_STATE conflicts must still surface.
const cancelProcessInstance = async (
  processInstanceKey: string,
  {
    ignoreNotFound = true,
    ignorePendingCancellation = false,
  }: {ignoreNotFound?: boolean; ignorePendingCancellation?: boolean} = {},
) => {
  return zeebe.cancelProcessInstance({processInstanceKey}).catch((e) => {
    const status = e?.status ?? e?.code ?? e?.response?.status;
    if (ignoreNotFound && (status === 404 || status === 'NOT_FOUND')) {
      return;
    }
    if (
      ignorePendingCancellation &&
      e instanceof HTTPError &&
      e.status === 409 &&
      e.title === 'INVALID_STATE' &&
      typeof e.detail === 'string' &&
      e.detail.includes('a cancel request is already in progress')
    ) {
      return;
    }
    throw e;
  });
};

const createWorker = (
  taskType: string,
  shouldFail: boolean = false,
  variables: JSONDoc = {},
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  handler?: (job: any) => any,
  timeout?: number,
) => {
  return zeebeGrpc.createWorker({
    taskType,
    taskHandler:
      handler ||
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      ((job: any) => {
        if (shouldFail) {
          return job.fail('Task failed for testing purposes');
        }
        return job.complete(variables);
      }),
    ...(timeout ? {timeout} : {}),
  });
};

const setVariables = async (
  elementInstanceKey: string,
  variables: Record<string, unknown>,
  local: boolean = false,
): Promise<void> => {
  await zeebeGrpc.setVariables({elementInstanceKey, variables, local});
};

/**
 * Waits until every given process instance is visible in secondary storage,
 * so UI pages and search APIs opened afterwards can find them. Returns as
 * soon as they are indexed instead of sleeping for a worst-case delay.
 */
async function waitForProcessInstancesIndexed(
  processInstanceKeys: Array<string | number>,
  timeout: number = 60_000,
) {
  const pending = new Set(processInstanceKeys.map(String));
  await expect
    .poll(
      async () => {
        for (const key of [...pending]) {
          const res = await zeebe.searchProcessInstances({
            filter: {processInstanceKey: key},
          });
          if (!res?.items?.length) {
            break;
          }
          pending.delete(key);
        }
        return [...pending];
      },
      {
        message: 'process instances not yet indexed',
        intervals: [500, 1_000],
        timeout,
      },
    )
    .toEqual([]);
}

/**
 * Waits until the process instance reaches the given state in secondary
 * storage, e.g. until a worker has driven it to completion.
 */
async function waitForProcessInstanceState(
  processInstanceKey: string | number,
  state: 'ACTIVE' | 'COMPLETED' | 'TERMINATED',
  timeout: number = 60_000,
) {
  await expect
    .poll(
      async () => {
        const res = await zeebe.searchProcessInstances({
          filter: {processInstanceKey: String(processInstanceKey)},
        });
        return res?.items?.[0]?.state;
      },
      {intervals: [500, 1_000], timeout},
    )
    .toBe(state);
}

/**
 * Waits until at least one element instance matching the filter exists, e.g.
 * until a multi-instance body has been activated.
 */
async function waitForElementInstance(
  filter: Parameters<typeof zeebe.searchElementInstances>[0]['filter'],
  timeout: number = 60_000,
) {
  await expect
    .poll(
      async () => {
        const res = await zeebe.searchElementInstances({filter});
        return res?.items?.length ?? 0;
      },
      {intervals: [500, 1_000], timeout},
    )
    .toBeGreaterThan(0);
}

/**
 * Waits until a process-instance variable holds the given (JSON-serialised)
 * value in secondary storage, e.g. after editing it in Operate.
 */
async function waitForVariableValue(
  processInstanceKey: string | number,
  name: string,
  value: string,
  timeout: number = 60_000,
) {
  await expect
    .poll(
      async () => {
        const res = await zeebe.searchVariables({
          filter: {processInstanceKey: String(processInstanceKey), name},
        });
        return res?.items?.[0]?.value;
      },
      {intervals: [500, 1_000], timeout},
    )
    .toBe(value);
}

async function checkUpdateOnVersion(
  targetVersion: string,
  processInstanceKey: string,
) {
  const res = await zeebe.searchProcessInstances({
    filter: {processInstanceKey},
  });
  const item = res?.items?.[0];
  return !!item && item.processDefinitionVersion == targetVersion;
}

/**
 * Waits until the given process definition version is indexed as the latest,
 * so UI pages relying on the search API (e.g. Tasklist's Processes tab) are
 * guaranteed to reflect it.
 */
const waitForLatestProcessVersion = async (
  processDefinitionId: string,
  expectedVersion: number,
  timeoutSeconds: number = 30,
) => {
  for (let attempt = 0; attempt < timeoutSeconds; attempt++) {
    const response = await zeebe.searchProcessDefinitions({
      // isLatestVersion mirrors the query Tasklist's Processes tab issues;
      // the SDK filter type does not expose the flag yet.
      filter: {processDefinitionId, isLatestVersion: true} as Parameters<
        typeof zeebe.searchProcessDefinitions
      >[0]['filter'] & {isLatestVersion: boolean},
    });
    if (response.items?.[0]?.version === expectedVersion) {
      return;
    }
    await sleep(1000);
  }
  throw new Error(
    `Process definition ${processDefinitionId} version ${expectedVersion} was not indexed as latest within ${timeoutSeconds}s`,
  );
};

export {
  deploy,
  deployWithSubstitutions,
  waitForLatestProcessVersion,
  waitForProcessInstancesIndexed,
  waitForProcessInstanceState,
  waitForElementInstance,
  waitForVariableValue,
  createInstances,
  generateManyVariables,
  checkUpdateOnVersion,
  createSingleInstance,
  cancelProcessInstance,
  createWorker,
  setVariables,
};
