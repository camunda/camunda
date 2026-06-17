/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type APIRequestContext} from '@playwright/test';
import {deployWithSubstitutions} from '../zeebeClient';
import {assertStatusCode, buildUrl, jsonHeaders} from '../http';
import {extendedAssertionOptions} from '../constants';

/**
 * Deploys N unique process definitions with IDs `pd-isLatest-${suffix}-0` …
 * `pd-isLatest-${suffix}-${count-1}`.  The first `redeployForV2Count` are
 * redeployed with a name tweak so that Zeebe registers them as version 2.
 * Returns the array of seeded process definition IDs.
 */
export async function seedUniqueProcessDefinitions(
  suffix: string,
  count: number,
  redeployForV2Count: number,
): Promise<string[]> {
  if (redeployForV2Count > count) {
    throw new Error(
      `redeployForV2Count (${redeployForV2Count}) must not exceed count (${count}).`,
    );
  }
  const ids: string[] = [];
  for (let i = 0; i < count; i++) {
    const pid = `pd-isLatest-${suffix}-${i}`;
    ids.push(pid);
    await deployWithSubstitutions(
      './resources/process_definition_tests_1.bpmn',
      {
        process_definition_tests_1: pid,
      },
    );
  }
  for (let i = 0; i < redeployForV2Count; i++) {
    const pid = ids[i]!;
    // Two-pass substitution: step 1 replaces both the `id` and `name` attributes
    // with `pid`; step 2 then changes only the `name` attribute to `${pid}-v2` so
    // the file content differs and Zeebe registers a new version for the same id.
    await deployWithSubstitutions(
      './resources/process_definition_tests_1.bpmn',
      {
        process_definition_tests_1: pid,
        [`name="${pid}"`]: `name="${pid}-v2"`,
      },
    );
  }
  return ids;
}

/**
 * Deploys N unique decision definitions with IDs `dd-isLatest-${suffix}-0` …
 * `dd-isLatest-${suffix}-${count-1}`.  All deployments share the same DRG name
 * `drs-${suffix}`, which lets tests scope queries via `decisionRequirementsName`
 * (the v2 search endpoint does not support advanced operators on
 * `decisionDefinitionId`, so a name-prefix `$like` is not available).
 * Each deployment uses a unique DRG id so Zeebe creates a separate DRG per
 * decision rather than versioning a shared one.
 *
 * The first `redeployForV2Count` decisions are redeployed with a name change so
 * Zeebe registers them as version 2.  Returns the seeded decision IDs.
 */
export async function seedUniqueDecisionDefinitions(
  suffix: string,
  count: number,
  redeployForV2Count: number,
): Promise<string[]> {
  if (redeployForV2Count > count) {
    throw new Error(
      `redeployForV2Count (${redeployForV2Count}) must not exceed count (${count}).`,
    );
  }
  const ids: string[] = [];
  const drgName = `drs-${suffix}`;
  for (let i = 0; i < count; i++) {
    const did = `dd-isLatest-${suffix}-${i}`;
    const drgId = `def-${suffix}-${i}`;
    ids.push(did);
    await deployWithSubstitutions('./resources/simpleDecisionTable1.dmn', {
      Definitions_1lja2g1: drgId,
      'name="DRD"': `name="${drgName}"`,
      Decision_f6ej9i5: did,
      SingleTableDecision: did,
    });
  }
  for (let i = 0; i < redeployForV2Count; i++) {
    const did = ids[i]!;
    const drgId = `def-${suffix}-${i}`;
    // Same DRG id and decision id, different decision name → Zeebe creates v2
    // of the DRG and v2 of the decision under the stable decision id.
    await deployWithSubstitutions('./resources/simpleDecisionTable1.dmn', {
      Definitions_1lja2g1: drgId,
      'name="DRD"': `name="${drgName}"`,
      Decision_f6ej9i5: did,
      SingleTableDecision: `${did}-v2`,
    });
  }
  return ids;
}

/**
 * Paginates forward through a search endpoint until a page returns fewer items
 * than `limit`, collecting all items across pages.
 */
export async function walkLatestVersionCursor(
  request: APIRequestContext,
  endpoint: string,
  filter: Record<string, unknown>,
  limit: number,
): Promise<unknown[]> {
  const allItems: unknown[] = [];
  let afterCursor: string | null = null;

  for (;;) {
    const pageParams: Record<string, unknown> = {limit};
    if (afterCursor !== null) {
      pageParams.after = afterCursor;
    }
    const res = await request.post(buildUrl(endpoint), {
      headers: jsonHeaders(),
      data: {filter, page: pageParams},
    });
    await assertStatusCode(res, 200);
    const body = (await res.json()) as {
      items?: unknown[];
      page?: {endCursor?: string | null};
    };
    const items = body.items ?? [];
    allItems.push(...items);
    if (items.length < limit) {
      break;
    }
    afterCursor = body.page?.endCursor ?? null;
    if (afterCursor === null) {
      break;
    }
  }

  return allItems;
}

/**
 * Polls a search endpoint with `page.limit: 1` until `page.totalItems` reaches
 * at least `expectedTotal`, absorbing ES/OS indexing lag.
 * Uses `extendedAssertionOptions` (90 s timeout).
 */
export async function waitForLatestVersionTotalItems(
  request: APIRequestContext,
  endpoint: string,
  filter: Record<string, unknown>,
  expectedTotal: number,
): Promise<void> {
  await expect(async () => {
    const res = await request.post(buildUrl(endpoint), {
      headers: jsonHeaders(),
      data: {filter, page: {limit: 1}},
    });
    await assertStatusCode(res, 200);
    const body = (await res.json()) as {page?: {totalItems?: number}};
    expect(body.page?.totalItems).toBeGreaterThanOrEqual(expectedTotal);
  }).toPass(extendedAssertionOptions);
}
