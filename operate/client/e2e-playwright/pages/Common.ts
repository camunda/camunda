/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {Page, Locator, ElementHandle, BrowserContext} from '@playwright/test';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {
  ARROW_HEAD_LENGTH,
  ARROW_BODY_LENGTH,
  getArrowStyles,
} from '../utils/arrowStyles';

export class Common {
  private page: Page;
  readonly openSettingsButton: Locator;
  readonly processesTab: Locator;
  readonly logoutButton: Locator;
  readonly operationsList: Locator;

  constructor(page: Page) {
    this.page = page;
    this.openSettingsButton = page.getByRole('button', {name: 'Open Settings'});
    this.processesTab = page.getByRole('link', {name: 'Processes'});
    this.logoutButton = page.getByRole('button', {name: 'Log out'});
    this.operationsList = page.getByTestId('operations-list');
  }

  clickOpenSettingsButton() {
    return this.openSettingsButton.click();
  }

  clickLogoutButton() {
    return this.logoutButton.click();
  }

  async logout() {
    await this.clickOpenSettingsButton();
    await this.clickLogoutButton();
  }

  collapseOperationsPanel() {
    return this.page
      .getByRole('button', {name: /collapse operations/i})
      .click();
  }

  expandOperationsPanel() {
    return this.page.getByRole('button', {name: /expand operations/i}).click();
  }

  changeTheme(theme: string) {
    return this.page.addInitScript((theme) => {
      window.localStorage.setItem(
        'sharedState',
        JSON.stringify({theme: theme}),
      );
    }, theme);
  }

  disableModalAnimation() {
    return this.page.addStyleTag({
      content: `
        .cds--modal-container {
          transform: none !important;
        }
        .cds--modal {
          transition: none !important;
        }
    `,
    });
  }

  async createArrowElement({
    direction,
    additionalContent,
  }: {
    direction: 'left' | 'right' | 'up' | 'down';
    additionalContent?: string;
  }) {
    const className = `custom-arrow-${generateUniqueID()}`;

    const arrowElement = await this.page.evaluateHandle(
      ([className = 'custom-arrow']) => {
        const arrow = document.createElement('div');
        arrow.classList.add(className);
        document.body.appendChild(arrow);
        return arrow;
      },
      [className],
    );

    await this.page.addStyleTag({
      content: getArrowStyles({className, direction, additionalContent}),
    });

    return arrowElement;
  }

  setArrowPosition({
    arrowElement,
    arrowPositionX,
    arrowPositionY,
  }: {
    arrowElement: ElementHandle<HTMLDivElement>;
    arrowPositionX: number;
    arrowPositionY: number;
  }) {
    return this.page.evaluate(
      ({arrow, x, y}) => {
        arrow.style.position = 'absolute';
        arrow.style.left = `${x}px`;
        arrow.style.top = `${y}px`;
      },
      {arrow: arrowElement, x: arrowPositionX, y: arrowPositionY},
    );
  }

  async addRightArrow(element: Locator, additionalContent?: string) {
    const arrowElement = await this.createArrowElement({
      direction: 'right',
      additionalContent,
    });

    const targetBoundingBox = await element.boundingBox();
    if (targetBoundingBox === null) {
      throw new Error(
        'An error occured when drawing the arrow: target bounding box is null',
      );
    }

    const arrowPositionX =
      targetBoundingBox.x - ARROW_BODY_LENGTH - ARROW_HEAD_LENGTH;
    const arrowPositionY = targetBoundingBox.y + targetBoundingBox.height / 2;

    await this.setArrowPosition({
      arrowElement,
      arrowPositionX,
      arrowPositionY,
    });
  }

  async addLeftArrow(element: Locator, additionalContent?: string) {
    const arrowElement = await this.createArrowElement({
      direction: 'left',
      additionalContent,
    });

    const targetBoundingBox = await element.boundingBox();
    if (targetBoundingBox === null) {
      throw new Error(
        'An error occured when drawing the arrow: target bounding box is null',
      );
    }

    const arrowPositionX =
      targetBoundingBox.x + targetBoundingBox.width + ARROW_HEAD_LENGTH;
    const arrowPositionY = targetBoundingBox.y + targetBoundingBox.height / 2;

    await this.setArrowPosition({
      arrowElement,
      arrowPositionX,
      arrowPositionY,
    });
  }

  async addDownArrow(element: Locator, additionalContent?: string) {
    const arrowElement = await this.createArrowElement({
      direction: 'down',
      additionalContent,
    });

    const targetBoundingBox = await element.boundingBox();
    if (targetBoundingBox === null) {
      throw new Error(
        'An error occured when drawing the arrow: target bounding box is null',
      );
    }

    const arrowPositionX = targetBoundingBox.x + targetBoundingBox.width / 2;
    const arrowPositionY =
      targetBoundingBox.y - ARROW_HEAD_LENGTH - ARROW_BODY_LENGTH;

    await this.setArrowPosition({
      arrowElement,
      arrowPositionX,
      arrowPositionY,
    });
  }

  async addUpArrow(element: Locator, additionalContent?: string) {
    const arrowElement = await this.createArrowElement({
      direction: 'up',
      additionalContent,
    });

    const targetBoundingBox = await element.boundingBox();
    if (targetBoundingBox === null) {
      throw new Error(
        'An error occured when drawing the arrow: target bounding box is null',
      );
    }

    const arrowPositionX = targetBoundingBox.x + targetBoundingBox.width / 2;
    const arrowPositionY =
      targetBoundingBox.y - ARROW_HEAD_LENGTH + ARROW_BODY_LENGTH;

    await this.setArrowPosition({
      arrowElement,
      arrowPositionX,
      arrowPositionY,
    });
  }

  deleteArrows() {
    return this.page.evaluate(() => {
      const arrowElement = document.querySelectorAll(
        `[class^="custom-arrow-"]`,
      );

      arrowElement.forEach((element) => {
        element.remove();
      });
    });
  }

  mockClientConfig(
    context: BrowserContext,
    config?: (typeof window)['clientConfig'],
  ) {
    const defaultConfig = {
      isEnterprise: true,
      canLogout: true,
      isLoginDelegated: false,
      contextPath: '',
      organizationId: null,
      clusterId: null,
      stage: null,
      mixpanelToken: null,
      mixpanelAPIHost: null,
    };

    return context.route('**/client-config.js', (route) =>
      route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'text/javascript;charset=UTF-8',
        },
        body: `window.clientConfig = ${JSON.stringify({
          ...defaultConfig,
          config,
        })};`,
      }),
    );
  }
}
