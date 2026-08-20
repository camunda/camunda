/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  CamundaClient,
  DeploymentProcessResult,
} from '@camunda8/orchestration-cluster-api';
import {resolve} from 'node:path';

function resource(name: string): string {
  return resolve(import.meta.dirname, `./resources/${name}`);
}

function deployResources(camunda: CamundaClient, resourceFileNames: string[]) {
  return camunda.deployResourcesFromFiles(resourceFileNames.map(resource));
}

async function deployProcess(
  camunda: CamundaClient,
  bpmnFileName: string,
  additionalResources: string[] = [],
): Promise<DeploymentProcessResult> {
  const result = await deployResources(camunda, [
    bpmnFileName,
    ...additionalResources,
  ]);
  const processResource = result.processes.find(
    (p) => p.resourceName === bpmnFileName,
  );
  if (!processResource) {
    throw new Error(
      `Process resource with name ${bpmnFileName} not found in deployment result`,
    );
  }

  return processResource;
}

export {deployProcess, deployResources};
