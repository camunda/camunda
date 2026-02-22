/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Camunda8} from '@camunda8/sdk';
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types.js';

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
    if (e.status === 404) {
      // an active process with this key was not found. It probably completed already.
      // we swallow the error, because this is a common cleanup scenario.
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
  console.log(
    `Target Version ${targetVersion}, Current Version ${item?.processDefinitionVersion}`,
  );
  console.log(!!item, item?.processDefinitionVersion == targetVersion);
  return !!item && item.processDefinitionVersion == targetVersion;
}

export {
  deploy,
  createInstances,
  generateManyVariables,
  checkUpdateOnVersion,
  createSingleInstance,
  cancelProcessInstance,
  createWorker,
};
