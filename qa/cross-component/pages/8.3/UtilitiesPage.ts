import {Page, expect} from '@playwright/test';
import {ModelerCreatePage} from './ModelerCreatePage';
import {ModelerHomePage} from './ModelerHomePage';
import {MailSlurp} from 'mailslurp-client';
import {HomePage} from './HomePage';
import {ClusterPage} from './ClusterPage';
import {ClusterDetailsPage} from './ClusterDetailsPage';
import {AppsPage} from './AppsPage';
import {sleep} from '../../utils/sleep';

export async function assertPageTextWithRetry(
  page: Page,
  text: string,
  timeout: number = 120000,
  maxRetries: number = 3,
) {
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      await page.reload();
      await expect(page.getByText(text, {exact: true}).first()).toBeVisible({
        timeout: timeout,
      });
      return;
    } catch (error) {
      if (attempt < maxRetries - 1) {
        console.warn(
          `Attempt ${attempt + 1} failed for asserting ${text}. Retrying...`,
        );
      } else {
        throw new Error(`Assertion failed after ${maxRetries} attempts`);
      }
    }
  }
}

export async function runExistingDiagramOrCreate(
  page: Page,
  modelerHomePage: ModelerHomePage,
  modelerCreatePage: ModelerCreatePage,
  processName: string,
  processId: string,
  rbaEnabled: boolean,
) {
  try {
    await runExistingDiagram(
      page,
      modelerHomePage,
      modelerCreatePage,
      processName,
      rbaEnabled,
    );
  } catch (error) {
    console.info(
      `Creating a new process with name(${processName}) and id(${processId})`,
    );
    await deploySingleProcess(
      page,
      modelerHomePage,
      modelerCreatePage,
      processName,
      processId,
      true,
      rbaEnabled,
    );
  }
}

export async function runExistingDiagram(
  page: Page,
  modelerHomePage: ModelerHomePage,
  modelerCreatePage: ModelerCreatePage,
  processName: string,
  rbaEnabled: boolean,
) {
  await modelerHomePage.clickProcessDiagram(processName);
  await modelerCreatePage.assertThreeElementsVisible();
  await modelerCreatePage.deployDiagram(rbaEnabled);
  await modelerCreatePage.clickStartInstanceMainButton();
  await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
    timeout: 120000,
  });
  await modelerCreatePage.clickStartInstanceSubButton();
  await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
    timeout: 180000,
  });
  await expect(modelerCreatePage.viewProcessInstanceLink).toBeHidden({
    timeout: 120000,
  });
  await modelerHomePage.clickProjectBreadcrumb();
}

export async function deploySingleProcess(
  page: Page,
  modelerHomePage: ModelerHomePage,
  modelerCreatePage: ModelerCreatePage,
  processName: string,
  processId: string = '',
  changeProcessId: boolean = false,
  rbaEnabled: boolean = false,
) {
  await modelerHomePage.clickDiagramTypeDropdown();
  await modelerHomePage.clickBpmnTemplateOption();

  await expect(modelerCreatePage.generalPanel).toBeVisible({
    timeout: 180000,
  });

  await modelerCreatePage.enterDiagramName(processName);
  await sleep(10000);
  await modelerCreatePage.clickAppendElementButton();
  await modelerCreatePage.clickAppendTaskButton();
  await modelerCreatePage.clickChangeTypeButton();
  await modelerCreatePage.clickUserTaskOption();
  await modelerCreatePage.clickAppendElementButton();
  await modelerCreatePage.clickAppendEndEventButton();
  await modelerCreatePage.clickCanvas();
  await sleep(10000);
  await modelerCreatePage.assertThreeElementsVisible();
  if (changeProcessId) {
    try {
      await modelerCreatePage.clickProcessIdInput();
      await modelerCreatePage.fillProcessIdInput(processId);
    } catch (error) {
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickProcessIdInput();
      await modelerCreatePage.fillProcessIdInput(processId);
    }
  }
  await modelerCreatePage.clickCanvas();
  await sleep(10000);
  await modelerCreatePage.deployDiagram(rbaEnabled);
  await sleep(3000);
  await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
    timeout: 60000,
  });
  await modelerCreatePage.clickStartInstanceMainButton();
  await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
    timeout: 120000,
  });
  await modelerCreatePage.clickStartInstanceSubButton();
  await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
    timeout: 180000,
  });
  await expect(modelerCreatePage.viewProcessInstanceLink).toBeHidden({
    timeout: 120000,
  });
  await modelerHomePage.clickProjectBreadcrumb();
}

export async function deployMultipleProcesses(
  page: Page,
  modelerHomePage: ModelerHomePage,
  modelerCreatePage: ModelerCreatePage,
  numberOfProcesses: number,
  processName: string,
  processId: string = '',
  changeProcessId: boolean = false,
  rbaEnabled: boolean = false,
) {
  for (let i = 1; i <= numberOfProcesses; i++) {
    await sleep(10000);
    await deploySingleProcess(
      page,
      modelerHomePage,
      modelerCreatePage,
      processName + i,
      processId + i,
      changeProcessId,
      rbaEnabled,
    );
  }
}

export async function clickInvitationLinkInEmail(
  page: Page,
  id: string,
  mailSlurp: MailSlurp,
): Promise<void> {
  const email = await mailSlurp.waitForLatestEmail(id, 120000);

  if (email && email.body) {
    const confirmationLinkMatch = email.body.match(/href="([^"]*)/);
    if (confirmationLinkMatch) {
      await page.goto(confirmationLinkMatch[1]);
    } else {
      throw new Error('Confirmation link not found in email body.');
    }
  } else {
    throw new Error('Email or email body is null or undefined.');
  }
}

export async function assertTestUsesCorrectOrganization(
  page: Page,
): Promise<void> {
  const homePage = new HomePage(page);
  await expect(homePage.openOrganizationButton).toBeVisible({timeout: 180000});

  try {
    await expect(page.url()).toContain(process.env.ORG_ID!);
  } catch (error) {
    try {
      await homePage.clickOpenOrganizationButton();
      await expect(
        page.getByTitle(process.env.ORG_NAME!, {exact: true}),
      ).toBeVisible({
        timeout: 60000,
      });
    } catch (reloadError) {
      // If the second check fails, perform button clicking actions
      if (page.url().includes('modeler')) {
        await page
          .getByRole('button', {name: process.env.ORG_NAME!})
          .click({timeout: 90000});
      } else {
        await page
          .getByRole('treeitem', {name: process.env.ORG_NAME!, exact: true})
          .click({timeout: 90000});
      }
      // Finally, check if the page URL contains the organization ID
      await expect(page.url()).toContain(process.env.ORG_ID!);
    }
  }
}

export async function disableRBAForNewTab(
  page: Page,
  homePage: HomePage,
  clusterPage: ClusterPage,
  clusterDetailsPage: ClusterDetailsPage,
  appsPage: AppsPage,
) {
  await homePage.clickCamundaApps();
  await appsPage.clickConsoleLink();
  const consoleTabPromise = page.waitForEvent('popup');
  const consoleTab = await consoleTabPromise;
  homePage = new HomePage(consoleTab);
  clusterDetailsPage = new ClusterDetailsPage(consoleTab);
  clusterPage = new ClusterPage(consoleTab);
  await doDisableRBA(homePage, clusterPage, clusterDetailsPage);
}

export async function disableRBA(
  homePage: HomePage,
  clusterPage: ClusterPage,
  clusterDetailsPage: ClusterDetailsPage,
  appsPage: AppsPage,
) {
  await homePage.clickCamundaComponents();
  await appsPage.clickConsoleLink();
  await expect(homePage.clusterTab).toBeVisible({timeout: 60000});
  await homePage.clickClusters();
  await clusterPage.clickTestClusterLink();
  await doDisableRBA(homePage, clusterPage, clusterDetailsPage);
}

async function doDisableRBA(
  homePage: HomePage,
  clusterPage: ClusterPage,
  clusterDetailsPage: ClusterDetailsPage,
) {
  await expect(clusterDetailsPage.settingsTab).toBeVisible({
    timeout: 90000,
  });
  await clusterDetailsPage.clickSettingsTab();
  await sleep(3000);
  await clusterDetailsPage.disableRBA();
  await homePage.clickClusters();
  await clusterPage.assertClusterHealthyStatusWithRetry();
}

export async function enableRBA(
  homePage: HomePage,
  clusterPage: ClusterPage,
  clusterDetailsPage: ClusterDetailsPage,
  appsPage: AppsPage,
) {
  await homePage.clickCamundaComponents();
  await appsPage.clickConsoleLink();
  await expect(homePage.clusterTab).toBeVisible({timeout: 120000});
  await homePage.clickClusters();
  await clusterPage.clickTestClusterLink();
  await expect(clusterDetailsPage.settingsTab).toBeVisible({
    timeout: 90000,
  });
  await clusterDetailsPage.clickSettingsTab();
  await clusterDetailsPage.enableRBA();
  await homePage.clickClusters();
  await clusterPage.assertClusterHealthyStatusWithRetry();
}
