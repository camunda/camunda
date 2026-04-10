/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createInstances, createSingleInstance} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {DATE_REGEX} from 'utils/constants';
import {sleep} from 'utils/sleep';
import {waitForAssertion} from '../../utils/waitForAssertion';

type ProcessInstance = {
  processInstanceKey: string;
};

let instanceWithIncidentToResolve: ProcessInstance;
let instanceWithIncidentToCancel: ProcessInstance;
let collapsedSubProcessInstance: ProcessInstance;
let executionCountProcessInstance: ProcessInstance;

test.beforeAll(async () => {
  await deploy([
    './resources/processWithAnIncident.bpmn',
    './resources/processWithMultiIncidents.bpmn',
    './resources/collapsedSubprocess.bpmn',
    './resources/executionCountProcess.bpmn',
  ]);

  instanceWithIncidentToCancel = await createSingleInstance(
    'processWithAnIncident',
    1,
  );

  instanceWithIncidentToResolve = await createSingleInstance(
    'processWithMultiIncidents',
    1,
    {goUp: 3},
  );

  collapsedSubProcessInstance = await createSingleInstance(
    'collapsedSubProcess',
    1,
  );

  executionCountProcessInstance = await createSingleInstance(
    'executionCountProcess',
    1,
  );

  await sleep(2000);
});

test.describe('Process Instance', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Resolve an incident', async ({page, operateProcessInstancePage}) => {
    await test.step('Navigate to process instance with incident', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: instanceWithIncidentToResolve.processInstanceKey,
      });
      await sleep(1000);
    });

    await test.step('Verify incidents tab is visible', async () => {
      await expect(operateProcessInstancePage.incidentsTab).toBeVisible();
    });

    await test.step('Open incidents tab', async () => {
      await operateProcessInstancePage.clickIncidentsTab();
      await expect(operateProcessInstancePage.incidentTypeFilter).toBeVisible();
      await operateProcessInstancePage.verifyIncidentCount(2);
    });

    await test.step('Edit goUp variable', async () => {
      await operateProcessInstancePage.clickVariablesTab();
      await operateProcessInstancePage.editVariableButtonInList.click();

      await operateProcessInstancePage.editVariableValueField.clear();
      await operateProcessInstancePage.editVariableValueField.type('20');

      await expect(operateProcessInstancePage.saveVariableButton).toBeEnabled();
      await operateProcessInstancePage.saveVariableButton.click();

      await expect(operateProcessInstancePage.variableSpinner).toBeVisible();
      await expect(operateProcessInstancePage.variableSpinner).toBeHidden();
    });

    await test.step('Retry one incident to resolve it', async () => {
      await operateProcessInstancePage.clickIncidentsTab();
      const firstErrorType = 'Condition error.';
      const remainingErrorType = 'Extract value error.';

      await operateProcessInstancePage.retryIncidentByErrorType(firstErrorType);

      await expect(
        (
          await operateProcessInstancePage.getIncidentRowByErrorType(
            firstErrorType,
          )
        ).getByTestId('operation-spinner'),
      ).toBeVisible();
      await waitForAssertion({
        assertion: async () => {
          await expect
            .poll(
              async () =>
                await operateProcessInstancePage.incidentsTableOperationSpinner.isVisible(),
            )
            .toBe(false);
        },
        onFailure: async () => {
          await page.reload();
          await sleep(5000);
        },
      });

      await expect
        .poll(async () => await operateProcessInstancePage.getIncidentCount())
        .toBe(1);

      await expect(
        await operateProcessInstancePage.getIncidentRowByErrorType(
          firstErrorType,
        ),
      ).toHaveCount(0);
      await expect(
        await operateProcessInstancePage.getIncidentRowByErrorType(
          remainingErrorType,
        ),
      ).toBeVisible();
    });

    await test.step('Add variable isCool', async () => {
      await operateProcessInstancePage.clickVariablesTab();
      await operateProcessInstancePage.addVariableButton.click();

      await operateProcessInstancePage.newVariableNameField.type('isCool');
      await operateProcessInstancePage.newVariableValueField.type('true');

      await expect(operateProcessInstancePage.saveVariableButton).toBeEnabled();

      await operateProcessInstancePage.saveVariableButton.click();
      await expect(operateProcessInstancePage.variableSpinner).toBeVisible();
      await expect(operateProcessInstancePage.variableSpinner).toBeHidden();
    });

    await test.step('Retry second incident to resolve it', async () => {
      await operateProcessInstancePage.clickIncidentsTab();
      const secondErrorType = 'Extract value error.';
      await operateProcessInstancePage.retryIncidentByErrorType(
        secondErrorType,
      );

      await expect(
        (
          await operateProcessInstancePage.getIncidentRowByErrorType(
            secondErrorType,
          )
        ).getByTestId('operation-spinner'),
      ).toBeVisible();
    });

    await test.step('Expect all incidents resolved', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessInstancePage.incidentsTab).toBeHidden();
        },
        onFailure: async () => {
          await page.reload();
          await sleep(500);
        },
      });
      await expect(operateProcessInstancePage.incidentsTable).toBeHidden();
      await expect(operateProcessInstancePage.completedIcon).toBeVisible();
    });
  });

  test('Cancel an instance', async ({operateProcessInstancePage, page}) => {
    const instanceId = instanceWithIncidentToCancel.processInstanceKey;

    await test.step('Navigate to process instance with incident', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: instanceId,
      });
      await sleep(1000);
    });

    await test.step('Verify incidents tab is visible', async () => {
      await expect(operateProcessInstancePage.incidentsTab).toBeVisible();
      await operateProcessInstancePage.clickIncidentsTab();
      await expect(operateProcessInstancePage.incidentTypeFilter).toBeVisible();
      await operateProcessInstancePage.verifyIncidentCount(3);
    });

    await test.step('Cancel the instance', async () => {
      await operateProcessInstancePage.cancelInstance(instanceId);

      await expect(
        operateProcessInstancePage.cancellationScheduledToast,
      ).toBeVisible();
    });

    await test.step('Verify instance is canceled', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessInstancePage.incidentsTab).toBeHidden();
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 5,
      });

      await expect(operateProcessInstancePage.terminatedIcon).toBeVisible();

      expect(await operateProcessInstancePage.endDateField.innerText()).toMatch(
        DATE_REGEX,
      );
    });
  });

  test('Should render collapsed sub process and navigate between planes', async ({
    page,
    operateProcessInstancePage,
  }) => {
    const {diagramHelper} = operateProcessInstancePage;

    await test.step('Navigate to collapsed sub process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: collapsedSubProcessInstance.processInstanceKey,
      });
      await sleep(1000);
    });

    await test.step('Click on startEvent in instance history', async () => {
      await operateProcessInstancePage.clickTreeItem('startEvent');
      await operateProcessInstancePage.clickDetailsTab();
      await expect(page.getByText(/execution duration/i)).toBeVisible();
    });

    await test.step('Click on submit application in instance history', async () => {
      await operateProcessInstancePage.clickTreeItem(/submit application/i);

      await expect(
        diagramHelper.getFlowNode('submit application'),
      ).toBeVisible();
    });

    await test.step('Navigate to fill form flow node', async () => {
      await page.keyboard.press('ArrowRight');
      await operateProcessInstancePage.clickTreeItem(/fill form/i);
      await expect(diagramHelper.getFlowNode('fill form')).toBeVisible();
    });

    await test.step('Click on collapsed sub process', async () => {
      await operateProcessInstancePage.diagram
        .getByText('collapsedSubProcess')
        .click();

      await expect(
        diagramHelper.getFlowNode('submit application'),
      ).toBeVisible();
      await expect(diagramHelper.getFlowNode('fill form')).toBeHidden();
    });

    await test.step('Navigate back to start event', async () => {
      await operateProcessInstancePage.clickTreeItem('startEvent', true);

      await expect(
        diagramHelper.getFlowNode('submit application'),
      ).toBeVisible();
    });

    await test.step('Drill down into sub process', async () => {
      await operateProcessInstancePage.drilldownButton.click();

      await expect(diagramHelper.getFlowNode('fill form')).toBeVisible();
    });
  });

  test('Should render execution count badges', async ({
    operateProcessInstancePage,
  }) => {
    await test.step('Navigate to execution count process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: executionCountProcessInstance.processInstanceKey,
      });
    });

    const elementIds = [
      'StartEvent_1',
      'ParallelGateway',
      'ExclusiveGateway',
      'StartEvent_2',
      'EndEvent_2',
      'SubProcess',
    ];

    await test.step('Verify execution count badges are not visible by default', async () => {
      await operateProcessInstancePage.verifyExecutionCountBadgesNotVisible(
        elementIds,
      );
    });

    await test.step('Toggle execution count on', async () => {
      await operateProcessInstancePage.toggleExecutionCount();
    });

    await test.step('Verify execution count badges are visible', async () => {
      await operateProcessInstancePage.verifyExecutionCountBadgesVisible([
        'StartEvent_1',
        'ParallelGateway',
        'ExclusiveGateway',
      ]);
    });

    await test.step('Toggle execution count off', async () => {
      await operateProcessInstancePage.toggleExecutionCount();
    });

    await test.step('Verify execution count badges are not visible again', async () => {
      await operateProcessInstancePage.verifyExecutionCountBadgesNotVisible(
        elementIds,
      );
    });
  });
});

test.beforeAll(async () => {
  await deploy([
    './resources/decision_to_test-incident.dmn',
    './resources/process_to_test_incidents.bpmn',
    './resources/callProcess_with Incident.bpmn',
  ]);
  await createInstances('root-cause-test', 1, 1);
  await createInstances('call-level-1-process', 1, 1, {shouldFail: true});

  await sleep(5000);
});

test.describe('Process Instance Incident', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Verify Incident root cause instance', async ({
    operateProcessInstancePage,
    operateHomePage,
    operateProcessesPage,
  }) => {
    test.slow();

    await test.step('Navigate to Processes tab and open the process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.filterByProcessName('Process-Incident');
      await operateProcessesPage.clickProcessInstanceLink();
    });

    await test.step('Verify error indicators on all affected elements', async () => {
      await expect(operateProcessInstancePage.diagram).toBeVisible();
      await expect(operateProcessInstancePage.diagramSpinner).toBeHidden({
        timeout: 30000,
      });

      await expect(operateProcessInstancePage.incidentsTab).toBeVisible();
      await operateProcessInstancePage.clickIncidentsTab();

      await operateProcessInstancePage.verifyIncidentCount(4);

      await expect(operateProcessInstancePage.incidentsTab).toBeVisible();
    });

    await test.step('Click IO mapping Error and verify Error Type', async () => {
      await operateProcessInstancePage.clickOnElementInDiagram(
        'Task_IOMapping',
      );

      await expect(
        operateProcessInstancePage.getSelectedIncidentRow(
          /IO mapping error\./i,
        ),
      ).toBeVisible();
    });

    await test.step('Click ExclusiveGatewayError and verify Error Type', async () => {
      await operateProcessInstancePage.clickOnElementInDiagram(
        'Gateway_Expression',
      );

      await expect(
        operateProcessInstancePage.getSelectedIncidentRow(
          /Extract value error\./i,
        ),
      ).toBeVisible();
    });

    await test.step('Click Call Activity and verify the error type', async () => {
      await operateProcessInstancePage
        .getDiagramElement('Task_CallActivity')
        .dblclick();

      await expect(
        operateProcessInstancePage.getIncidentRow(/Called element error\./i),
      ).toBeVisible();
    });
  });
});
