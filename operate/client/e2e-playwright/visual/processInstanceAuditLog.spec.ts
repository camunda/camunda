/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '../visual-fixtures';
import {mockResponses, runningInstance} from '../mocks/processInstance';
import {mockAuditLogs} from '../mocks/auditLog.mocks';
import {URL_API_PATTERN} from '../constants';
import {clientConfigMock} from '../mocks/clientConfig';
import {takePercySnapshot} from '../utils/percy';

const {processInstanceKey} = runningInstance.detail;

test.beforeEach(async ({context}) => {
  await context.route('**/client-config.js', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/javascript;charset=UTF-8',
      },
      body: clientConfigMock,
    }),
  );
});

test.describe('process instance audit log tab', () => {
  test('empty state', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: runningInstance.variables,
        xml: runningInstance.xml,
        auditLogs: {
          items: [],
          page: {
            totalItems: 0,
            startCursor: null,
            endCursor: null,
            hasMoreTotalItems: false,
          },
        },
      }),
    );

    await processInstancePage.gotoProcessInstanceOperationsLogPage({
      key: processInstanceKey,
    });

    await expect(
      page.getByText('No operations found for this instance'),
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
    await takePercySnapshot(page, 'Process Instance Audit Log - empty state');
  });

  test('error state', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: runningInstance.variables,
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstanceOperationsLogPage({
      key: processInstanceKey,
    });

    await expect(page.getByText('Data could not be fetched')).toBeVisible();
    await expect(page).toHaveScreenshot();
    await takePercySnapshot(page, 'Process Instance Audit Log - error state');
  });

  test('filled with data', async ({page, processInstancePage}) => {
    const piAuditLogs = {
      ...mockAuditLogs,
      items: mockAuditLogs.items.map((item) => ({
        ...item,
        processInstanceKey,
      })),
    };

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: runningInstance.variables,
        xml: runningInstance.xml,
        auditLogs: piAuditLogs,
      }),
    );

    await processInstancePage.gotoProcessInstanceOperationsLogPage({
      key: processInstanceKey,
    });

    await expect(processInstancePage.operationsLogTable).toBeVisible();
    await expect(
      processInstancePage.operationsLogTableSpinner,
    ).not.toBeVisible();

    await expect(page).toHaveScreenshot();
    await takePercySnapshot(page, 'Process Instance Audit Log - filled with data');
  });
});
