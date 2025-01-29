import {test} from '@fixtures/8.6';
import {navigateToApp} from '@pages/c8Run-8.6/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {createInstances, deploy} from 'utils/zeebeClient';
import {expect} from '@playwright/test';
import {sleep} from 'utils/sleep';

test.beforeAll(async () => {
  await deploy('./resources/Basic_Auth_REST_Connector.bpmn');
  await deploy(
    './resources/Intermediate_Event_Webhook_Connector_No_Auth_Process.bpmn',
  );
  await deploy(
    './resources/Start_Event_Webhook_Connector_No_Auth_Process.bpmn',
  );
  await deploy('./resources/REST_Connector_Bearer_Auth_Process.bpmn');
  await deploy('./resources/REST_Connector_No_Auth_Process.bpmn');
  await createInstances('REST_Connector_Basic_Auth_Process', 1, 1);
  await createInstances(
    'Intermediate_Event_Webhook_Connector_No_Auth_Process',
    1,
    1,
  );
  await createInstances('Start_Event_Webhook_Connector_No_Auth_Process', 1, 1);
  await createInstances('REST_Connector_No_Auth_Process', 1, 1);
  await createInstances('REST_Connector_Bearer_Auth_Process', 1, 1);
});

test.beforeEach(async ({page, operateLoginPage, operateHomePage}) => {
  await navigateToApp(page, 'operate');
  await operateLoginPage.login('demo', 'demo');
  await operateHomePage.operateBannerIsVisible();
});

test.describe('Connectors User Flow Tests', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('REST Connector No Auth User Flow', async ({
    operateHomePage,
    operateProcessInstancePage,
    operateProcessesPage,
  }) => {
    await test.step('View Process Instance in Operate, assert it completes and assert result expression', async () => {
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'REST_Connector_No_Auth_Process',
      );
      await operateProcessInstancePage.completedIconAssertion();

      await expect(operateProcessInstancePage.statusVariable).toBeVisible({
        timeout: 60000,
      });

      await expect(
        operateProcessInstancePage.statusVariable.getByText('"Awesome!"'),
      ).toBeVisible({
        timeout: 60000,
      });
    });
  });

  test('REST Connector Basic Auth User Flow', async ({
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
  }) => {
    await test.step('View Process Instance in Operate, assert it completes and assert result expression', async () => {
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'REST_Connector_Basic_Auth_Process',
      );

      await operateProcessInstancePage.completedIconAssertion();

      await expect(operateProcessInstancePage.variablesList).toBeVisible({
        timeout: 60000,
      });

      await expect(
        operateProcessInstancePage.messageVariable.getByText('"Awesome!"'),
      ).toBeVisible({
        timeout: 60000,
      });
    });
  });

  test('REST Connector Bearer Token Auth User Flow', async ({
    operateHomePage,
    operateProcessInstancePage,
    operateProcessesPage,
  }) => {
    await test.step('View Process Instance in Operate, assert it completes and assert result expression', async () => {
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'REST_Connector_Bearer_Auth_Process',
      );
      await operateProcessInstancePage.completedIconAssertion();

      await expect(operateProcessInstancePage.variablesList).toBeVisible({
        timeout: 60000,
      });

      await expect(
        operateProcessInstancePage.messageVariable.getByText('"Awesome!"'),
      ).toBeVisible({
        timeout: 60000,
      });
    });
  });

  // Test will be skipped until CI bug regarding inbound connector endpoint is resolved
  test.skip('Start Event Webhook Connector No Auth User Flow', async ({
    page,
    request,
    operateHomePage,
    operateProcessInstancePage,
    operateProcessesPage,
  }) => {
    await test.step('Make Authorization Request', async () => {
      await sleep(300000);
      const response = await request.get(
        process.env.C8RUN_CONNECTORS_API_URL + '/inbound/test-webhook-id',
      );

      await expect(response.status()).toBe(200);
    });

    await test.step('Assert Diagram Has Successfully Completed in Operate', async () => {
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      await operateHomePage.clickProcessesTab();
      await page.reload();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'Start_Event_Webhook_Connector_No_Auth_Process',
      );
      await operateProcessInstancePage.completedIconAssertion();
    });
  });

  // Test will be skipped until CI bug regarding inbound connector endpoint is resolved
  test.skip('Intermediate Event Webhook Connector No Auth User Flow', async ({
    page,
    request,
    operateHomePage,
    operateProcessInstancePage,
    operateProcessesPage,
  }) => {
    await test.step('Make Authorization Request', async () => {
      await sleep(60000);
      const response = await request.post(
        process.env.C8RUN_CONNECTORS_API_URL +
          '/inbound/test-webhook-intermediate',
        {
          data: {
            test: 'test',
          },
        },
      );

      await expect(response.status()).toBe(200);
    });

    await test.step('Assert Diagram Has Successfully Completed in Operate', async () => {
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      await operateHomePage.clickProcessesTab();
      await page.reload();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'Intermediate_Event_Webhook_Connector_No_Auth_Process',
      );
      await operateProcessInstancePage.completedIconAssertion();
    });
  });
});
