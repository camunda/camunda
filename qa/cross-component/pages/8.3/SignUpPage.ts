import {Page, Locator} from '@playwright/test';

class SignUpPage {
  private page: Page;
  readonly firstNameInput: Locator;
  readonly lastNameInput: Locator;
  readonly passwordInput: Locator;
  readonly passwordInputInvitedUser: Locator;
  readonly signupButton: Locator;
  readonly emailInput: Locator;
  readonly companyInput: Locator;
  readonly createAccountButton: Locator;
  readonly backgroundDrodownList: Locator;
  readonly whatDoYouDoDropdownList: Locator;
  readonly APIClientDropdownList: Locator;
  readonly levelOfExperienceDropdownList: Locator;
  readonly nextButton: Locator;
  readonly runProcessRadioButton: Locator;
  readonly whyAreYouEvaluatingCamunda: Locator;
  readonly confirmSaaSTrial: Locator;
  readonly loginToCamundaButton: Locator;
  readonly backgroundPageHeading: Locator;
  readonly useCasePageHeading: Locator;
  readonly goalPageHeading: Locator;
  readonly camundaPlansPageHeading: Locator;
  readonly setPasswordPageHeading: Locator;
  readonly signUpCompletedPageHeading: Locator;

  constructor(page: Page) {
    this.page = page;
    this.firstNameInput = page.getByLabel('First Name');
    this.lastNameInput = page.getByLabel('Last Name');
    this.passwordInput = page.getByPlaceholder('Password (min 12 characters)');
    this.passwordInputInvitedUser = page.getByLabel(
      'Password (min 12 characters)',
    );
    this.signupButton = page.getByRole('button', {name: 'Sign up'});
    this.emailInput = page.getByLabel('Email');
    this.companyInput = page.getByLabel('Company*');
    this.createAccountButton = page.getByRole('button', {
      name: 'Create account',
    });
    this.backgroundDrodownList = page.getByLabel('Background*');
    this.whatDoYouDoDropdownList = page.locator('#input_4_51_2');
    this.APIClientDropdownList = page.getByRole('combobox', {
      name: 'what is your preferred api client?',
    });
    this.levelOfExperienceDropdownList = page.getByLabel(
      'what is your level of experience with process design?*',
    );
    this.nextButton = page.getByRole('button', {name: 'Next'});
    this.runProcessRadioButton = page.getByRole('radio');
    this.whyAreYouEvaluatingCamunda = page.getByLabel(
      'Why are you evaluating Camunda?*',
    );
    this.confirmSaaSTrial = page.getByRole('button', {
      name: 'Confirm SaaS Trial',
    });
    this.loginToCamundaButton = page.getByRole('button', {
      name: 'log in to camunda',
    });
    this.backgroundPageHeading = page.getByRole('heading', {
      name: 'great! to customize your account, tell us a little about yourself',
    });
    this.useCasePageHeading = page.getByRole('heading', {
      name: 'thank you! how will you use camunda',
    });
    this.goalPageHeading = page.getByRole('heading', {
      name: 'finally, what is your goal with camunda?',
    });
    this.camundaPlansPageHeading = page.getByRole('heading', {
      name: 'Here is your custom recommendation, please confirm your Camunda account below:',
    });
    this.setPasswordPageHeading = page.getByRole('heading', {
      name: 'Finish',
    });
    this.signUpCompletedPageHeading = page.getByRole('heading', {
      name: 'thank you for signing up for camunda',
    });
    this.runProcessRadioButton = page.getByRole('radio', {name: 'yes'});
  }

  async clickFirstNameInput(): Promise<void> {
    await this.firstNameInput.click();
  }

  async fillFirstNameInput(firstName: string): Promise<void> {
    await this.firstNameInput.fill(firstName);
  }

  async clickLastNameInput(): Promise<void> {
    await this.lastNameInput.click();
  }

  async fillLastNameInput(lastName: string): Promise<void> {
    await this.lastNameInput.fill(lastName);
  }

  async clickPasswordInput(): Promise<void> {
    await this.passwordInput.click();
  }

  async fillPasswordInput(password: string): Promise<void> {
    await this.passwordInput.fill(password);
  }

  async clickPasswordInputInvitedUser(): Promise<void> {
    await this.passwordInputInvitedUser.click({timeout: 30000});
  }

  async fillPasswordInputInvitedUser(password: string): Promise<void> {
    await this.passwordInputInvitedUser.fill(password);
  }

  async clickCompanyInput(): Promise<void> {
    await this.companyInput.click();
  }

  async fillCompanyInput(company: string): Promise<void> {
    await this.companyInput.fill(company);
  }

  async clickSignupButton(): Promise<void> {
    await this.signupButton.click();
  }

  async clickEmailInput(): Promise<void> {
    await this.emailInput.click();
  }

  async fillEmailInput(email: string): Promise<void> {
    await this.emailInput.fill(email);
  }

  async selectEndpointsToAutomate(endpoint: string): Promise<void> {
    // Select the container to expand the dropdown
    await this.page.getByText('choose all that apply').hover();
    const dropdownContainerSelector = '.ginput_container_multiselect';
    await this.page.click(dropdownContainerSelector);

    // Wait for the dropdown options to appear
    await this.page.waitForSelector(
      '.multiselect-dropdown-list > div > input[type="checkbox"]',
    );

    // Select the checkbox
    const checkboxSelector = `.multiselect-dropdown-list  > div > input[type="checkbox"] + label:has-text("${endpoint}")`;
    await this.page.click(checkboxSelector, {timeout: 60000});
  }

  async clickCreateAccount(): Promise<void> {
    await this.createAccountButton.click();
  }

  async clickNext(): Promise<void> {
    await this.nextButton.click();
  }

  async selectBackground(background: string): Promise<void> {
    await this.backgroundDrodownList.selectOption(background);
  }

  async selectWhatDoYouDo(profession: string): Promise<void> {
    await this.whatDoYouDoDropdownList.selectOption(profession);
  }

  async selectAPIClient(apiClient: string): Promise<void> {
    await this.APIClientDropdownList.selectOption(apiClient, {timeout: 40000});
  }

  async selectLevelOfExperience(experience: string): Promise<void> {
    await this.levelOfExperienceDropdownList.selectOption(experience);
  }

  async selectRunProcessRadioButton(): Promise<void> {
    await this.runProcessRadioButton.check({timeout: 30000});
  }

  async selectWhyEvaluatingCamunda(reason: string): Promise<void> {
    await this.whyAreYouEvaluatingCamunda.selectOption(reason);
  }

  async clickConfirmSaaSTrial(): Promise<void> {
    await this.confirmSaaSTrial.click();
  }

  async clickLoginToCamunda(): Promise<void> {
    await this.loginToCamundaButton.click();
  }
}

export {SignUpPage};
