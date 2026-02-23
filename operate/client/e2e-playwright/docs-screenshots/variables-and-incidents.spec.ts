/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';
import {expect} from '@playwright/test';

import {mockResponses as mockProcessesResponses} from '../mocks/processes.mocks';
import {
  mockResponses as mockProcessDetailResponses,
  orderProcessInstance,
} from '../mocks/processInstance';
import {URL_API_PATTERN} from '../constants';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('variables and incidents', () => {
  test('view process with incident', async ({page, processesPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        processDefinitions: [
          {
            processDefinitionId: 'order-process',
            version: 1,
            name: 'order-process',
            processDefinitionKey: '2251799813686456',
            tenantId: '<default>',
            hasStartForm: false,
          },
        ],
        batchOperations: {items: [], page: {totalItems: 0}},
        processInstances: {
          items: [
            {
              processInstanceKey: '2251799813725328',
              processDefinitionKey: '2251799813688192',
              processDefinitionName: 'order-process',
              processDefinitionVersion: 2,
              startDate: '2023-09-29T07:16:22.701+0000',
              endDate: undefined,
              state: 'ACTIVE',
              processDefinitionId: 'order-process',
              tenantId: '<default>',
              hasIncident: true,
            },
          ],
          page: {totalItems: 1},
        },
        statistics: {
          items: [
            {
              elementId: 'Gateway_1qlqb7o',
              active: 0,
              canceled: 0,
              incidents: 1,
              completed: 0,
            },
          ],
        },
        processXml: orderProcessInstance.incidentState.xml,
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
        process: 'order-process',
        version: '1',
      },
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-process-view-incident.png',
    });
  });

  test('resolve an incident', async ({
    page,
    commonPage,
    processInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: orderProcessInstance.incidentState.detail,
        processInstanceDetailV2: orderProcessInstance.incidentState.detailV2,
        callHierarchy: orderProcessInstance.incidentState.callHierarchy,
        elementInstances: orderProcessInstance.incidentState.elementInstances,
        statistics: orderProcessInstance.incidentState.statistics,
        sequenceFlows: orderProcessInstance.incidentState.sequenceFlows,
        sequenceFlowsV2: orderProcessInstance.incidentState.sequenceFlowsV2,
        variables: orderProcessInstance.incidentState.variables,
        incidents: orderProcessInstance.incidentState.incidents,
        incidentsV2: orderProcessInstance.incidentState.incidentsV2,
        xml: orderProcessInstance.incidentState.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: '2251799813725328',
    });

    await expect(
      page.getByRole('button', {
        name: /view 1 incident in instance/i,
      }),
    ).toBeVisible();

    await page
      .getByRole('button', {
        name: /view 1 incident in instance/i,
      })
      .click();

    await expect(
      page.getByRole('combobox', {
        name: /filter by incident type/i,
      }),
    ).toBeInViewport();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-view-instance-incident.png',
    });

    const editVariableButton = await page.getByRole('button', {
      name: 'Edit variable orderValue',
    });

    await commonPage.addUpArrow(editVariableButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-view-instance-edit-icon.png',
    });

    await commonPage.deleteArrows();

    await editVariableButton.click();

    const editableField = page.getByRole('textbox', {
      name: /value/i,
    });
    await editableField.clear();
    await editableField.fill('99');

    const saveVariableButton = await page.getByRole('button', {
      name: 'Save variable',
    });

    await expect(saveVariableButton).toBeEnabled();

    await commonPage.addUpArrow(saveVariableButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-view-instance-save-variable-icon.png',
    });
  });

  test('retry an incident', async ({page, commonPage, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: orderProcessInstance.incidentState.detail,
        processInstanceDetailV2: orderProcessInstance.incidentState.detailV2,
        callHierarchy: orderProcessInstance.incidentState.callHierarchy,
        elementInstances: orderProcessInstance.incidentState.elementInstances,
        statistics: orderProcessInstance.incidentState.statistics,
        sequenceFlows: orderProcessInstance.incidentState.sequenceFlows,
        sequenceFlowsV2: orderProcessInstance.incidentState.sequenceFlowsV2,
        variables: orderProcessInstance.incidentResolvedState.variables,
        incidents: orderProcessInstance.incidentState.incidents,
        incidentsV2: orderProcessInstance.incidentState.incidentsV2,
        xml: orderProcessInstance.incidentState.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: '2251799813725328',
    });

    await expect(
      page.getByRole('button', {
        name: /view 1 incident in instance/i,
      }),
    ).toBeVisible();

    await page
      .getByRole('button', {
        name: /view 1 incident in instance/i,
      })
      .click();

    await expect(
      page.getByRole('combobox', {
        name: /filter by incident type/i,
      }),
    ).toBeInViewport();

    const retryIncidentButton = await page.getByRole('button', {
      name: 'Retry Incident',
    });

    await commonPage.addRightArrow(retryIncidentButton);

    const retryInstanceButton = await page.getByRole('button', {
      name: /Retry Instance/,
    });

    await commonPage.addRightArrow(retryInstanceButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-process-retry-incident.png',
    });
  });

  test('view resolved incident', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail:
          orderProcessInstance.incidentResolvedState.detail,
        processInstanceDetailV2:
          orderProcessInstance.incidentResolvedState.detailV2,
        callHierarchy: orderProcessInstance.incidentResolvedState.callHierarchy,
        elementInstances:
          orderProcessInstance.incidentResolvedState.elementInstances,
        statistics: orderProcessInstance.incidentResolvedState.statistics,
        sequenceFlows: orderProcessInstance.incidentResolvedState.sequenceFlows,
        sequenceFlowsV2:
          orderProcessInstance.incidentResolvedState.sequenceFlowsV2,
        variables: orderProcessInstance.incidentResolvedState.variables,
        xml: orderProcessInstance.incidentResolvedState.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: '2251799813725328',
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-incident-resolved.png',
    });
  });

  test('view completed instance', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: orderProcessInstance.completedState.detail,
        processInstanceDetailV2: orderProcessInstance.completedState.detailV2,
        callHierarchy: orderProcessInstance.completedState.callHierarchy,
        elementInstances: orderProcessInstance.completedState.elementInstances,
        statistics: orderProcessInstance.completedState.statistics,
        sequenceFlows: orderProcessInstance.completedState.sequenceFlows,
        sequenceFlowsV2: orderProcessInstance.completedState.sequenceFlowsV2,
        variables: orderProcessInstance.completedState.variables,
        xml: orderProcessInstance.completedState.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: '2251799813725328',
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-incident-resolved-path.png',
    });
  });
});
