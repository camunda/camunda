/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  createEligibilityIds,
  deployEligibilityForm,
  deployEligibilityProcess,
  deployTaggedDecision,
  expectEvaluatedDecisionVersion,
  expectRejectionTaskCompleted,
  findUserTask,
  resolveIncident,
  REVIEW_USER_TASK_ID,
  startEligibilityInstance,
  VIP_INPUT,
  waitForVersionTagIncident,
  expectProcessState,
} from '@requestHelpers';
import {setVariables} from '../../../../utils/zeebeClient';

/**
 * Calling a specific DMN version from a business rule task through a FEEL
 * version tag expression (product-hub#3501).
 *
 * Versions differ only in rule 1's output, so with identical inputs the branch
 * taken reveals which version ran: eligible -> review user task, not eligible
 * -> process completes.
 */
test.describe
  .parallel('Business rule task - FEEL version tag expression', () => {
  test.beforeAll(async () => {
    await deployEligibilityForm();
  });

  test('resolves the decision version tag from a process variable', async ({
    request,
  }) => {
    const ids = createEligibilityIds('varRef');
    const v1 = await deployTaggedDecision(ids, 'v1', true);
    await deployTaggedDecision(ids, 'v2', false);
    await deployEligibilityProcess(ids, '= decisionVersion');

    const processInstanceKey = await startEligibilityInstance(ids, {
      ...VIP_INPUT,
      decisionVersion: 'v1',
    });

    await expectEvaluatedDecisionVersion(request, processInstanceKey, v1);
    const userTaskKey = await findUserTask(
      request,
      processInstanceKey,
      'CREATED',
      REVIEW_USER_TASK_ID,
    );
    expect(
      userTaskKey,
      'the eligible branch must create the review user task',
    ).toBeDefined();
  });

  test('routes two instances to different decision versions without redeployment', async ({
    request,
  }) => {
    const ids = createEligibilityIds('twoInstances');
    const v1 = await deployTaggedDecision(ids, 'v1', true);
    const v2 = await deployTaggedDecision(ids, 'v2', false);
    await deployEligibilityProcess(ids, '= decisionVersion');

    const eligibleInstanceKey = await startEligibilityInstance(ids, {
      ...VIP_INPUT,
      decisionVersion: 'v1',
    });
    const notEligibleInstanceKey = await startEligibilityInstance(ids, {
      ...VIP_INPUT,
      decisionVersion: 'v2',
    });

    expect(
      eligibleInstanceKey,
      'both instances must come from the same deployed process',
    ).not.toBe(notEligibleInstanceKey);

    await test.step('instance with decisionVersion=v1 evaluates v1 and reaches the review task', async () => {
      await expectEvaluatedDecisionVersion(request, eligibleInstanceKey, v1);
      await findUserTask(
        request,
        eligibleInstanceKey,
        'CREATED',
        REVIEW_USER_TASK_ID,
      );
    });

    await test.step('instance with decisionVersion=v2 evaluates v2 and takes the rejection branch', async () => {
      await expectEvaluatedDecisionVersion(request, notEligibleInstanceKey, v2);
      await expectProcessState(request, notEligibleInstanceKey, 'COMPLETED');
      await expectRejectionTaskCompleted(request, notEligibleInstanceKey);
    });
  });

  test('resolves the decision version tag from a conditional FEEL expression', async ({
    request,
  }) => {
    const ids = createEligibilityIds('conditional');
    const v1 = await deployTaggedDecision(ids, 'v1', true);
    const v2 = await deployTaggedDecision(ids, 'v2', false);
    const v3 = await deployTaggedDecision(ids, 'v3', true);
    await deployEligibilityProcess(
      ids,
      '= if productLine = "legacy" then "v1" else if productLine = "current" then "v2" else "v3"',
    );

    const expectEligibleBranch = async (processInstanceKey: string) => {
      await findUserTask(
        request,
        processInstanceKey,
        'CREATED',
        REVIEW_USER_TASK_ID,
      );
    };
    const expectRejectionBranch = async (processInstanceKey: string) => {
      await expectProcessState(request, processInstanceKey, 'COMPLETED');
      await expectRejectionTaskCompleted(request, processInstanceKey);
    };

    const cases = [
      {productLine: 'legacy', expected: v1, expectBranch: expectEligibleBranch},
      {
        productLine: 'current',
        expected: v2,
        expectBranch: expectRejectionBranch,
      },
      {productLine: 'other', expected: v3, expectBranch: expectEligibleBranch},
    ];

    for (const {productLine, expected, expectBranch} of cases) {
      await test.step(`productLine=${productLine} evaluates ${expected.versionTag}`, async () => {
        const processInstanceKey = await startEligibilityInstance(ids, {
          ...VIP_INPUT,
          productLine,
        });

        await expectEvaluatedDecisionVersion(
          request,
          processInstanceKey,
          expected,
        );
        await expectBranch(processInstanceKey);
      });
    }
  });

  test('raises an incident naming the evaluated value when the version tag does not exist', async ({
    request,
  }) => {
    const ids = createEligibilityIds('unknownTag');
    await deployTaggedDecision(ids, 'v1', true);
    await deployEligibilityProcess(ids, '= decisionVersion');

    const processInstanceKey = await startEligibilityInstance(ids, {
      ...VIP_INPUT,
      decisionVersion: 'v99',
    });

    const incident = await waitForVersionTagIncident(
      request,
      processInstanceKey,
    );
    expect(incident.errorType).toBe('CALLED_DECISION_ERROR');
    expect(
      incident.errorMessage,
      'the evaluated version tag must be diagnosable from the incident message',
    ).toContain('v99');
    expect(incident.errorMessage).toContain(ids.decisionId);
  });

  test('raises a resolvable incident when the version tag expression evaluates to null', async ({
    request,
  }) => {
    const ids = createEligibilityIds('nullTag');
    await deployTaggedDecision(ids, 'v1', true);
    const v2 = await deployTaggedDecision(ids, 'v2', false);
    await deployEligibilityProcess(ids, '= decisionVersion');

    // decisionVersion is deliberately absent from the start variables
    const processInstanceKey = await startEligibilityInstance(ids, {
      ...VIP_INPUT,
    });

    const incident = await waitForVersionTagIncident(
      request,
      processInstanceKey,
    );
    expect(incident.errorType).toBe('CALLED_DECISION_ERROR');
    expect(incident.errorMessage).toContain('decisionVersion');
    expect(incident.errorMessage).toContain('NULL');

    await test.step('setting the variable and resolving the incident evaluates the requested version', async () => {
      await setVariables(processInstanceKey, {decisionVersion: 'v2'});
      await resolveIncident(request, incident.incidentKey);

      await expectEvaluatedDecisionVersion(request, processInstanceKey, v2);
      await expectProcessState(request, processInstanceKey, 'COMPLETED');
    });
  });
});
