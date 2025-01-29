import {test as base} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {LoginPage} from '@pages/8.4/LoginPage';
import {HomePage} from '@pages/8.4/HomePage';
import {ClusterPage} from '@pages/8.4/ClusterPage';
import {AppsPage} from '@pages/8.4/AppsPage';
import {ModelerHomePage} from '@pages/8.4/ModelerHomePage';
import {ModelerCreatePage} from '@pages/8.4/ModelerCreatePage';
import {TaskPanelPage} from '@pages/8.4/TaskPanelPage';
import {TaskDetailsPage} from '@pages/8.4/TaskDetailsPage';
import {OperateHomePage} from '@pages/8.4/OperateHomePage';
import {OperateProcessesPage} from '@pages/8.4/OperateProcessesPage';
import {ClusterDetailsPage} from '@pages/8.4/ClusterDetailsPage';
import {OperateProcessInstancePage} from '@pages/8.4/OperateProcessInstancePage';
import {OptimizeCollectionsPage} from '@pages/8.4/OptimizeCollectionsPage';
import {OptimizeHomePage} from '@pages/8.4/OptimizeHomePage';
import {FormJsPage} from '@pages/8.4/FormJsPage';
import {OptimizeReportPage} from '@pages/8.4/OptimizeReportPage';
import {ConsoleOrganizationPage} from '@pages/8.4/ConsoleOrganizationPage';
import {SettingsPage} from '@pages/8.4/SettingsPage';
import {ConnectorSettingsPage} from '@pages/8.4/ConnectorSettingsPage';
import {ClusterSecretsPage} from '@pages/8.4/ClusterSecretsPage';
import {TaskProcessesPage} from '@pages/8.4/TaskProcessesPage';
import {ConnectorMarketplacePage} from '@pages/8.4/ConnectorMarketplacePage';
import {ModelerUserInvitePage} from '@pages/8.4/ModelerUserInvitePage';
import {SignUpPage} from '@pages/8.4/SignUpPage';

type PlaywrightFixtures = {
  makeAxeBuilder: () => AxeBuilder;
  loginPage: LoginPage;
  homePage: HomePage;
  clusterPage: ClusterPage;
  appsPage: AppsPage;
  modelerHomePage: ModelerHomePage;
  modelerCreatePage: ModelerCreatePage;
  taskPanelPage: TaskPanelPage;
  taskDetailsPage: TaskDetailsPage;
  operateHomePage: OperateHomePage;
  operateProcessesPage: OperateProcessesPage;
  clusterDetailsPage: ClusterDetailsPage;
  optimizeCollectionsPage: OptimizeCollectionsPage;
  operateProcessInstancePage: OperateProcessInstancePage;
  optimizeHomePage: OptimizeHomePage;
  formJsPage: FormJsPage;
  optimizeReportPage: OptimizeReportPage;
  consoleOrganizationsPage: ConsoleOrganizationPage;
  settingsPage: SettingsPage;
  connectorSettingsPage: ConnectorSettingsPage;
  clusterSecretsPage: ClusterSecretsPage;
  taskProcessesPage: TaskProcessesPage;
  overrideTrackingScripts: void;
  connectorMarketplacePage: ConnectorMarketplacePage;
  modelerUserInvitePage: ModelerUserInvitePage;
  signUpPage: SignUpPage;
};

const test = base.extend<PlaywrightFixtures>({
  makeAxeBuilder: async ({page}, use) => {
    const makeAxeBuilder = () =>
      new AxeBuilder({page}).withTags([
        'best-practice',
        'wcag2a',
        'wcag2aa',
        'cat.semantics',
        'cat.forms',
      ]);

    await use(makeAxeBuilder);
  },
  loginPage: async ({page}, use) => {
    await use(new LoginPage(page));
  },
  homePage: async ({page}, use) => {
    await use(new HomePage(page));
  },
  clusterPage: async ({page}, use) => {
    await use(new ClusterPage(page));
  },
  appsPage: async ({page}, use) => {
    await use(new AppsPage(page));
  },
  modelerHomePage: async ({page}, use) => {
    await use(new ModelerHomePage(page));
  },
  modelerCreatePage: async ({page}, use) => {
    await use(new ModelerCreatePage(page));
  },
  taskPanelPage: async ({page}, use) => {
    await use(new TaskPanelPage(page));
  },
  taskDetailsPage: async ({page}, use) => {
    await use(new TaskDetailsPage(page));
  },
  operateHomePage: async ({page}, use) => {
    await use(new OperateHomePage(page));
  },
  operateProcessesPage: async ({page}, use) => {
    await use(new OperateProcessesPage(page));
  },
  clusterDetailsPage: async ({page}, use) => {
    await use(new ClusterDetailsPage(page));
  },
  operateProcessInstancePage: async ({page}, use) => {
    await use(new OperateProcessInstancePage(page));
  },
  optimizeCollectionsPage: async ({page}, use) => {
    await use(new OptimizeCollectionsPage(page));
  },
  optimizeHomePage: async ({page}, use) => {
    await use(new OptimizeHomePage(page));
  },
  formJsPage: async ({page}, use) => {
    await use(new FormJsPage(page));
  },
  optimizeReportPage: async ({page}, use) => {
    await use(new OptimizeReportPage(page));
  },
  consoleOrganizationsPage: async ({page}, use) => {
    await use(new ConsoleOrganizationPage(page));
  },
  settingsPage: async ({page}, use) => {
    await use(new SettingsPage(page));
  },
  connectorSettingsPage: async ({page}, use) => {
    await use(new ConnectorSettingsPage(page));
  },
  clusterSecretsPage: async ({page}, use) => {
    await use(new ClusterSecretsPage(page));
  },
  taskProcessesPage: async ({page}, use) => {
    await use(new TaskProcessesPage(page));
  },
  connectorMarketplacePage: async ({page}, use) => {
    await use(new ConnectorMarketplacePage(page));
  },
  modelerUserInvitePage: async ({page}, use) => {
    await use(new ModelerUserInvitePage(page));
  },
  signUpPage: async ({page}, use) => {
    await use(new SignUpPage(page));
  },
  overrideTrackingScripts: [
    async ({context}, use) => {
      await context.route(
        'https://cmp.osano.com/16CVvwSNKHi9t1grQ/9403708a-488b-4f3b-aea6-613825dec79f/osano.js',
        (route) =>
          route.fulfill({
            status: 200,
            headers: {
              'Content-Type': 'text/javascript;charset=UTF-8',
            },
            body: '',
          }),
      );
      await context.route(
        'https://cmp.osano.com/16CVvwSNKHi9t1grQ/82b4a594-aa1b-41df-b4f1-fa858971ebe8/osano.js',
        (route) =>
          route.fulfill({
            status: 200,
            headers: {
              'Content-Type': 'text/javascript;charset=UTF-8',
            },
            body: '',
          }),
      );
      await context.route(
        'https://cmp.osano.com/16CVvwSNKHi9t1grQ/2ce963c0-31c9-4b54-b052-d66a2a948ccc/osano-ui.js',
        (route) =>
          route.fulfill({
            status: 200,
            headers: {
              'Content-Type': 'text/javascript;charset=UTF-8',
            },
            body: '',
          }),
      );
      await use();
    },
    {
      auto: true,
    },
  ],
});

export {test};
