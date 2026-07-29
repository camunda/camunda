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

/** Matches rule 1 (VIP, score >= 25) - the only rule that differs per version. */
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

/** Unique IDs per test - tag lookup is scoped to a decision ID. */
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

/** Deploys one version under the given tag; `vipEligible` sets rule 1's output. */
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

/** `versionTagExpression` goes verbatim into the BPMN `versionTag` attribute. */
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

/** Waits for the instance's single decision instance to be indexed. */
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
    assertRequiredFields(body.items[0], DECISION_INSTANCE_FIELDS);
    Object.assign(found, body.items[0]);
  }).toPass(defaultAssertionOptions);
  return found as EvaluatedDecisionInstance;
}

/** Asserts which deployed version the engine actually evaluated. */
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

/** No user task means the gateway took the "not eligible" branch. */
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
