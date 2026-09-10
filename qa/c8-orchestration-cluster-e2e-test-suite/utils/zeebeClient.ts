/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {readFileSync} from 'node:fs';
import {basename} from 'node:path';
import {Camunda8} from '@camunda8/sdk';
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types.js';
import {DeployResourceResponse} from '@camunda8/sdk/dist/c8/lib/C8Dto';
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

const deployWithProcessId = async (
  filePath: string,
  processId: string,
): Promise<DeployResourceResponse> => {
  let content = readFileSync(filePath, 'utf-8');
  // Replace id="..." and name="..." on the <bpmn:process> element so each
  // test run creates an isolated process definition that won't collide with
  // prior runs or interfere with Operate's auto-migration version selection.
  content = content.replace(
    /(<bpmn:process\s+id=")[^"]*("\s+name=")[^"]*/,
    `$1${processId}$2${processId}`,
  );
  const name = basename(filePath);
  try {
    return await zeebe.deployResources([{content, name}]);
  } catch (error) {
    console.error('Deployment failed:', error);
    throw error;
  }
};

const createWorker = (
  taskType: string,
  shouldFail = false,
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
    if (e.status === 404) {
      // an active process with this key was not found. It probably completed already.
      // we swallow the error, because this is a common cleanup scenario.
      return;
    }
    // Something else happened. Throw the error to surface the problem.
    throw e;
  });
};

async function searchByProcessInstanceKey(processInstanceKey: string) {
  return zeebe.searchProcessInstances({
    filter: {processInstanceKey},
    sort: [],
  });
}
async function checkUpdateOnVersion(
  targetVersion: string,
  processInstanceKey: string,
) {
  const res = await zeebe.searchProcessInstances({
    filter: {processInstanceKey},
    sort: [],
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
    if (attempt < timeoutSeconds - 1) {
      await sleep(1000);
    }
  }
  throw new Error(
    `Process definition ${processDefinitionId} version ${expectedVersion} was not indexed as latest within ${timeoutSeconds}s`,
  );
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

const setVariables = async (
  elementInstanceKey: string,
  variables: Record<string, unknown>,
  local: boolean = false,
): Promise<void> => {
  await zeebeGrpc.setVariables({elementInstanceKey, variables, local});
};

export {
  deploy,
  waitForLatestProcessVersion,
  deployWithProcessId,
  createInstances,
  generateManyVariables,
  createSingleInstance,
  cancelProcessInstance,
  searchByProcessInstanceKey,
  checkUpdateOnVersion,
  createWorker,
  deployWithSubstitutions,
  setVariables,
};
