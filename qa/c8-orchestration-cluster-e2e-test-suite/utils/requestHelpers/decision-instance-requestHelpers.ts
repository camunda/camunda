/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, expect} from '@playwright/test';
import {createSingleInstance, deploy} from '../zeebeClient';
import {validateResponse} from '../../json-body-assertions';
import {
  assertStatusCode,
  buildUrl,
  jsonHeaders,
  assertRequiredFields,
} from 'utils/http';
import {defaultAssertionOptions} from '../constants';
import {decisionInstanceRequiredFields} from 'utils/beans/requestBeans';
import {waitForAssertion} from 'utils/waitForAssertion';

export interface DecisionInstance {
  decisionEvaluationInstanceKey: string;
  state: 'EVALUATED' | 'FAILED' | 'UNSPECIFIED' | 'UNKNOWN';
  evaluationDate: string;
  decisionDefinitionId: string;
  decisionDefinitionName: string;
  decisionDefinitionVersion: number;
  decisionDefinitionType:
    | 'DECISION_TABLE'
    | 'LITERAL_EXPRESSION'
    | 'UNSPECIFIED'
    | 'UNKNOWN';
  result: string;
  tenantId: string;
  decisionEvaluationKey: string;
  processDefinitionKey: string;
  processInstanceKey: string;
  decisionDefinitionKey: string;
  elementInstanceKey: string;
  rootDecisionDefinitionKey: string;
}

export async function searchDecisionInstancesByProcessInstanceKey(
  processInstanceKey: string,
  request: APIRequestContext,
) {
  const foundDecisionInstances: DecisionInstance[] = [];

  await waitForAssertion({
    assertion: async () => {
      foundDecisionInstances.length = 0;
      await expect(async () => {
        const res = await request.post(buildUrl('/decision-instances/search'), {
          headers: jsonHeaders(),
          data: {
            page: {
              from: 0,
              limit: 10,
            },
            filter: {
              processInstanceKey: processInstanceKey,
            },
          },
        });

        await assertStatusCode(res, 200);
        // this assertion is commented as response shape isn't correct yet. As soon as it's fixed, uncomment it.
        // await validateResponse(
        //   {
        //     path: '/decision-instances/search',
        //     method: 'POST',
        //     status: '200',
        //   },
        //   res,
        // );

        const body = await res.json();
        body.items.forEach((item: Record<string, unknown>) => {
          expect(item.state).toEqual('EVALUATED');
        });
        expect(body.items.length).toBeGreaterThan(0);
        for (const element of body.items) {
          assertRequiredFields(element, decisionInstanceRequiredFields);
          foundDecisionInstances.push(element as DecisionInstance);
        }
      }).toPass(defaultAssertionOptions);
    },
    onFailure: async () => {},
    maxRetries: 10,
  });
  return foundDecisionInstances;
}

export async function createMammalProcessInstanceAndDeployMammalDecision(
  request: APIRequestContext,
) {
  await deploy([
    './resources/mammalAnimalProcess.bpmn',
    './resources/isMammal_.dmn',
  ]);
  const instance = await createSingleInstance('mammalAnimalProcess', 1, {
    hasHairOrFur: true,
    warmBlooded: true,
    givesMilk: true,
  });
  const decisions: DecisionInstance[] =
    await searchDecisionInstancesByProcessInstanceKey(
      instance.processInstanceKey,
      request,
    );
  return {
    decisions,
    instance,
  };
}
