/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Camunda8} from '@camunda8/sdk';
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types.js';
import {sleep} from './sleep';

const c8 = new Camunda8({
  CAMUNDA_AUTH_STRATEGY: process.env.CAMUNDA_AUTH_STRATEGY as
    | 'BASIC'
    | 'OAUTH'
    | 'BEARER'
    | 'COOKIE'
    | 'NONE'
    | undefined,
  CAMUNDA_BASIC_AUTH_USERNAME: process.env.CAMUNDA_BASIC_AUTH_USERNAME,
  CAMUNDA_BASIC_AUTH_PASSWORD: process.env.CAMUNDA_BASIC_AUTH_PASSWORD,
  ZEEBE_REST_ADDRESS: process.env.ZEEBE_REST_ADDRESS,
  ZEEBE_GRPC_ADDRESS:
    process.env.ZEEBE_GRPC_ADDRESS || 'grpc://localhost:26500',
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
    const results = await zeebe.deployResourcesFromFiles(processFilePaths);
    return results;
  } catch (error) {
    console.error('Deployment failed:', error);
    throw error;
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
    const instance = await zeebe.createProcessInstance({
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
  return zeebe.createProcessInstance({
    processDefinitionId,
    processDefinitionVersion,
    variables: {...(variables ?? {})},
  });
};

const cancelProcessInstance = async (processInstanceKey: string) => {
  return zeebe.cancelProcessInstance({processInstanceKey}).catch((e) => {
    // The SDK wraps HTTPError and exposes the response status as `statusCode`;
    // older code paths used `status`. Accept either so 404s from already-
    // completed instances are silently swallowed (common cleanup scenario).
    const status = e?.statusCode ?? e?.status;
    if (status === 404) {
      return;
    }
    // Something else happened. Throw the error to surface the problem.
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
  waitForLatestProcessVersion,
  createInstances,
  generateManyVariables,
  checkUpdateOnVersion,
  createSingleInstance,
  cancelProcessInstance,
  createWorker,
};
