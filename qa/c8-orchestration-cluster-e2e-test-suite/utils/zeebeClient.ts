/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Camunda8} from '@camunda8/sdk';
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types';

// REST client – uses BASIC auth from env
const c8Rest = new Camunda8({
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
});

// gRPC client – no auth, no TLS.
// CAMUNDA_AUTH_STRATEGY is set explicitly to NONE to prevent
// the SDK from picking up CAMUNDA_AUTH_STRATEGY=BASIC from the env.
// In SDK >=8.7, the grpc:// scheme in ZEEBE_GRPC_ADDRESS signals an insecure
// (non-TLS) connection. grpcs:// signals TLS. ZEEBE_INSECURE_CONNECTION is
// deprecated and ignored when ZEEBE_GRPC_ADDRESS is set.
const rawGrpcAddress =
  process.env.ZEEBE_GRPC_ADDRESS ?? 'grpc://localhost:26500';
const c8Grpc = new Camunda8({
  CAMUNDA_OAUTH_DISABLED: true,
  CAMUNDA_AUTH_STRATEGY: 'NONE',
  // Ensure the address always has the grpc:// scheme so the SDK treats it as insecure.
  ZEEBE_GRPC_ADDRESS: rawGrpcAddress.startsWith('grpcs://')
    ? rawGrpcAddress
    : `grpc://${rawGrpcAddress.replace(/^grpcs?:\/\//, '')}`,
});

const zeebe = c8Rest.getCamundaRestClient();
const zeebeGrpc = c8Grpc.getZeebeGrpcApiClient();
zeebeGrpc.onConnectionError = () =>
  console.error(
    '[zeebeGrpc] gRPC connection error – check ZEEBE_GRPC_ADDRESS and TLS settings.',
  );

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
    pollInterval: 300,
    ...(timeout ? {timeout} : {}),
  });
};

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
  const result = await zeebe.createProcessInstance({
    processDefinitionId,
    variables: variables ?? {},
  });
  return result;
};

const publishMessage = async (messageName: string, correlationKey: string) => {
  try {
    await zeebe.publishMessage({
      name: messageName,
      correlationKey,
    });
  } catch (error) {
    console.error('Message publishing failed:', error);
    throw error;
  }
};

const generateManyVariables = () => {
  let variables: Record<string, string> = {};
  const alphabet = 'abcdefghijklmnopqrstuvwxyz'.split('');

  alphabet.forEach((letter1) => {
    alphabet.forEach((letter2) => {
      variables[`${letter1}${letter2}`] = `${letter1}${letter2}`;
    });
  });

  return variables;
};

const cancelProcessInstance = async (processInstanceKey: string) => {
  return zeebe.cancelProcessInstance({processInstanceKey});
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
  console.log(
    `Target Version ${targetVersion}, Current Version ${item?.processDefinitionVersion}`,
  );
  console.log(!!item, item?.processDefinitionVersion == targetVersion);
  return !!item && item.processDefinitionVersion == targetVersion;
}

export {
  deploy,
  createInstances,
  createSingleInstance,
  createWorker,
  publishMessage,
  generateManyVariables,
  cancelProcessInstance,
  searchByProcessInstanceKey,
  checkUpdateOnVersion,
};
