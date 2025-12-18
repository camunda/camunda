/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {
  deploy,
  createSingleInstance,
  searchByProcessInstanceKey,
} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {waitForAssertion} from 'utils/waitForAssertion';
import {defaultAssertionOptions} from 'utils/constants';

type ProcessInstance = {processInstanceKey: number};

let incidentProcessInstance: ProcessInstance;
let embeddedSubprocessInstance: ProcessInstance;

const activityNode = 'Node';
const activityFirstSubprocess = 'FirstSubprocess';
const activityCollectMoney = 'CollectMoney';
const eventEndFirstSubProcess = 'EndFirstSubProcess';
const activitySecondSubprocess = 'SecondSubprocess';
const eventStartSecondSubProcess = 'StartSecondSubProcess';
const activitySendItems = 'SendItems';
const eventEndSecondSubProcess = 'EndSecondSubProcess';
const eventEndGlobal = 'EndGlobal';

test.beforeAll(async () => {
  await deploy([
    './resources/IncidentProcess.bpmn',
    './resources/EmbeddedSubprocess.bpmn',
  ]);

  incidentProcessInstance = {
    processInstanceKey: Number(
      (
        await createSingleInstance('IncidentProcess', 1, {
          wuf: 1,
        })
      ).processInstanceKey,
    ),
  };

  embeddedSubprocessInstance = {
    processInstanceKey: Number(
      (
        await createSingleInstance('Process_EmbeddedSubprocess', 1, {
          meow: 0,
        })
      ).processInstanceKey,
    ),
  };
});

test.describe('Process Instance History', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Verify history of instance with an incident', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
    operateProcessInstancePage,
  }) => {
    const incidentProcessInstanceKey =
      incidentProcessInstance.processInstanceKey;

    console.log(`Incident Process Instance Key: ${incidentProcessInstanceKey}`);

    await test.step('Verify Process Instance is active', async () => {
      await expect(async () => {
        const process = await searchByProcessInstanceKey(
          incidentProcessInstanceKey.toString(),
        );
        expect(process.items.length).toBeGreaterThan(0);
      }).toPass({intervals: [5_000], timeout: 60_000});
    });

    await test.step('Wait for process name filter to be enabled', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateFiltersPanelPage.processNameFilter).toBeEnabled();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Open Process Instances Page', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(async () => {
            await operateFiltersPanelPage.selectProcess('IncidentProcess');
          }).toPass({intervals: [5_000], timeout: 60_000});
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await operateFiltersPanelPage.selectVersion('1');
    });

    await test.step('Wait for incident to appear', async () => {
      await operateFiltersPanelPage.clickActiveInstancesCheckbox();
      await page.waitForTimeout(5000);
      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('1 result')).toBeVisible();
          expect(
            await operateProcessesPage.tableHasInstanceKey(
              incidentProcessInstanceKey.toString(),
            ),
          ).toBe(true);
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    const mainProcessName = 'IncidentProcess';
    const incidentItemName = 'Where to go?';

    await test.step('Verify Instance History Tab has incidents', async () => {
      await operateFiltersPanelPage.displayOptionalFilter('Variable');
      await operateFiltersPanelPage.fillVariableNameFilter('wuf');
      await operateFiltersPanelPage.fillVariableValueFilter('1');
      await operateProcessesPage.clickProcessInstanceLink();
      await waitForAssertion({
        assertion: async () => {
          const key = await operateProcessInstancePage.getProcessInstanceKey();
          expect(key).toContain(`${incidentProcessInstanceKey}`);
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await expect(operateProcessInstancePage.instanceHistory).toBeVisible();
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessInstancePage.incidentsBanner,
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await operateFiltersPanelPage.displayOptionalFilter('Variable');
          await operateFiltersPanelPage.fillVariableNameFilter('wuf');
          await operateFiltersPanelPage.fillVariableValueFilter('1');
        },
      });

      await operateProcessInstancePage.verifyHistoryItemsStatus(
        mainProcessName,
        ['INCIDENT'],
      );
      await operateProcessInstancePage.verifyHistoryItemsStatus(
        incidentItemName,
        ['INCIDENT'],
      );
    });

    await test.step('Add variable to the process', async () => {
      await operateProcessInstancePage.clickAddVariableButton();
      await operateProcessInstancePage.fillNewVariable('goUp', '6');
      await operateProcessInstancePage.clickSaveVariableButton();
      await expect(operateProcessInstancePage.variableSpinner).toBeHidden();
    });

    await test.step('Retry incident', async () => {
      await operateProcessInstancePage.clickIncidentsBanner();
      const errorMessage = "Expected result of the expression 'goUp < 0'";
      await operateProcessInstancePage.retryIncidentByErrorMessage(
        errorMessage,
      );
    });

    await test.step('Verify incident is resolved in Instance History', async () => {
      await page.waitForTimeout(12000);
      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessInstancePage.incidentsBanner).toBeHidden();
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await operateProcessInstancePage.verifyHistoryItemsStatus(
        mainProcessName,
        ['ACTIVE'],
      );
      await operateProcessInstancePage.verifyHistoryItemsStatus(
        incidentItemName,
        ['COMPLETED'],
      );
    });
  });

  test('Verify history of instance with a subprocess', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
    operateProcessInstancePage,
    operateProcessModificationModePage,
    operateDiagramPage,
  }) => {
    const embeddedProcessInstancePIK =
      embeddedSubprocessInstance.processInstanceKey;

    await test.step('Verify Process Instance is active', async () => {
      await expect(async () => {
        const process = await searchByProcessInstanceKey(
          embeddedProcessInstancePIK.toString(),
        );
        expect(process.items.length).toBeGreaterThan(0);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Wait for process name filter to be enabled', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateFiltersPanelPage.processNameFilter).toBeEnabled();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Open Process Instances Page and verify results', async () => {
      await operateFiltersPanelPage.selectProcess('EmbeddedSubprocess');
      await operateFiltersPanelPage.selectVersion('1');
      await operateProcessesPage.clickProcessInstanceLink();
      const key = await operateProcessInstancePage.getProcessInstanceKey();
      expect(key).toContain(`${embeddedProcessInstancePIK}`);
    });

    await test.step('Enter modification mode and assert results', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    const mainProcessName = 'EmbeddedSubprocess';
    let expandingElementsInMainProcess;
    await test.step('Verify Main process has no nested element', async () => {
      expandingElementsInMainProcess =
        await operateProcessInstancePage.checkIfPresentExpandeingElementsInMainProcess(
          mainProcessName,
        );
      expect(expandingElementsInMainProcess).toBe(0);
    });

    await test.step('Add modification to activate task flow node process in first subprocess and verify results', async () => {
      const firstSubprocessTaskElement = activityCollectMoney;
      await operateProcessModificationModePage.addTokenToFlowNodeAndApplyChanges(
        firstSubprocessTaskElement,
      );
      await operateDiagramPage.verifyStateOverlay(
        firstSubprocessTaskElement,
        'active',
      );
    });

    await test.step('Verify Instance History Tab has 1 nested element', async () => {
      expandingElementsInMainProcess =
        await operateProcessInstancePage.checkIfPresentExpandeingElementsInMainProcess(
          mainProcessName,
        );
      expect(expandingElementsInMainProcess).toBe(1);
    });

    await test.step('Enter modification mode and assert results', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    await test.step('Add modification to activate task flow node process in second subprocess and verify results', async () => {
      const secondSubprocessTaskElement = activitySendItems;
      await operateProcessModificationModePage.addTokenToFlowNodeAndApplyChanges(
        secondSubprocessTaskElement,
      );
      await operateDiagramPage.verifyStateOverlay(
        secondSubprocessTaskElement,
        'active',
      );
    });

    await test.step('Verify Instance History Tab has nested subprocess element and verify results', async () => {
      expandingElementsInMainProcess =
        await operateProcessInstancePage.checkIfPresentExpandeingElementsInMainProcess(
          mainProcessName,
        );
      expect(expandingElementsInMainProcess).toBe(2);
    });
  });

  test('Verify process Instance modification', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
    operateProcessInstancePage,
    operateProcessModificationModePage,
    operateDiagramPage,
  }) => {
    const embeddedSubprocesModificationInstance = {
      processInstanceKey: Number(
        (
          await createSingleInstance('Process_EmbeddedSubprocess', 1, {
            meow: 0,
            test: 101,
          })
        ).processInstanceKey,
      ),
    };
    const embeddedProcessInstanceModificationPIK =
      embeddedSubprocesModificationInstance.processInstanceKey;

    await test.step('Verify Process Instance is active', async () => {
      await expect(async () => {
        const process = await searchByProcessInstanceKey(
          embeddedProcessInstanceModificationPIK.toString(),
        );
        expect(process.items.length).toBeGreaterThan(0);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Wait for process name filter to be enabled', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateFiltersPanelPage.processNameFilter).toBeEnabled();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Open Process Instances Page and verify results', async () => {
      await operateFiltersPanelPage.selectProcess('EmbeddedSubprocess');
      await operateFiltersPanelPage.selectVersion('1');
      await waitForAssertion({
        assertion: async () => {
          await operateFiltersPanelPage.displayOptionalFilter('Variable');
          await operateFiltersPanelPage.fillVariableNameFilter('test');
          await operateFiltersPanelPage.fillVariableValueFilter('101');
          await expect(page.getByText('1 result')).toBeVisible({timeout: 5000});
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await operateProcessesPage.clickProcessInstanceLink();
      await waitForAssertion({
        assertion: async () => {
          const key = await operateProcessInstancePage.getProcessInstanceKey();
          expect(key).toContain(`${embeddedProcessInstanceModificationPIK}`);
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Verify one active token', async () => {
      const nodeStateOverlayActive =
        await operateDiagramPage.getStateOverlayLocatorByElementNameAndState(
          activityNode,
          'active',
        );
      await waitForAssertion({
        assertion: async () => {
          await expect(nodeStateOverlayActive).toBeVisible({timeout: 5000});
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Enter modification mode and assert results', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessModificationModePage.modifyModeHeader,
      ).toBeVisible({timeout: 5000});
    });

    await test.step('Add 2 tokens to "Collect money" task in first subprocess and verify two active tokens in diagram', async () => {
      for (let i = 1; i < 3; i++) {
        await operateProcessModificationModePage.addTokenToFlowNode(
          activityCollectMoney,
        );
        await operateProcessModificationModePage.verifyModificationOverlay(
          activityCollectMoney,
          i,
        );
      }
      await operateProcessModificationModePage.applyChanges();
      await operateDiagramPage.verifyStateOverlay(
        activityCollectMoney,
        'active',
        2,
      );
    });

    await test.step('Verify History has "Collect Money" 2 times', async () => {
      const nestedParentName = activityFirstSubprocess;
      await operateProcessInstancePage.ensureElementExpandedInHistory(
        nestedParentName,
      );
      const nestedParentGroupLocator =
        await operateProcessInstancePage.getNestedGroupInHistoryLocator(
          nestedParentName,
        );
      await expect(nestedParentGroupLocator).toBeVisible();
      expect(
        await nestedParentGroupLocator.getByLabel(activityCollectMoney).count(),
      ).toBe(2);
    });

    await test.step('Verify First Subprocess has state overlay 1', async () => {
      await operateDiagramPage.verifyStateOverlay(
        activityFirstSubprocess,
        'active',
        1,
      );
    });

    await test.step('Enter modification mode and assert results', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    await test.step('Add token to the Second Subprocess and verify active token in diagram', async () => {
      await operateProcessModificationModePage.addTokenToSubprocessAndApplyChanges(
        activitySecondSubprocess,
      );
      await operateDiagramPage.verifyStateOverlay(
        activitySecondSubprocess,
        'active',
        1,
      );
      await operateDiagramPage.verifyStateOverlay(
        activitySendItems,
        'active',
        1,
      );
    });

    await test.step('Verify History has "Send items" 1 time', async () => {
      const nestedParentName = activitySecondSubprocess;
      await operateProcessInstancePage.ensureElementExpandedInHistory(
        nestedParentName,
      );
      const nestedParentGroupLocator =
        await operateProcessInstancePage.getNestedGroupInHistoryLocator(
          nestedParentName,
        );
      await expect(nestedParentGroupLocator).toBeVisible({timeout: 30000});
      expect(
        await nestedParentGroupLocator.getByLabel(activitySendItems).count(),
      ).toBe(1);
    });

    await test.step('Enter modification mode and assert results', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    await test.step('Move tokens from "Collect money" to end state of first subprocess and verify results', async () => {
      await operateProcessModificationModePage.moveAllTokensFromSelectedFlowNodeToTarget(
        activityCollectMoney,
        eventEndFirstSubProcess,
      );
      await operateProcessModificationModePage.verifyModificationOverlay(
        activityCollectMoney,
        -2,
      );
      await operateProcessModificationModePage.verifyModificationOverlay(
        eventEndFirstSubProcess,
        2,
      );
      await operateProcessModificationModePage.applyChanges();
    });

    await test.step('Verify in diagram that "Collect money" changed accordingly', async () => {
      await operateDiagramPage.verifyStateOverlay(
        activityCollectMoney,
        'canceled',
        2,
      );
      await operateDiagramPage.verifyStateOverlay(
        activitySendItems,
        'active',
        2,
      );
    });

    await test.step('Verify First Subprocess history reflects moved tokens accordingly', async () => {
      const nestedParentName = activityFirstSubprocess;
      await operateProcessInstancePage.ensureElementExpandedInHistory(
        nestedParentName,
      );
      const firstSubprocessLocator =
        await operateProcessInstancePage.getNestedParentLocatorInHistory(
          nestedParentName,
        );
      await expect(firstSubprocessLocator).toBeVisible();

      await operateProcessInstancePage.verifyHistoryItemsStatus(
        activityFirstSubprocess,
        ['COMPLETED'],
      );
      await operateProcessInstancePage.verifyHistoryItemsStatus(
        activityCollectMoney,
        ['TERMINATED', 'TERMINATED'],
      );
      await operateProcessInstancePage.verifyHistoryItemsStatus(
        eventEndFirstSubProcess,
        ['COMPLETED'],
      );
    });

    await test.step('Verify Second Subprocess history reflects moved tokens accordingly', async () => {
      const nestedParentName = activitySecondSubprocess;
      await operateProcessInstancePage.ensureElementExpandedInHistory(
        nestedParentName,
      );

      await operateProcessInstancePage.verifyHistoryItemsStatus(
        activitySecondSubprocess,
        ['ACTIVE', 'ACTIVE'],
      );
      await operateProcessInstancePage.verifyHistoryItemsStatus(
        eventStartSecondSubProcess,
        ['COMPLETED', 'COMPLETED'],
      );
      await operateProcessInstancePage.verifyHistoryItemsStatus(
        activitySendItems,
        ['ACTIVE', 'ACTIVE'],
      );
    });

    await test.step('Cancel one instance of "Send items"', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await operateProcessModificationModePage.cancelOneTokenUsingHistory(
        activitySendItems,
      );
    });

    await test.step('Verify diagram changed accordingly', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateDiagramPage.verifyStateOverlay(
            activitySecondSubprocess,
            'canceled',
            1,
          );
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await operateDiagramPage.verifyStateOverlay(
        activitySecondSubprocess,
        'active',
        1,
      );
      await operateDiagramPage.verifyStateOverlay(
        activitySendItems,
        'active',
        1,
      );
      await operateDiagramPage.verifyStateOverlay(
        activitySendItems,
        'canceled',
        1,
      );
    });

    await test.step('Verify history changed accordingly', async () => {
      const nestedParentName = activitySecondSubprocess;
      await operateProcessInstancePage.ensureElementExpandedInHistory(
        nestedParentName,
      );

      await operateProcessInstancePage.verifyHistoryItemsStatus(
        activitySecondSubprocess,
        ['TERMINATED', 'ACTIVE'],
      );
      await operateProcessInstancePage.verifyHistoryItemsStatus(
        eventStartSecondSubProcess,
        ['COMPLETED', 'COMPLETED'],
      );
      await operateProcessInstancePage.verifyHistoryItemsStatus(
        activitySendItems,
        ['TERMINATED', 'ACTIVE'],
      );
    });

    await test.step('Move all Send items', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessModificationModePage.modifyModeHeader,
      ).toBeVisible();

      await operateProcessModificationModePage.moveAllTokensFromSelectedFlowNodeToTarget(
        activitySendItems,
        eventEndSecondSubProcess,
      );
      await operateProcessModificationModePage.verifyModificationOverlay(
        activitySendItems,
        -1,
      );
      await operateProcessModificationModePage.verifyModificationOverlay(
        eventEndSecondSubProcess,
        1,
      );
      await operateProcessModificationModePage.applyChanges();
    });

    await test.step('Verify diagram changed accordingly', async () => {
      await page.waitForTimeout(10000);
      await waitForAssertion({
        assertion: async () => {
          await operateDiagramPage.verifyStateOverlay(
            activitySendItems,
            'canceled',
            2,
          );
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await operateDiagramPage.verifyStateOverlay(
        activitySecondSubprocess,
        'canceled',
        1,
      );
      await operateDiagramPage.verifyStateOverlay(
        eventEndGlobal,
        'completedEndEvents',
        1,
      );
    });

    await test.step('Verify history changed accordingly', async () => {
      const nestedParentName = activitySecondSubprocess;
      await operateProcessInstancePage.ensureElementExpandedInHistory(
        nestedParentName,
      );

      await operateProcessInstancePage.verifyHistoryItemsStatus(
        activitySecondSubprocess,
        ['TERMINATED', 'COMPLETED'],
      );
      await operateProcessInstancePage.verifyHistoryItemsStatus(
        eventStartSecondSubProcess,
        ['COMPLETED', 'COMPLETED'],
      );
      await operateProcessInstancePage.verifyHistoryItemsStatus(
        activitySendItems,
        ['TERMINATED', 'TERMINATED'],
      );
      await operateProcessInstancePage.verifyHistoryItemsStatus(
        eventEndSecondSubProcess,
        ['COMPLETED'],
      );

      const mainParentName = 'EmbeddedSubprocess';
      await operateProcessInstancePage.ensureElementExpandedInHistory(
        mainParentName,
      );
      await operateProcessInstancePage.verifyHistoryItemsStatus(
        eventEndGlobal,
        ['COMPLETED'],
      );
    });
  });
});
