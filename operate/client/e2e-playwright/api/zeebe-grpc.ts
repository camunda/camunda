/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ZeebeGrpcClient} from '@camunda8/sdk/dist/zeebe';
import {
  CreateProcessInstanceResponse,
  DecisionDeployment,
  DeployResourceResponse,
  EvaluateDecisionRequest,
  IOutputVariables,
  ProcessDeployment,
  ZBWorkerTaskHandler,
} from '@camunda8/sdk/dist/zeebe/types';
import * as path from 'path';
import {config} from '../config';
import {camunda8} from './camunda8';

function getFullFilePath(filename: string) {
  return path.join(config.e2eBasePath, 'tests', 'resources', filename);
}

class ZeebeGrpcApi {
  zeebe: ZeebeGrpcClient;

  constructor() {
    this.zeebe = camunda8.getZeebeGrpcApiClient();

    this.zeebe.onReady = () => console.log('camunda8 sdk connected!');
    this.zeebe.onConnectionError = () =>
      console.log('camunda8 sdk disconnected!');
  }

  deployDecisions = (filenames: string[]) => {
    const resources = filenames.map((filename) => {
      return {
        decisionFilename: getFullFilePath(filename),
      };
    });

    return this.zeebe.deployResources(resources) as Promise<
      DeployResourceResponse<DecisionDeployment>
    >;
  };

  deployProcess = (filename: string) => {
    return this.zeebe.deployResource({
      processFilename: getFullFilePath(filename),
    });
  };

  deployProcesses = (filenames: string[]) => {
    const resources = filenames.map((filename) => {
      return {processFilename: getFullFilePath(filename)};
    });

    return this.zeebe.deployResources(resources) as Promise<
      DeployResourceResponse<ProcessDeployment>
    >;
  };

  createInstances = async (
    bpmnProcessId: string,
    version: number,
    numberOfInstances: number,
    variables?: object,
  ): Promise<CreateProcessInstanceResponse[]> => {
    const batchSize = Math.min(numberOfInstances, 50);

    const responses = await Promise.all(
      [...new Array(batchSize)].map(() =>
        this.zeebe.createProcessInstance({
          bpmnProcessId,
          version,
          variables: {...variables},
        }),
      ),
    );

    if (batchSize < 50) {
      return responses;
    }

    return [
      ...responses,
      ...(await this.createInstances(
        bpmnProcessId,
        version,
        numberOfInstances - batchSize,
        variables,
      )),
    ];
  };

  createSingleInstance = (
    bpmnProcessId: string,
    version: number,
    variables?: object,
  ) => {
    return this.zeebe.createProcessInstance({
      bpmnProcessId,
      version,
      variables: {...variables},
    });
  };

  completeTask = (
    taskType: string,
    shouldFail: boolean,
    variables?: IOutputVariables,
    taskHandler: ZBWorkerTaskHandler = (job) => {
      if (shouldFail) {
        return job.fail({errorMessage: 'task failed'});
      } else {
        return job.complete(variables ?? {});
      }
    },
    pollInterval = 300,
  ) => {
    return this.zeebe.createWorker({
      taskType,
      taskHandler,
      pollInterval,
    });
  };

  evaluateDecision = (evaluateDecisionRequest: EvaluateDecisionRequest) => {
    return this.zeebe.evaluateDecision(evaluateDecisionRequest);
  };
}

const zeebeGrpcApi = new ZeebeGrpcApi();

export {zeebeGrpcApi};
