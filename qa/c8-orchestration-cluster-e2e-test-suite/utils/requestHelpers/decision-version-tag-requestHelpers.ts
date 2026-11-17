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
import {assertStatusCode, buildUrl, jsonHeaders} from '../http';
import {searchDecisionInstancesByProcessInstanceKey} from './decision-instance-requestHelpers';
import {searchElementInstanceByElementIdAndState} from './element-instance-requestHelpers';
import {searchIncidentByPIK} from './incident-requestHelpers';

const DMN_TEMPLATE = './resources/decideUpgradeEligibility.dmn';
const PROCESS_TEMPLATE = './resources/decideUpgradeEligibilityProcess.bpmn';
const FORM = './resources/decideUpgradeEligibilityCheck.form';

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
    processId: `dmnTagProcess_${suffix}`,
    decisionId: `dmnTagDecision_${suffix}`,
    drdId: `dmnTagDrd_${suffix}`,
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
  if (!decision) {
    throw new Error(
      `Decision ${ids.decisionId} missing from deployment response`,
    );
  }
  return {
    versionTag,
    version: decision.version,
    decisionDefinitionKey: decision.decisionDefinitionKey,
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

/** Asserts which deployed version the engine actually evaluated. */
export async function expectEvaluatedDecisionVersion(
  request: APIRequestContext,
  processInstanceKey: string,
  expected: DeployedDecisionVersion,
): Promise<void> {
  const [instance] = await searchDecisionInstancesByProcessInstanceKey(
    processInstanceKey,
    request,
  );
  expect(instance.decisionDefinitionVersion).toBe(expected.version);
  expect(instance.decisionDefinitionKey).toBe(expected.decisionDefinitionKey);
}

/**
 * The "not eligible" branch's only outcome is the rejection task, so its
 * completion is a positive witness that branch was taken.
 */
export async function expectRejectionTaskCompleted(
  request: APIRequestContext,
  processInstanceKey: string,
): Promise<void> {
  await searchElementInstanceByElementIdAndState(
    request,
    processInstanceKey,
    'Activity_sendemail',
    'COMPLETED',
  );
}

export type IncidentSummary = {
  incidentKey: string;
  errorType: string;
  errorMessage: string;
};

/** Filters for the CALLED_DECISION_ERROR incident raised by version tag resolution. */
export async function waitForVersionTagIncident(
  request: APIRequestContext,
  processInstanceKey: string,
): Promise<IncidentSummary> {
  const incidents = await searchIncidentByPIK(request, {processInstanceKey});
  const incident = incidents.find(
    (i) => i.errorType === 'CALLED_DECISION_ERROR',
  );
  if (!incident) {
    throw new Error(
      `No CALLED_DECISION_ERROR incident found for process instance ${processInstanceKey}`,
    );
  }
  return incident;
}

export async function resolveIncident(
  request: APIRequestContext,
  incidentKey: string,
): Promise<void> {
  const res = await request.post(
    buildUrl('/incidents/{incidentKey}/resolution', {incidentKey}),
    {headers: jsonHeaders()},
  );
  await assertStatusCode(res, 204);
}
