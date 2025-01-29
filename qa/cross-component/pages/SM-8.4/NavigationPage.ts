import {Page} from '@playwright/test';
import {LoginPage} from './LoginPage';

class NavigationPage {
  private page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async goTo(url: string): Promise<void> {
    await this.page.goto(url, {timeout: 90000});
    const loginPage = new LoginPage(this.page);
    loginPage.loginAfterPermissionsReadded(
      'demo',
      process.env.DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD!,
    );
  }

  async goToModeler(): Promise<void> {
    await this.goTo('/modeler');
  }

  async goToTasklist(): Promise<void> {
    await this.goTo('/tasklist');
  }

  async goToOperate(): Promise<void> {
    await this.goTo('/operate');
  }

  async goToOptimize(): Promise<void> {
    await this.goTo('/optimize');
  }

  async goToIdentity(): Promise<void> {
    await this.goTo('/identity');
  }

  async goToConsole(): Promise<void> {
    this.page.goto('/', {timeout: 90000});
  }
}

export {NavigationPage};
