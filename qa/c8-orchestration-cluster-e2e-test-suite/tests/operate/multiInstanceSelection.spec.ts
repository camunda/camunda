/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createSingleInstance, createWorker} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {waitForIncidents} from 'utils/incidentsHelper';

type ProcessInstance = {
  processInstanceKey: string;
};

let multiInstanceProcessInstance: ProcessInstance;

const LOOP_CARDINALITY = 5;
const EXPANSIONS_COUNT = 2;

test.beforeAll(async ({request}) => {
  await deploy(['./resources/multiInstanceProcess.bpmn']);

  createWorker('multiInstanceProcessTaskA', false, {}, (job) => {
    return job.complete({i: Number(job.variables.i) + 1});
  });

  createWorker('multiInstanceProcessTaskB', true);

  multiInstanceProcessInstance = await createSingleInstance(
    'multiInstanceProcess',
    1,
    {
      i: 0,
      loopCardinality: 5,
      clients: [0, 1, 2, 3, 4],
    },
  );

  await waitForIncidents(
    request,
    multiInstanceProcessInstance.processInstanceKey,
    'taskB',
    25,
  );
});

test.describe('Multi Instance Flow Node Selection', () => {
  test.beforeEach(
    async ({page, loginPage, operateHomePage, operateProcessInstancePage}) => {
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await expect(operateHomePage.operateBanner).toBeVisible();

      await operateProcessInstancePage.gotoProcessInstancePage({
        id: multiInstanceProcessInstance.processInstanceKey,
      });
    },
  );

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('expand multi instance flow nodes in instance history', async ({
    operateProcessInstancePage,
  }) => {
    await test.step('Verify that the process instance is selected by default', async () => {
      await expect(
        operateProcessInstancePage.getSelectedTreeItemsInHistory(
          /multiInstanceProcess/,
        ),
      ).toHaveAttribute('aria-label', 'multiInstanceProcess');
    });

    await test.step('Unfold 2x Task B (Multi Instance)', async () => {
      await operateProcessInstancePage.expandTreeItemInHistory(
        /^task b \(multi instance\)$/i,
      );
      await operateProcessInstancePage.expandTreeItemInHistory(
        /^task b \(multi instance\)$/i,
      );
    });

    await test.step('Verify 10x Task B visible', async () => {
      await expect(
        operateProcessInstancePage.findTreeItemInHistory('Task B'),
      ).toHaveCount(EXPANSIONS_COUNT * LOOP_CARDINALITY);
    });
  });

  test('verify execution counts and incidents display for multi-instance flow node', async ({
    operateProcessInstancePage,
    operateDiagramPage,
  }) => {
    await test.step('Expand Task B (Multi instance) flow node', async () => {
      await operateProcessInstancePage.expandTreeItemInHistory(
        /^task b \(multi instance\)$/i,
      );
    });

    await test.step('Verify 5 child Task B instances are visible', async () => {
      await expect(
        operateProcessInstancePage.findTreeItemInHistory(/^task b$/i),
      ).toHaveCount(5);
    });

    await test.step('Enable execution count toggle', async () => {
      await operateProcessInstancePage.toggleExecutionCount();
    });

    await test.step('Verify Task B shows 25 incidents overlay', async () => {
      await expect(operateDiagramPage.getIncidentsOverlay('taskB')).toHaveText(
        '25',
      );
    });

    await test.step('Verify multi-instance timer event shows execution count of 5', async () => {
      await expect(
        operateDiagramPage.getExecutionCountOverlay('Event_06lbs4q'),
      ).toHaveText('5');
    });

    await test.step('Verify incidents banner shows 25 incidents', async () => {
      await expect(operateProcessInstancePage.incidentsBanner).toBeVisible();
      await expect(operateProcessInstancePage.incidentsBanner).toContainText(
        '25 Incidents occurred',
      );
    });

    await test.step('Click incidents banner to open incidents table', async () => {
      await operateProcessInstancePage.navigateToRootScope();
      await operateProcessInstancePage.incidentsBanner.click();
      await expect(
        operateProcessInstancePage.incidentsViewHeader,
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.incidentsViewHeader,
      ).toContainText(/Incidents\s+-\s+25 results/i);
    });

    await test.step('Verify incident table shows 25 "No retries left" errors', async () => {
      await expect(
        operateProcessInstancePage.getIncidentRow(/Job: No retries left/i),
      ).toHaveCount(25);
    });
  });

  test('select single task', async ({
    operateProcessInstancePage,
    operateDiagramPage,
  }) => {
    await test.step('Select a single Task B instance', async () => {
      await operateProcessInstancePage.expandTreeItemInHistory(
        /^task b \(multi instance\)$/i,
      );
      await operateProcessInstancePage
        .findTreeItemInHistory(/^task b$/i)
        .first()
        .click();
    });

    await test.step('Verify metadata popover shows flow node instance details', async () => {
      await expect(
        operateDiagramPage.getPopoverText(/element instance key/i),
      ).toBeVisible();
    });

    await test.step('Open incident table and verify one incident is selected', async () => {
      await operateDiagramPage.clickShowIncident();

      await expect(
        operateProcessInstancePage.getIncidentRow(/Job: No retries left/i),
      ).toHaveCount(1);
      await expect(
        operateProcessInstancePage.getSelectedIncidentRow(
          /Job: No retries left/i,
        ),
      ).toHaveCount(1);

      await expect(
        operateProcessInstancePage.incidentsViewHeader,
      ).toContainText(/Incidents\s+-\s+Filtered by "Task B"\s+-\s+1 result/i);
    });
  });
});
