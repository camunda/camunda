/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {randomUUID} from 'node:crypto';
import {expect, type APIRequestContext} from '@playwright/test';
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types.js';
import {
  createSingleInstance,
  deploy,
  deployWithSubstitutions,
} from '../zeebeClient';
import {
  assertRequiredFields,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
} from '../http';
import {defaultAssertionOptions} from '../constants';

const DMN_TEMPLATE = './resources/decideUpgradeEligibility.dmn';
const PROCESS_TEMPLATE = './resources/decideUpgradeEligibilityProcess.bpmn';
const FORM = './resources/decideUpgradeEligibilityCheck.form';

export const BUSINESS_RULE_TASK_ID = 'Activity_1wwz77k';
export const REVIEW_USER_TASK_ID = 'Activity_1851bui';

/**
 * Inputs that match rule 1 of the decision table (VIP with an engagement score
 * of at least 25). Rule 1 is the only rule whose output differs between the
 * deployed version tags, so the branch the process takes after the gateway
 * reveals which DMN version was evaluated.
 */
export const VIP_INPUT = {
  userStatus: 'VIP',
  CalculateEngagementScore: 30,
  under18: false,
  isStudent: false,
} as const;

export type EligibilityIds = {
  processId: string;
  decisionId: string;
  drdId: string;
};

export type DeployedDecisionVersion = {
  versionTag: string;
  version: number;
  decisionDefinitionKey: string;
};

/**
 * Every test deploys its own decision and process definitions. Decision
 * resolution by version tag is scoped to a decision ID, so unique IDs keep
 * parallel specs from resolving each other's tags.
 */
export function createEligibilityIds(label: string): EligibilityIds {
  const suffix = `${label}_${randomUUID().slice(0, 8)}`;
  return {
    processId: `versionTagProcess_${suffix}`,
    decisionId: `versionTagDecision_${suffix}`,
    drdId: `versionTagDrd_${suffix}`,
  };
}

function escapeXmlAttribute(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function decisionSubstitutions(
  ids: EligibilityIds,
  versionTag: string,
  vipEligible: boolean,
) {
  return {
    '{{DRD_ID}}': ids.drdId,
    '{{DECISION_ID}}': ids.decisionId,
    '{{VERSION_TAG}}': escapeXmlAttribute(versionTag),
    '{{VIP_ELIGIBLE}}': String(vipEligible),
  };
}

function processSubstitutions(ids: EligibilityIds, versionTag: string) {
  return {
    '{{PROCESS_ID}}': ids.processId,
    '{{DECISION_ID}}': ids.decisionId,
    '{{VERSION_TAG}}': escapeXmlAttribute(versionTag),
  };
}

export async function deployEligibilityForm(): Promise<void> {
  await deploy([FORM]);
}

/**
 * Deploys one version of the decision under the given version tag. `vipEligible`
 * controls the output of rule 1, which is what makes the versions behave
 * differently at runtime.
 */
export async function deployTaggedDecision(
  ids: EligibilityIds,
  versionTag: string,
  vipEligible: boolean,
): Promise<DeployedDecisionVersion> {
  const response = await deployWithSubstitutions(
    DMN_TEMPLATE,
    decisionSubstitutions(ids, versionTag, vipEligible),
  );
  const decision = response.decisions.find(
    (d) => d.decisionDefinitionId === ids.decisionId,
  );
  expect(
    decision,
    `Decision ${ids.decisionId} missing from deployment response`,
  ).toBeDefined();
  return {
    versionTag,
    version: decision!.version,
    decisionDefinitionKey: decision!.decisionDefinitionKey,
  };
}

/**
 * Deploys the process whose business rule task binds the decision by version
 * tag. `versionTagExpression` is written verbatim into the `versionTag`
 * attribute, so it can be a FEEL expression (`= decisionVersion`) or a static
 * tag.
 */
export async function deployEligibilityProcess(
  ids: EligibilityIds,
  versionTagExpression: string,
): Promise<void> {
  await deployWithSubstitutions(
    PROCESS_TEMPLATE,
    processSubstitutions(ids, versionTagExpression),
  );
}

export async function startEligibilityInstance(
  ids: EligibilityIds,
  variables: JSONDoc,
): Promise<string> {
  const instance = await createSingleInstance(ids.processId, 1, variables);
  return instance.processInstanceKey;
}

export type EvaluatedDecisionInstance = {
  decisionEvaluationInstanceKey: string;
  decisionDefinitionKey: string;
  decisionDefinitionVersion: number;
  decisionDefinitionName: string;
};

const DECISION_INSTANCE_FIELDS = [
  'decisionEvaluationInstanceKey',
  'decisionDefinitionKey',
  'decisionDefinitionVersion',
  'decisionDefinitionName',
];

/**
 * Waits until the single decision instance of the process instance is indexed
 * and returns it.
 */
export async function getEvaluatedDecisionInstance(
  request: APIRequestContext,
  processInstanceKey: string,
): Promise<EvaluatedDecisionInstance> {
  const found: Partial<EvaluatedDecisionInstance> = {};
  await expect(async () => {
    const res = await request.post(buildUrl('/decision-instances/search'), {
      headers: jsonHeaders(),
      data: {filter: {processInstanceKey}},
    });
    await assertStatusCode(res, 200);
    const body = await res.json();
    expect(body.items).toHaveLength(1);
    expect(body.items[0].state).toBe('EVALUATED');
    // Fail loudly on API shape drift rather than returning silent undefineds.
    assertRequiredFields(body.items[0], DECISION_INSTANCE_FIELDS);
    Object.assign(found, body.items[0]);
  }).toPass(defaultAssertionOptions);
  return found as EvaluatedDecisionInstance;
}

/**
 * Asserts which deployed decision version the engine actually evaluated for the
 * given process instance.
 */
export async function expectEvaluatedDecisionVersion(
  request: APIRequestContext,
  processInstanceKey: string,
  expected: DeployedDecisionVersion,
): Promise<void> {
  const decisionInstance = await getEvaluatedDecisionInstance(
    request,
    processInstanceKey,
  );
  expect(decisionInstance.decisionDefinitionVersion).toBe(expected.version);
  expect(decisionInstance.decisionDefinitionKey).toBe(
    expected.decisionDefinitionKey,
  );
}

/**
 * Asserts the process instance never created a user task, i.e. the gateway took
 * the "not eligible" branch.
 */
export async function expectNoUserTask(
  request: APIRequestContext,
  processInstanceKey: string,
): Promise<void> {
  const res = await request.post(buildUrl('/user-tasks/search'), {
    headers: jsonHeaders(),
    data: {filter: {processInstanceKey}},
  });
  await assertStatusCode(res, 200);
  const body = await res.json();
  expect(body.page.totalItems).toBe(0);
}

export type IncidentSummary = {
  incidentKey: string;
  errorType: string;
  errorMessage: string;
};

const INCIDENT_FIELDS = ['incidentKey', 'errorType', 'errorMessage'];

export async function waitForVersionTagIncident(
  request: APIRequestContext,
  processInstanceKey: string,
): Promise<IncidentSummary> {
  const found: Partial<IncidentSummary> = {};
  await expect(async () => {
    const res = await request.post(
      buildUrl(`/process-instances/${processInstanceKey}/incidents/search`),
      {headers: jsonHeaders(), data: {filter: {state: 'ACTIVE'}}},
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    expect(body.items).toHaveLength(1);
    assertRequiredFields(body.items[0], INCIDENT_FIELDS);
    Object.assign(found, body.items[0]);
  }).toPass(defaultAssertionOptions);
  return found as IncidentSummary;
}

export async function resolveIncident(
  request: APIRequestContext,
  incidentKey: string,
): Promise<void> {
  const res = await request.post(
    buildUrl(`/incidents/${incidentKey}/resolution`),
    {headers: jsonHeaders()},
  );
  await assertStatusCode(res, 204);
}
