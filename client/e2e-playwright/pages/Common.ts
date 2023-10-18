/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

  constructor(page: Page) {
    this.page = page;
    this.openSettingsButton = page.getByRole('button', {name: 'Open Settings'});
    this.processesTab = page.getByRole('link', {name: 'Processes'});
    this.logoutButton = page.getByRole('button', {name: 'Log out'});
  }

  async clickOpenSettingsButton(): Promise<void> {
    await this.openSettingsButton.click();
  }

  async clickLogoutButton(): Promise<void> {
    await this.logoutButton.click();
  }

  async logout(): Promise<void> {
    await this.clickOpenSettingsButton();
    await this.clickLogoutButton();
  }

  async changeTheme(theme: string): Promise<void> {
    await this.page.addInitScript((theme) => {
      window.localStorage.setItem(
        'sharedState',
        JSON.stringify({theme: theme}),
      );
    }, theme);
  }

  async disableModalAnimation() {
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

  async setArrowPosition({
    arrowElement,
    arrowPositionX,
    arrowPositionY,
  }: {
    arrowElement: ElementHandle<HTMLDivElement>;
    arrowPositionX: number;
    arrowPositionY: number;
  }) {
    await this.page.evaluate(
      ({arrow, x, y}) => {
        arrow.style.position = 'absolute';
        arrow.style.left = `${x}px`;
        arrow.style.top = `${y}px`;
      },
      {arrow: arrowElement, x: arrowPositionX, y: arrowPositionY},
    );
  }

  async addRightArrow(
    element: Locator,
    additionalContent?: string,
  ): Promise<void> {
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

  async addLeftArrow(
    element: Locator,
    additionalContent?: string,
  ): Promise<void> {
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

  async addDownArrow(
    element: Locator,
    additionalContent?: string,
  ): Promise<void> {
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

  async addUpArrow(
    element: Locator,
    additionalContent?: string,
  ): Promise<void> {
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

  async deleteArrows(): Promise<void> {
    await this.page.evaluate(() => {
      const arrowElement = document.querySelectorAll(
        `[class^="custom-arrow-"]`,
      );

      arrowElement.forEach((element) => {
        element.remove();
      });
    });
  }

  async mockClientConfig(
    context: BrowserContext,
    config?: (typeof window)['clientConfig'],
  ): Promise<void> {
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

    await context.route('**/client-config.js', (route) =>
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
