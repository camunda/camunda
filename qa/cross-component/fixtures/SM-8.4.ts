import {test as base} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {LoginPage} from '@pages/SM-8.4/LoginPage';
import {HomePage} from '@pages/SM-8.4/HomePage';
import {NavigationPage} from '@pages/SM-8.4/NavigationPage';
import {ModelerHomePage} from '@pages/SM-8.4/ModelerHomePage';
import {ModelerCreatePage} from '@pages/SM-8.4/ModelerCreatePage';
import {TaskPanelPage} from '@pages/SM-8.4/TaskPanelPage';
import {TaskDetailsPage} from '@pages/SM-8.4/TaskDetailsPage';
import {OperateHomePage} from '@pages/SM-8.4/OperateHomePage';
import {OperateProcessesPage} from '@pages/SM-8.4/OperateProcessesPage';
import {OperateProcessInstancePage} from '@pages/SM-8.4/OperateProcessInstancePage';
import {OptimizeCollectionsPage} from '@pages/SM-8.4/OptimizeCollectionsPage';
import {OptimizeHomePage} from '@pages/SM-8.4/OptimizeHomePage';
import {FormJsPage} from '@pages/SM-8.4/FormJsPage';
import {OptimizeReportPage} from '@pages/SM-8.4/OptimizeReportPage';
import {ConsoleOrganizationPage} from '@pages/SM-8.4/ConsoleOrganizationPage';
import {SettingsPage} from '@pages/SM-8.4/SettingsPage';
import {ConnectorSettingsPage} from '@pages/SM-8.4/ConnectorSettingsPage';
import {ConnectorMarketplacePage} from '@pages/SM-8.4/ConnectorMarketplacePage';
import {IdentityPage} from '@pages/SM-8.4/IdentityPage';
import {ConsoleHomePage} from '@pages/SM-8.4/ConsoleHomePage';
import {OptimizeDashboardPage} from '@pages/SM-8.4/OptimizeDashboardPage';

type PlaywrightFixtures = {
  makeAxeBuilder: () => AxeBuilder;
  loginPage: LoginPage;
  homePage: HomePage;
  navigationPage: NavigationPage;
  modelerHomePage: ModelerHomePage;
  modelerCreatePage: ModelerCreatePage;
  taskPanelPage: TaskPanelPage;
  taskDetailsPage: TaskDetailsPage;
  operateHomePage: OperateHomePage;
  operateProcessesPage: OperateProcessesPage;
  optimizeCollectionsPage: OptimizeCollectionsPage;
  operateProcessInstancePage: OperateProcessInstancePage;
  optimizeHomePage: OptimizeHomePage;
  formJsPage: FormJsPage;
  optimizeReportPage: OptimizeReportPage;
  consoleOrganizationsPage: ConsoleOrganizationPage;
  settingsPage: SettingsPage;
  connectorSettingsPage: ConnectorSettingsPage;
  connectorMarketplacePage: ConnectorMarketplacePage;
  identityPage: IdentityPage;
  consoleHomePage: ConsoleHomePage;
  optimizeDashboardPage: OptimizeDashboardPage;
  overrideTrackingScripts: void;
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
  navigationPage: async ({page}, use) => {
    await use(new NavigationPage(page));
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
  connectorMarketplacePage: async ({page}, use) => {
    await use(new ConnectorMarketplacePage(page));
  },
  identityPage: async ({page}, use) => {
    await use(new IdentityPage(page));
  },
  consoleHomePage: async ({page}, use) => {
    await use(new ConsoleHomePage(page));
  },
  optimizeDashboardPage: async ({page}, use) => {
    await use(new OptimizeDashboardPage(page));
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
      await use();
    },
    {
      auto: true,
    },
  ],
});

export {test};
