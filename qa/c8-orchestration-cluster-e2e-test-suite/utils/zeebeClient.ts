/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Camunda8} from '@camunda8/sdk';
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types';

const c8 = new Camunda8({
  CAMUNDA_AUTH_STRATEGY: process.env.CAMUNDA_AUTH_STRATEGY,
  CAMUNDA_BASIC_AUTH_USERNAME: process.env.CAMUNDA_BASIC_AUTH_USERNAME,
  CAMUNDA_BASIC_AUTH_PASSWORD: process.env.CAMUNDA_BASIC_AUTH_PASSWORD,
  ZEEBE_REST_ADDRESS: process.env.ZEEBE_REST_ADDRESS,
});
const zeebe = c8.getCamundaRestClient();
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
  version: number,
  numberOfInstances: number,
  variables?: JSONDoc,
) => {
  for (let i = 0; i < numberOfInstances; i++) {
    await zeebe.createProcessInstance({
      processDefinitionId,
      version,
      variables: variables ?? {}, // Ensure it's never undefined
    });
  }
};

const createSingleInstance = async (
  processDefinitionId: string,
  version: number,
  variables?: JSONDoc,
) => {
  const result = await zeebe.createProcessInstance({
    processDefinitionId,
    version,
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
  publishMessage,
  generateManyVariables,
  checkUpdateOnVersion,
};
