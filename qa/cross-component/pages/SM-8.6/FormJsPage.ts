import {Page, Locator} from '@playwright/test';

class FormJsPage {
  private page: Page;
  readonly aiFormGeneratorButton: Locator;
  readonly formRequestInput: Locator;
  readonly generateFormButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.aiFormGeneratorButton = page.getByRole('button', {
      name: 'AI Form Generator',
    });
    this.formRequestInput = page.getByPlaceholder(
      'A form for an american standard loan request',
    );
    this.generateFormButton = page.getByRole('button', {name: 'Generate form'});
  }

  async clickAIFormGeneratorButton(): Promise<void> {
    await this.aiFormGeneratorButton.click();
  }

  async clickFormRequestInput(): Promise<void> {
    await this.formRequestInput.click();
  }

  async fillFormRequestInput(formRequest: string): Promise<void> {
    await this.formRequestInput.fill(formRequest);
  }

  async clickGenerateFormButton(): Promise<void> {
    await this.generateFormButton.click();
  }
}

export {FormJsPage};
