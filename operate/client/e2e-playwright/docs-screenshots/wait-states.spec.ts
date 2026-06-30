/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';
import {expect} from '@playwright/test';

import {
  mockResponses as mockProcessDetailResponses,
  waitStateProcessInstance,
} from '../mocks/processInstance';
import {URL_API_PATTERN} from '../constants';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('wait states', () => {
  test('view wait state details for a service task', async ({
    page,
    processInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: waitStateProcessInstance.detail,
        callHierarchy: waitStateProcessInstance.callHierarchy,
        elementInstances: waitStateProcessInstance.elementInstances,
        statistics: waitStateProcessInstance.statistics,
        sequenceFlows: waitStateProcessInstance.sequenceFlows,
        variables: waitStateProcessInstance.variables,
        xml: waitStateProcessInstance.xml,
        waitStates: waitStateProcessInstance.waitStates,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: waitStateProcessInstance.detail.processInstanceKey,
    });

    // select the service task in the diagram and open its details
    await processInstancePage.diagram.getFlowNodeById('chargePayment').click();
    await page.getByRole('link', {name: 'Details', exact: true}).click();

    // the wait state status is shown for the selected service task
    await expect(page.getByTestId('waiting-status')).toBeVisible();
    await expect(
      page.getByText('Waiting for job: charge-payment'),
    ).toBeVisible();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/wait-states/operate-wait-states.png',
    });
  });
});
