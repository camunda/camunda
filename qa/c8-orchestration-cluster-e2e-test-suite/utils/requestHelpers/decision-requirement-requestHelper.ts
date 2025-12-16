/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Serializable} from 'playwright-core/types/structs';
import {APIRequestContext, expect} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from '../http';
import {decisionDefinitionRequiredFields} from '../beans/requestBeans';
import {validateResponse} from '../../json-body-assertions';
import {deploy} from '../zeebeClient';
import {DecisionRequirementsDeployment} from '@camunda8/sdk/dist/c8/lib/C8Dto';
import { defaultAssertionOptions } from 'utils/constants';

export async function deployMammalDecisionAndStoreResponse(
  decisionRequirements: DecisionRequirementsDeployment[],
) {
  const response = await deploy(['./resources/isMammal_.dmn',]);
  expect(response.decisionRequirements.length).toBe(1);
  decisionRequirements.push(response.decisionRequirements[0] as DecisionRequirementsDeployment);
}

export async function deployTwoSimpleDecisionsAndStoreResponse(decisionRequirements: DecisionRequirementsDeployment[]){
  const response = await deploy(['./resources/simpleDecisionTable1.dmn','./resources/simpleDecisionTable2.dmn']);
  expect(response.decisionRequirements.length).toBe(2);
  for (const decisionRequirementElement of response.decisionRequirements) {
    decisionRequirements.push(decisionRequirementElement as DecisionRequirementsDeployment);
  }
}