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
  return zeebe.cancelProcessInstance({processInstanceKey});
};

export {
  deploy,
  createInstances,
  generateManyVariables,
  createSingleInstance,
  cancelProcessInstance,
};
