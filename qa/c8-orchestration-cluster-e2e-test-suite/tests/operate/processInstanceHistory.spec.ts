/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { test } from 'fixtures';
import { expect } from '@playwright/test';
import { deploy, createSingleInstance } from 'utils/zeebeClient';
import { captureScreenshot, captureFailureVideo } from '@setup';
import { navigateToApp } from '@pages/UtilitiesPage';
import { waitForAssertion } from 'utils/waitForAssertion';

type ProcessInstance = { processInstanceKey: number };

let incidentProcessInstance: ProcessInstance;
let embeddedSubprocessInstance: ProcessInstance;

test.beforeAll(async () => {
    await deploy([
        './resources/IncidentProcess.bpmn',
        './resources/EmbeddedSubprocess.bpmn',
    ]);

    incidentProcessInstance = {
        processInstanceKey: Number(
            (await createSingleInstance('IncidentProcess', 1)).processInstanceKey,
        ),
    };

    embeddedSubprocessInstance = {
        processInstanceKey: Number(
            (await createSingleInstance('Process_EmbeddedSubprocess', 1, {
                meow: 0,
            })).processInstanceKey,
        ),
    };
});

test.describe('Process Instance History', () => {
    test.beforeEach(async ({ page, loginPage, operateHomePage }) => {
        await navigateToApp(page, 'operate');
        await loginPage.login('demo', 'demo');
        await expect(operateHomePage.operateBanner).toBeVisible();
        await operateHomePage.clickProcessesTab();
    });

    test.afterEach(async ({ page }, testInfo) => {
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

        await test.step('Open Process Instances Page', async () => {
            await operateFiltersPanelPage.selectProcess('IncidentProcess');
            await operateFiltersPanelPage.selectVersion('1');
            await operateProcessesPage.clickProcessInstanceLink();
            const key = await operateProcessInstancePage.getProcessInstanceKey();
            expect(key).toContain(`${incidentProcessInstanceKey}`);
        });

        await test.step('Verify Instance History Tab has incidents', async () => {
            await expect(operateProcessInstancePage.instanceHistory).toBeVisible();
            await waitForAssertion({
                assertion: async () => {
                    await expect(
                        operateProcessInstancePage.incidentsBanner,
                    ).toBeVisible();
                },
                onFailure: async () => {
                    await page.reload();
                },
            });
            await expect(operateProcessInstancePage.incidentsBanner).toContainText(
                '1 Incident',
            );

            const incidentIconsCount =
                await operateProcessInstancePage.getAllIncidentIconsAmountInHistory();
            expect(incidentIconsCount).toBe(2);
        });

        await test.step('Add variable to the process', async () => {
            await operateProcessInstancePage.clickAddVariableButton();
            await operateProcessInstancePage.fillNewVariable('goUp', '6');
            await operateProcessInstancePage.clickSaveVariableButton();
            await expect(operateProcessInstancePage.variableSpinner).toBeHidden();
        });

        await test.step('Retry incident', async () => {
            await operateProcessInstancePage.clickIncidentsBanner();
            const errorMessage =
                "Expected result of the expression 'goUp < 0' to be 'BOOLEAN'...";
            await operateProcessInstancePage.retryIncidentByErrorMessage(
                errorMessage,
            );
        });

        // await test.step('Verify incident is resolved in Instance History', async () => {
        //   await waitForAssertion({
        //     assertion: async () => {
        //       await expect(operateProcessInstancePage.incidentsBanner).toBeHidden();
        //     },
        //     onFailure: async () => {
        //       await page.reload();
        //     },
        //   });
        //   await waitForAssertion({
        //     assertion: async () => {
        //       const incidentIconsCountAfterResolution =
        //         await operateProcessInstancePage.getAllIncidentIconsAmountInHistory();
        //       expect(incidentIconsCountAfterResolution).toBe(0);
        //     },
        //     onFailure: async () => {
        //       await page.reload();
        //     },
        //   });
        // });

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
        await test.step('Open Process Instances Page and verify results', async () => {
            await operateFiltersPanelPage.selectProcess('EmbeddedSubprocess');
            await operateFiltersPanelPage.selectVersion('1');
            await operateProcessesPage.clickProcessInstanceLink();
            const key = await operateProcessInstancePage.getProcessInstanceKey();
            expect(key).toContain(`${embeddedProcessInstancePIK}`);
        });

        await test.step('Enter modification mode and assert results', async () => {
            await operateProcessInstancePage.clickModifyInstanceButton();
            await operateProcessInstancePage.clickModifyDialogContinueButton();
            await expect(operateProcessModificationModePage.modifyModeHeader).toBeVisible();
        });

        const mainProcessName = 'EmbeddedSubprocess';
        let expandingElementsInMainProcess;
        await test.step('Verify Main process has no nested element', async () => {
            expandingElementsInMainProcess = await operateProcessInstancePage.checkIfPresentExpandeingElementsInMainProcess(mainProcessName);
            expect(expandingElementsInMainProcess).toBe(0);
        });

        await test.step('Add modification to activate task flow node process in first subprocess and verify results', async () => {
            const firstSubprocessTaskElement = 'Activity_CollectMoney';
            await operateDiagramPage.clickFlowNode(firstSubprocessTaskElement);
            await operateProcessModificationModePage.clickAddModificationButtononPopup();
            await operateProcessModificationModePage.clickApplyModificationsButton();
            await operateProcessModificationModePage.clickApplyButtonModificationsDialog();

            const activeStateOverlayTask = await operateDiagramPage
                .getStateOverlayLocatorByElementNameAndState(firstSubprocessTaskElement, 'active');

            await waitForAssertion({
                assertion: async () => {
                    await expect(activeStateOverlayTask).toBeVisible();
                },
                onFailure: async () => {
                    await page.reload();
                },
            });
        });

        await test.step('Verify Instance History Tab has 1 nested element', async () => {
            expandingElementsInMainProcess = await operateProcessInstancePage.checkIfPresentExpandeingElementsInMainProcess(mainProcessName);
            expect(expandingElementsInMainProcess).toBe(1);
        });

        await test.step('Enter modification mode and assert results', async () => {
            await operateProcessInstancePage.clickModifyInstanceButton();
            await operateProcessInstancePage.clickModifyDialogContinueButton();
            await expect(operateProcessModificationModePage.modifyModeHeader).toBeVisible();
        });

        await test.step('Add modification to activate task flow node process in second subprocess and verify results', async () => {
            const secondSubprocessTaskElement = 'Activity_SendItems';
            await operateDiagramPage.clickFlowNode(secondSubprocessTaskElement);
            await operateProcessModificationModePage.clickAddModificationButtononPopup();
            await operateProcessModificationModePage.clickApplyModificationsButton();
            await operateProcessModificationModePage.clickApplyButtonModificationsDialog();

            const activeStateOverlayTask = await operateDiagramPage
                .getStateOverlayLocatorByElementNameAndState(secondSubprocessTaskElement, 'active');

            await waitForAssertion({
                assertion: async () => {
                    await expect(activeStateOverlayTask).toBeVisible();
                },
                onFailure: async () => {
                    await page.reload();
                },
            });
        });

        await test.step('Verify Instance History Tab has nested subprocess element and verify results', async () => {
            expandingElementsInMainProcess = await operateProcessInstancePage.checkIfPresentExpandeingElementsInMainProcess(mainProcessName);
            expect(expandingElementsInMainProcess).toBe(2);
        });
    })

    test('Verify process Instance modification', async ({
        page,
        operateProcessesPage,
        operateFiltersPanelPage,
        operateProcessInstancePage,
        operateProcessModificationModePage,
        operateDiagramPage, }) => {

        const embeddedSubprocesModificationInstance = {
            processInstanceKey: Number(
                (await createSingleInstance('Process_EmbeddedSubprocess', 1, {
                    meow: 0,
                    test: 101,
                })).processInstanceKey,
            ),
        };

        const embeddedProcessInstanceModificationPIK =
            embeddedSubprocesModificationInstance.processInstanceKey;
        await test.step('Open Process Instances Page and verify results', async () => {
            await operateFiltersPanelPage.selectProcess('EmbeddedSubprocess');
            await operateFiltersPanelPage.selectVersion('1');
            await waitForAssertion({
                assertion: async () => {
                    await operateFiltersPanelPage.displayOptionalFilter('Variable');
                    await operateFiltersPanelPage.fillVariableNameFilter('test');
                    await operateFiltersPanelPage.fillVariableValueFilter('101');
                    await expect(page.getByText('1 result')).toBeVisible();
                },
                onFailure: async () => {
                    await page.reload();
                },
            });

            await operateProcessesPage.clickProcessInstanceLink();
            const key = await operateProcessInstancePage.getProcessInstanceKey();
            expect(key).toContain(`${embeddedProcessInstanceModificationPIK}`);
        });
    })

});
