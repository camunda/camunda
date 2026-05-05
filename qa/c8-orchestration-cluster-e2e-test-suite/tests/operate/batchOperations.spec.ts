/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {
  cancelBatchOperation,
  createCancellationBatch,
  expectBatchState,
  findCompletedBatchKey,
  suspendBatchOperation,
} from '@requestHelpers';
import {waitForAssertion} from 'utils/waitForAssertion';

test.beforeAll(async () => {
  await deploy([
    './resources/batch_cancellation_process.bpmn',
    './resources/batch_suspension_process.bpmn',
    './resources/batch_suspension_long_process.bpmn',
  ]);
});

test.describe('Batch Operations', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Navigate to batch detail page by clicking a list row link', async ({
    page,
    request,
    operateBatchOperationsPage,
    operateOperationsDetailsPage,
  }) => {
    test.slow();
    await createCancellationBatch(request);

    await test.step('Navigate to batch operations list', async () => {
      await operateBatchOperationsPage.goto();
      await expect(operateBatchOperationsPage.heading).toBeVisible();
    });

    const firstLink = operateBatchOperationsPage.table
      .getByRole('link')
      .first();

    await test.step('Click the first batch operation row link', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(firstLink).toBeVisible({timeout: 30000});
        },
        onFailure: async () => {
          await page.reload();
          await expect(operateBatchOperationsPage.heading).toBeVisible();
        },
      });
      await firstLink.click();
    });

    await test.step('Verify navigation to detail page', async () => {
      await expect(page).toHaveURL(/\/operate\/batch-operations\/\d+/);
      await expect(operateOperationsDetailsPage.backButton).toBeVisible();
    });
  });

  test('Navigate to process instance from a link in the batch items table', async ({
    page,
    request,
    operateOperationsDetailsPage,
  }) => {
    test.slow();
    const batchKey = await findCompletedBatchKey(request);
    await operateOperationsDetailsPage.goto(batchKey);

    await test.step('Wait for items table to load a process instance link', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateOperationsDetailsPage.itemsTable.getByRole('link').first(),
          ).toBeVisible({timeout: 20000});
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    const firstLink = operateOperationsDetailsPage.itemsTable
      .getByRole('link')
      .first();
    const href = await firstLink.getAttribute('href');
    await firstLink.click();

    await test.step('Verify navigation to process instance page', async () => {
      await expect(page).toHaveURL(new RegExp(`/operate/processes/\\d+`));
      expect(href).toMatch(/\/processes\/\d+/);
    });
  });

  test('Navigate to Batch Operations list via the Operations header menu', async ({
    page,
    operateHomePage,
    operateBatchOperationsPage,
  }) => {
    await test.step('Open the Operations menu and click Batch operations', async () => {
      await operateHomePage.operationsMenuButton.click();
      await operateHomePage.batchOperationsNavItem.click();
    });

    await test.step('Verify Batch Operations list page loads', async () => {
      await expect(page).toHaveURL(/\/operate\/batch-operations/);
      await expect(operateBatchOperationsPage.heading).toBeVisible();
    });
  });

  test('Update URL sort parameter when clicking the Operation column header', async ({
    page,
    operateBatchOperationsPage,
  }) => {
    await operateBatchOperationsPage.goto();
    await expect(operateBatchOperationsPage.heading).toBeVisible();

    await test.step('Click Operation column header for the first time', async () => {
      await operateBatchOperationsPage.operationColumnHeader.click();
      await expect(page).toHaveURL(/sort=operationType%2Bdesc/);
    });

    await test.step('Click Operation column header again to toggle sort order', async () => {
      await operateBatchOperationsPage.operationColumnHeader.click();
      await expect(page).toHaveURL(/sort=operationType%2Basc/);
    });
  });

  test('Update URL sort parameter when clicking the Actor column header', async ({
    page,
    operateBatchOperationsPage,
  }) => {
    await operateBatchOperationsPage.goto();
    await expect(operateBatchOperationsPage.heading).toBeVisible();

    await operateBatchOperationsPage.actorColumnHeader.click();

    await expect(page).toHaveURL(/sort=actorId%2Bdesc/);
  });

  test('Restore sort order from URL parameters on page reload', async ({
    page,
    operateBatchOperationsPage,
  }) => {
    await operateBatchOperationsPage.goto({
      searchParams: {sort: 'operationType+asc'},
    });
    await expect(operateBatchOperationsPage.heading).toBeVisible();

    await page.reload();

    await expect(operateBatchOperationsPage.heading).toBeVisible();
    await expect(page).toHaveURL(/sort=operationType%2Basc/);
  });

  test('Suspend an active batch operation via the Suspend button', async ({
    page,
    request,
    operateOperationsDetailsPage,
  }) => {
    test.slow();
    const batchKey = await createCancellationBatch(
      request,
      2000,
      'batch_suspension_long_process',
    );
    await operateOperationsDetailsPage.goto(batchKey);

    await test.step('Click Suspend and wait for state change', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateOperationsDetailsPage.suspendButton).toBeVisible({
            timeout: 20000,
          });
          await expect(
            operateOperationsDetailsPage.suspendButton,
          ).toBeEnabled();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await operateOperationsDetailsPage.suspendButton.click();
      // Wait for the UI action to complete - no need to also suspend via API
      await expectBatchState(request, batchKey, 'SUSPENDED');
    });

    await test.step('Verify state indicator shows Suspended', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateOperationsDetailsPage.state).toContainText(
            'Suspended',
            {timeout: 15000},
          );
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await expect(operateOperationsDetailsPage.resumeButton).toBeVisible();
      await expect(operateOperationsDetailsPage.suspendButton).toBeHidden();
    });
  });

  test('Cancel an active batch operation via the Options menu', async ({
    page,
    request,
    operateOperationsDetailsPage,
  }) => {
    test.slow();
    const batchKey = await createCancellationBatch(
      request,
      2000,
      'batch_suspension_long_process',
    );
    await operateOperationsDetailsPage.goto(batchKey);

    await test.step('Click Options > Cancel and wait for state change', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateOperationsDetailsPage.suspendButton).toBeVisible({
            timeout: 20000,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await operateOperationsDetailsPage.clickCancelFromOptionsMenu();
      // Wait for the UI action to complete - no need to also cancel via API
      await expectBatchState(request, batchKey, 'CANCELED');
    });

    await test.step('Verify state indicator shows Canceled', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateOperationsDetailsPage.state).toContainText(
            'Canceled',
            {timeout: 15000},
          );
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await expect(operateOperationsDetailsPage.suspendButton).toBeHidden();
      await expect(operateOperationsDetailsPage.resumeButton).toBeHidden();
    });
  });

  test('Cancel a suspended batch operation via the Options menu', async ({
    page,
    request,
    operateOperationsDetailsPage,
  }) => {
    test.slow();
    const batchKey = await createCancellationBatch(
      request,
      200,
      'batch_suspension_process',
    );
    await suspendBatchOperation(request, batchKey);
    await expectBatchState(request, batchKey, 'SUSPENDED');

    await operateOperationsDetailsPage.goto(batchKey);

    await test.step('Wait for Suspended state and click Options > Cancel', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateOperationsDetailsPage.state).toContainText(
            'Suspended',
            {timeout: 20000},
          );
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await operateOperationsDetailsPage.clickCancelFromOptionsMenu();
    });

    await test.step('Verify state indicator shows Canceled', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateOperationsDetailsPage.state).toContainText(
            'Canceled',
            {timeout: 15000},
          );
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await expect(operateOperationsDetailsPage.resumeButton).toBeHidden();
      await expect(operateOperationsDetailsPage.optionsMenuButton).toBeHidden();
    });
  });

  test('Display batch state, item summary, start date and actor tiles on the detail page', async ({
    page,
    request,
    operateOperationsDetailsPage,
  }) => {
    test.slow();
    const batchKey = await createCancellationBatch(request);
    await operateOperationsDetailsPage.goto(batchKey);

    await test.step('Verify all metadata tiles are visible', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateOperationsDetailsPage.state).toBeVisible({
            timeout: 20000,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await expect(operateOperationsDetailsPage.summaryTile).toBeVisible();
      await expect(operateOperationsDetailsPage.startDateTile).toBeVisible();
      await expect(operateOperationsDetailsPage.actorTile).toBeVisible();
    });
  });

  test('Return to Batch Operations list when back button is clicked', async ({
    page,
    request,
    operateOperationsDetailsPage,
  }) => {
    test.slow();
    const batchKey = await createCancellationBatch(request);
    await operateOperationsDetailsPage.goto(batchKey);
    await expect(operateOperationsDetailsPage.backButton).toBeVisible({
      timeout: 20000,
    });

    await operateOperationsDetailsPage.backButton.click();

    await expect(page).toHaveURL(/\/operate\/batch-operations$/);
  });

  test('Display all expected column headers in the Batch Operations list', async ({
    operateBatchOperationsPage,
  }) => {
    await operateBatchOperationsPage.goto();
    await expect(operateBatchOperationsPage.heading).toBeVisible();

    await test.step('Verify all five column headers are visible', async () => {
      await expect(
        operateBatchOperationsPage.operationColumnHeader,
      ).toBeVisible();
      await expect(
        operateBatchOperationsPage.batchStateColumnHeader,
      ).toBeVisible();
      await expect(operateBatchOperationsPage.itemsColumnHeader).toBeVisible();
      await expect(operateBatchOperationsPage.actorColumnHeader).toBeVisible();
      await expect(
        operateBatchOperationsPage.startDateColumnHeader,
      ).toBeVisible();
    });
  });

  test('Update URL sort parameter when clicking the Start date column header', async ({
    page,
    operateBatchOperationsPage,
  }) => {
    await operateBatchOperationsPage.goto();
    await expect(operateBatchOperationsPage.heading).toBeVisible();

    await operateBatchOperationsPage.startDateColumnHeader.click();

    await expect(page).toHaveURL(/sort=startDate%2Bdesc/);
  });

  test('Hide lifecycle action buttons when a batch reaches Completed state', async ({
    request,
    operateOperationsDetailsPage,
  }) => {
    test.slow();
    const batchKey = await findCompletedBatchKey(request);
    await operateOperationsDetailsPage.goto(batchKey);

    await test.step('Verify Completed state and no action buttons are present', async () => {
      await expect(operateOperationsDetailsPage.state).toContainText(
        'Completed',
        {timeout: 20000},
      );
      await expect(operateOperationsDetailsPage.suspendButton).toBeHidden();
      await expect(operateOperationsDetailsPage.resumeButton).toBeHidden();
      await expect(operateOperationsDetailsPage.optionsMenuButton).toBeHidden();
    });
  });
});
