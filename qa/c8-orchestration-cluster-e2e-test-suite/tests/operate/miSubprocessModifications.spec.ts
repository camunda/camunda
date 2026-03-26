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
  cancelProcessInstance,
} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp, hideHelperModals} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';
import {waitForAssertion} from 'utils/waitForAssertion';

type ProcessInstance = {
  processInstanceKey: string;
};

let seqMiInstance: ProcessInstance;
let parMiInstance: ProcessInstance;
let restrictionInstance: ProcessInstance;
let crossMiInstance: ProcessInstance;
let outwardMoveInstance: ProcessInstance;
let moveAllInstance: ProcessInstance;
let cancelTokenInstance: ProcessInstance;
let addTokenInstance: ProcessInstance;
let completedMiTaskInstance: ProcessInstance;

test.beforeAll(async () => {
  await deploy([
    './resources/miSeqSubprocessModification.bpmn',
    './resources/miParSubprocessModification.bpmn',
    './resources/miSubprocessMoveRestriction.bpmn',
    './resources/miCrossMiRestriction.bpmn',
  ]);

  seqMiInstance = await createSingleInstance('miSeqSubprocess', 1, {
    items: ['itemA', 'itemB'],
  });

  parMiInstance = await createSingleInstance('miParSubprocess', 1, {
    items: ['itemA', 'itemB', 'itemC'],
  });

  restrictionInstance = await createSingleInstance(
    'miSubprocessRestriction',
    1,
    {
      items: ['itemA', 'itemB'],
    },
  );

  crossMiInstance = await createSingleInstance('miCrossMiRestriction', 1, {
    items: ['itemA', 'itemB'],
  });

  outwardMoveInstance = await createSingleInstance(
    'miSubprocessRestriction',
    1,
    {
      items: ['itemA', 'itemB'],
    },
  );

  moveAllInstance = await createSingleInstance('miSubprocessRestriction', 1, {
    items: ['itemA', 'itemB'],
  });

  cancelTokenInstance = await createSingleInstance('miParSubprocess', 1, {
    items: ['itemA', 'itemB'],
  });

  addTokenInstance = await createSingleInstance('miSeqSubprocess', 1, {
    items: ['itemA'],
  });

  completedMiTaskInstance = await createSingleInstance(
    'miSubprocessRestriction',
    1,
    {
      items: ['itemA'],
    },
  );

  await sleep(3000);
});

test.afterAll(async () => {
  for (const instance of [
    seqMiInstance,
    parMiInstance,
    restrictionInstance,
    crossMiInstance,
    outwardMoveInstance,
    moveAllInstance,
    cancelTokenInstance,
    addTokenInstance,
    completedMiTaskInstance,
  ]) {
    await cancelProcessInstance(instance.processInstanceKey);
  }
});

test.describe('Multi-Instance Subprocess Modifications', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await hideHelperModals(page);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Should move an element inside the same sequential MI body instance', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    await test.step('Navigate to sequential MI subprocess process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: seqMiInstance.processInstanceKey,
      });
    });

    await test.step('Wait for Task A to be active inside the sequential MI body', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /sequential mi subprocess \(multi instance\)/i,
          );
          await operateProcessInstancePage.expandTreeItemInHistory(
            /^sequential mi subprocess$/i,
          );
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/task a/i),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });

    await test.step('Enter modification mode', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessInstanceViewModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    await test.step('Move token from Task A to Task B within the same MI body', async () => {
      await operateProcessInstanceViewModificationModePage.moveInstanceFromSelectedFlowNodeToTarget(
        'SubTaskA_miSeq',
        'SubTaskB_miSeq',
      );
    });

    await test.step('Verify modification overlays: -1 on Task A and +1 on Task B', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
            'SubTaskA_miSeq',
            -1,
          );
          await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
            'SubTaskB_miSeq',
            1,
          );
        },
        onFailure: async () => {},
      });
    });

    await test.step('Apply the modification', async () => {
      await operateProcessInstanceViewModificationModePage.applyChanges();
    });

    await test.step('Verify the token moved to Task B in the instance history', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /sequential mi subprocess \(multi instance\)/i,
          );
          await operateProcessInstancePage.expandTreeItemInHistory(
            /^sequential mi subprocess$/i,
          );
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/task b/i),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });
  });

  test('Should move an element inside the same parallel MI body instance', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    await test.step('Navigate to parallel MI subprocess process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: parMiInstance.processInstanceKey,
      });
    });

    await test.step('Expand the parallel MI subprocess and wait for 3 active body instances', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /parallel mi subprocess \(multi instance\)/i,
          );
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(
              /^parallel mi subprocess$/i,
            ),
          ).toHaveCount(3);
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });

    await test.step('Enter modification mode', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessInstanceViewModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    await test.step('Click Task A in the diagram and verify multiple instances alert', async () => {
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        'SubTaskA_miPar',
      );
      await expect(
        operateProcessInstanceViewModificationModePage.multipleInstancesAlert,
      ).toBeVisible();
    });

    await test.step('Select one specific Task A instance from the instance history', async () => {
      await operateProcessInstancePage.expandTreeItemInHistory(
        /^parallel mi subprocess$/i,
      );
      await operateProcessInstancePage.instanceHistory
        .getByRole('treeitem', {name: /^task a$/i})
        .first()
        .click();
      await expect(
        page.getByTestId('popover').getByText('Selected running instances: 1'),
      ).toBeVisible();
    });

    await test.step('Initiate move for the selected Task A instance to Task B', async () => {
      await operateProcessInstanceViewModificationModePage.clickMoveInstanceButtononPopup();
      await expect(
        operateProcessInstanceViewModificationModePage.moveTokensMessage,
      ).toBeVisible();
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        'SubTaskB_miPar',
      );
    });

    await test.step('Verify modification overlays: -1 on Task A and +1 on Task B', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
            'SubTaskA_miPar',
            -1,
          );
          await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
            'SubTaskB_miPar',
            1,
          );
        },
        onFailure: async () => {},
      });
    });

    await test.step('Apply the modification', async () => {
      await operateProcessInstanceViewModificationModePage.applyChanges();
    });

    await test.step('Verify a new Task B instance appeared in the operation history', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /parallel mi subprocess \(multi instance\)/i,
          );
          const bodyInstances =
            operateProcessInstancePage.instanceHistory.getByRole('treeitem', {
              name: /^parallel mi subprocess$/i,
            });
          for (const bodyInstance of await bodyInstances.all()) {
            const isExpanded = await bodyInstance.getAttribute('aria-expanded');
            if (isExpanded === 'false') {
              await bodyInstance
                .locator('.cds--tree-parent-node__toggle')
                .click();
            }
          }
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/^task b$/i),
          ).toHaveCount(1);
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });
  });

  test('Should prevent moving elements from outside into a multi-instance body', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    await test.step('Navigate to the move restriction process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: restrictionInstance.processInstanceKey,
      });
    });

    await test.step('Wait for Outer Task and Inner Task to be simultaneously active', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /mi subprocess \(multi instance\)/i,
          );
          await operateProcessInstancePage.expandTreeItemInHistory(
            /^mi subprocess$/i,
          );
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/outer task/i),
          ).toBeVisible();
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/inner task/i),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });

    await test.step('Enter modification mode', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessInstanceViewModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    await test.step('Select Outer Task and initiate a move', async () => {
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        'OuterTask_restriction',
      );
      await operateProcessInstanceViewModificationModePage.clickMoveInstanceButtononPopup();
      await expect(
        operateProcessInstanceViewModificationModePage.moveTokensMessage,
      ).toBeVisible();
    });

    await test.step('Attempt to select Inner Task (inside MI body) as move target', async () => {
      await operateProcessInstanceViewModificationModePage
        .getFlowNode('SubTaskA_restriction')
        .first()
        .click({force: true, timeout: 20000});
    });

    await test.step('Verify the cross-boundary move was rejected: target selection prompt remains active', async () => {
      await expect(
        operateProcessInstanceViewModificationModePage.moveTokensMessage,
      ).toBeVisible();
    });
  });

  test('Should prevent moving an element from one MI subprocess body to a different MI subprocess body', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    await test.step('Navigate to cross-MI restriction process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: crossMiInstance.processInstanceKey,
      });
    });

    await test.step('Wait for Cross Task 1 and Cross Task 2 to be active in their respective MI bodies', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /mi subprocess 1 \(multi instance\)/i,
          );
          await operateProcessInstancePage.expandTreeItemInHistory(
            /^mi subprocess 1$/i,
          );
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/cross task 1/i),
          ).toBeVisible();
          await operateProcessInstancePage.expandTreeItemInHistory(
            /mi subprocess 2 \(multi instance\)/i,
          );
          await operateProcessInstancePage.expandTreeItemInHistory(
            /^mi subprocess 2$/i,
          );
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/cross task 2/i),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });

    await test.step('Enter modification mode', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessInstanceViewModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    await test.step('Click Cross Task 1 and verify multiple instances alert', async () => {
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        'CrossTask1',
      );
      await expect(
        operateProcessInstanceViewModificationModePage.multipleInstancesAlert,
      ).toBeVisible();
    });

    await test.step('Select a specific Cross Task 1 instance from the instance history', async () => {
      await operateProcessInstancePage.expandTreeItemInHistory(
        /^mi subprocess 1$/i,
      );
      await operateProcessInstancePage.instanceHistory
        .getByRole('treeitem', {name: /^cross task 1$/i})
        .first()
        .click();
      await expect(
        page.getByTestId('popover').getByText('Selected running instances: 1'),
      ).toBeVisible();
    });

    await test.step('Initiate move from Cross Task 1', async () => {
      await operateProcessInstanceViewModificationModePage.clickMoveInstanceButtononPopup();
      await expect(
        operateProcessInstanceViewModificationModePage.moveTokensMessage,
      ).toBeVisible();
    });

    await test.step('Attempt to select Cross Task 2 (inside a different MI body) as move target', async () => {
      await operateProcessInstanceViewModificationModePage
        .getFlowNode('CrossTask2')
        .first()
        .click({force: true, timeout: 20000});
    });

    await test.step('Verify the cross-MI move was rejected: target selection prompt remains active', async () => {
      await expect(
        operateProcessInstanceViewModificationModePage.moveTokensMessage,
      ).toBeVisible();
    });
  });

  test('Should move an element from inside an MI body to its parent scope', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    await test.step('Navigate to the outward-move process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: outwardMoveInstance.processInstanceKey,
      });
    });

    await test.step('Wait for Outer Task and Inner Task to be simultaneously active', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /mi subprocess \(multi instance\)/i,
          );
          await operateProcessInstancePage.expandTreeItemInHistory(
            /^mi subprocess$/i,
          );
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/outer task/i),
          ).toBeVisible();
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/inner task/i),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });

    await test.step('Enter modification mode', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessInstanceViewModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    await test.step('Click Inner Task and verify multiple instances alert', async () => {
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        'SubTaskA_restriction',
      );
      await expect(
        operateProcessInstanceViewModificationModePage.multipleInstancesAlert,
      ).toBeVisible();
    });

    await test.step('Select a specific Inner Task instance from the instance history', async () => {
      await operateProcessInstancePage.expandTreeItemInHistory(
        /^mi subprocess$/i,
      );
      await operateProcessInstancePage.instanceHistory
        .getByRole('treeitem', {name: /^inner task$/i})
        .first()
        .click();
      await expect(
        page.getByTestId('popover').getByText('Selected running instances: 1'),
      ).toBeVisible();
    });

    await test.step('Initiate move from Inner Task to Outer Task (parent scope)', async () => {
      await operateProcessInstanceViewModificationModePage.clickMoveInstanceButtononPopup();
      await expect(
        operateProcessInstanceViewModificationModePage.moveTokensMessage,
      ).toBeVisible();
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        'OuterTask_restriction',
      );
    });

    await test.step('Verify modification overlays: -1 on Inner Task and +1 on Outer Task', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
            'SubTaskA_restriction',
            -1,
          );
          await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
            'OuterTask_restriction',
            1,
          );
        },
        onFailure: async () => {},
      });
    });

    await test.step('Apply the modification', async () => {
      await operateProcessInstanceViewModificationModePage.applyChanges();
    });

    await test.step('Verify Outer Task is still active in the instance history after move', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/outer task/i),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });
  });

  test('Should move all instances of a parallel MI task to another node using move-all', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    await test.step('Navigate to the move-all process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: moveAllInstance.processInstanceKey,
      });
    });

    await test.step('Wait for Outer Task and 2 Inner Task instances to be active', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /mi subprocess \(multi instance\)/i,
          );
          await operateProcessInstancePage.expandTreeItemInHistory(
            /^mi subprocess$/i,
          );
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/outer task/i),
          ).toBeVisible();
          await expect(
            operateProcessInstancePage.instanceHistory.getByRole('treeitem', {
              name: /^mi subprocess$/i,
            }),
          ).toHaveCount(2);
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });

    await test.step('Enter modification mode', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessInstanceViewModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    await test.step('Click Inner Task and verify multiple instances alert', async () => {
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        'SubTaskA_restriction',
      );
      await expect(
        operateProcessInstanceViewModificationModePage.multipleInstancesAlert,
      ).toBeVisible();
    });

    await test.step('Move all Inner Task instances to Outer Task (parent scope)', async () => {
      await operateProcessInstanceViewModificationModePage.clickMoveAllButtononPopup();
      await expect(
        operateProcessInstanceViewModificationModePage.moveTokensMessage,
      ).toBeVisible();
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        'OuterTask_restriction',
      );
    });

    await test.step('Verify modification overlays: -2 on Inner Task and +2 on Outer Task', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
            'SubTaskA_restriction',
            -2,
          );
          await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
            'OuterTask_restriction',
            2,
          );
        },
        onFailure: async () => {},
      });
    });

    await test.step('Apply the modification', async () => {
      await operateProcessInstanceViewModificationModePage.applyChanges();
    });

    await test.step('Verify Outer Task is still active in the instance history after move', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/outer task/i),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });
  });

  test('Should cancel a single MI body token (negative: token removed from history)', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    await test.step('Navigate to the cancel-token process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: cancelTokenInstance.processInstanceKey,
      });
    });

    await test.step('Wait for 2 parallel MI body instances with Task A active', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /parallel mi subprocess \(multi instance\)/i,
          );
          await expect(
            operateProcessInstancePage.instanceHistory.getByRole('treeitem', {
              name: /^parallel mi subprocess$/i,
            }),
          ).toHaveCount(2);
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });

    await test.step('Enter modification mode', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessInstanceViewModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    await test.step('Click Task A and verify multiple instances alert', async () => {
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        'SubTaskA_miPar',
      );
      await expect(
        operateProcessInstanceViewModificationModePage.multipleInstancesAlert,
      ).toBeVisible();
    });

    await test.step('Select one specific Task A instance from the instance history', async () => {
      await operateProcessInstancePage.expandTreeItemInHistory(
        /^parallel mi subprocess$/i,
      );
      await operateProcessInstancePage.instanceHistory
        .getByRole('treeitem', {name: /^task a$/i})
        .first()
        .click();
      await expect(
        page.getByTestId('popover').getByText('Selected running instances: 1'),
      ).toBeVisible();
    });

    await test.step('Cancel the selected Task A instance', async () => {
      await operateProcessInstanceViewModificationModePage.cancelButtonPopup.click();
    });

    await test.step('Verify -1 cancel overlay on Task A', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
            'SubTaskA_miPar',
            -1,
          );
        },
        onFailure: async () => {},
      });
    });

    await test.step('Apply the modification', async () => {
      await operateProcessInstanceViewModificationModePage.applyChanges();
    });

    await test.step('Verify only one Task A instance remains active in the instance history', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /parallel mi subprocess \(multi instance\)/i,
          );
          const bodyInstances =
            operateProcessInstancePage.instanceHistory.getByRole('treeitem', {
              name: /^parallel mi subprocess$/i,
            });
          for (const bodyInstance of await bodyInstances.all()) {
            const isExpanded = await bodyInstance.getAttribute('aria-expanded');
            if (isExpanded === 'false') {
              await bodyInstance
                .locator('.cds--tree-parent-node__toggle')
                .click();
            }
          }
          // History shows both active and terminated instances; filter by the
          // ACTIVE-icon state indicator rendered in the Bar component to count
          // only the remaining running Task A token.
          await expect(
            operateProcessInstancePage
              .findTreeItemInHistory(/^task a$/i)
              .filter({has: page.getByTestId('ACTIVE-icon')}),
          ).toHaveCount(1);
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });
  });

  test('Should add a new token inside a sequential MI body', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    await test.step('Navigate to the add-token process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: addTokenInstance.processInstanceKey,
      });
    });

    await test.step('Wait for Task A to be active inside the sequential MI body', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /sequential mi subprocess \(multi instance\)/i,
          );
          await operateProcessInstancePage.expandTreeItemInHistory(
            /^sequential mi subprocess$/i,
          );
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/task a/i),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });

    await test.step('Enter modification mode', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessInstanceViewModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    await test.step('Click Task B and add a new token to it', async () => {
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        'SubTaskB_miSeq',
      );
      await operateProcessInstanceViewModificationModePage.clickAddModificationButtononPopup();
    });

    await test.step('Verify +1 add overlay on Task B', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
            'SubTaskB_miSeq',
            1,
          );
        },
        onFailure: async () => {},
      });
    });

    await test.step('Apply the modification', async () => {
      await operateProcessInstanceViewModificationModePage.applyChanges();
    });

    await test.step('Verify Task B is now active in the instance history', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /sequential mi subprocess \(multi instance\)/i,
          );
          await operateProcessInstancePage.expandTreeItemInHistory(
            /^sequential mi subprocess$/i,
          );
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/task b/i),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });
  });

  test('Should prevent move and cancel actions on a terminated MI body task', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    await test.step('Navigate to the single-item MI subprocess process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: completedMiTaskInstance.processInstanceKey,
      });
    });

    await test.step('Wait for Outer Task and Inner Task to be simultaneously active', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /mi subprocess \(multi instance\)/i,
          );
          await operateProcessInstancePage.expandTreeItemInHistory(
            /^mi subprocess$/i,
          );
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/outer task/i),
          ).toBeVisible();
          await expect(
            operateProcessInstancePage.findTreeItemInHistory(/inner task/i),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });

    await test.step('Enter modification mode and cancel the single Inner Task token', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessInstanceViewModificationModePage.modifyModeHeader,
      ).toBeVisible();
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        'SubTaskA_restriction',
      );
      await operateProcessInstanceViewModificationModePage.cancelButtonPopup.click();
    });

    await test.step('Verify -1 cancel overlay on Inner Task', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
            'SubTaskA_restriction',
            -1,
          );
        },
        onFailure: async () => {},
      });
    });

    await test.step('Apply the modification', async () => {
      await operateProcessInstanceViewModificationModePage.applyChanges();
    });

    await test.step('Wait until Inner Task shows as terminated in the instance history', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateProcessInstancePage.expandTreeItemInHistory(
            /mi subprocess \(multi instance\)/i,
          );
          await operateProcessInstancePage.expandTreeItemInHistory(
            /^mi subprocess$/i,
          );
          await expect(
            operateProcessInstancePage
              .findTreeItemInHistory(/inner task/i)
              .filter({has: page.getByTestId('TERMINATED-icon')}),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await hideHelperModals(page);
        },
      });
    });

    await test.step('Re-enter modification mode', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(
        operateProcessInstanceViewModificationModePage.modifyModeHeader,
      ).toBeVisible();
    });

    await test.step('Click the terminated Inner Task node in the diagram', async () => {
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        'SubTaskA_restriction',
      );
    });

    await test.step('Verify no cancel or move actions are available for the terminated flow node', async () => {
      await expect(
        operateProcessInstanceViewModificationModePage.cancelButtonPopup,
      ).not.toBeVisible();
      await expect(
        operateProcessInstanceViewModificationModePage.moveSelectedInstanceButton,
      ).not.toBeVisible();
    });

    await test.step('Verify Add is also not available for the terminated flow node', async () => {
      await expect(
        operateProcessInstanceViewModificationModePage.addModificationButtononPopup,
      ).not.toBeVisible();
    });
  });
});
