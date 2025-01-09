/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../test-fixtures';
import {expect} from '@playwright/test';

import {mockResponses as mockProcessesResponses} from '../mocks/processes.mocks';
import {
  mockResponses as mockProcessDetailResponses,
  orderProcessInstance,
} from '../mocks/processInstance';
import {URL_PATTERN} from '../constants';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('variables and incidents', () => {
  test('view process with incident', async ({page, processesPage}) => {
    await page.route(
      URL_PATTERN,
      mockProcessesResponses({
        groupedProcesses: [
          {
            bpmnProcessId: 'order-process',
            name: null,
            permissions: [],
            processes: [
              {
                id: '2251799813686456',
                name: 'order-process',
                version: 1,
                bpmnProcessId: 'order-process',
                versionTag: null,
              },
            ],
            tenantId: '<default>',
          },
        ],
        batchOperations: [],
        processInstances: {
          processInstances: [
            {
              id: '2251799813725328',
              processId: '2251799813688192',
              processName: 'order-process',
              processVersion: 2,
              startDate: '2023-09-29T07:16:22.701+0000',
              endDate: null,
              state: 'INCIDENT',
              bpmnProcessId: 'order-process',
              hasActiveOperation: false,
              operations: [],
              parentInstanceId: null,
              rootInstanceId: null,
              callHierarchy: [],
              sortValues: [],
              permissions: [],
              tenantId: '<default>',
            },
          ],
          totalCount: 1,
        },
        statistics: [
          {
            activityId: 'Gateway_1qlqb7o',
            active: 0,
            canceled: 0,
            incidents: 1,
            completed: 0,
          },
        ],
        processXml: orderProcessInstance.incidentState.xml,
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        incidents: 'true',
        process: 'order-process',
        version: '1',
      },
      options: {waitUntil: 'networkidle'},
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
      URL_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: orderProcessInstance.incidentState.detail,
        flowNodeInstances: orderProcessInstance.incidentState.flowNodeInstances,
        statistics: orderProcessInstance.incidentState.statistics,
        sequenceFlows: orderProcessInstance.incidentState.sequenceFlows,
        variables: orderProcessInstance.incidentState.variables,
        incidents: orderProcessInstance.incidentState.incidents,
        xml: orderProcessInstance.incidentState.xml,
      }),
    );

    await processInstancePage.navigateToProcessInstance({
      id: '2251799813725328',
      options: {waitUntil: 'networkidle'},
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
        name: /filter by flow node/i,
      }),
    ).toBeInViewport();
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
      URL_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: orderProcessInstance.incidentState.detail,
        flowNodeInstances: orderProcessInstance.incidentState.flowNodeInstances,
        statistics: orderProcessInstance.incidentState.statistics,
        sequenceFlows: orderProcessInstance.incidentState.sequenceFlows,
        variables: orderProcessInstance.incidentResolvedState.variables,
        incidents: orderProcessInstance.incidentState.incidents,
        xml: orderProcessInstance.incidentState.xml,
      }),
    );

    await processInstancePage.navigateToProcessInstance({
      id: '2251799813725328',
      options: {waitUntil: 'networkidle'},
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
        name: /filter by flow node/i,
      }),
    ).toBeInViewport();
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
      URL_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail:
          orderProcessInstance.incidentResolvedState.detail,
        flowNodeInstances:
          orderProcessInstance.incidentResolvedState.flowNodeInstances,
        statistics: orderProcessInstance.incidentResolvedState.statistics,
        sequenceFlows: orderProcessInstance.incidentResolvedState.sequenceFlows,
        variables: orderProcessInstance.incidentResolvedState.variables,
        xml: orderProcessInstance.incidentResolvedState.xml,
      }),
    );

    await processInstancePage.navigateToProcessInstance({
      id: '2251799813725328',
      options: {waitUntil: 'networkidle'},
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-incident-resolved.png',
    });
  });

  test('view completed instance', async ({page, processInstancePage}) => {
    await page.route(
      URL_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: orderProcessInstance.completedState.detail,
        flowNodeInstances:
          orderProcessInstance.completedState.flowNodeInstances,
        statistics: orderProcessInstance.completedState.statistics,
        sequenceFlows: orderProcessInstance.completedState.sequenceFlows,
        variables: orderProcessInstance.completedState.variables,
        xml: orderProcessInstance.completedState.xml,
      }),
    );

    await processInstancePage.navigateToProcessInstance({
      id: '2251799813725328',
      options: {waitUntil: 'networkidle'},
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-incident-resolved-path.png',
    });
  });
});
