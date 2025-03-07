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
  CAMUNDA_AUTH_STRATEGY: 'BASIC',
  CAMUNDA_BASIC_AUTH_USERNAME: 'demo',
  CAMUNDA_BASIC_AUTH_PASSWORD: 'demo',
  CAMUNDA_OAUTH_DISABLED: true,
  ZEEBE_REST_ADDRESS: 'http://localhost:8089',
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

export {deploy, createInstances};
