/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  Page,
  Locator,
  ElementHandle,
  BrowserContext,
} from '@playwright/test';
import {
  ARROW_HEAD_LENGTH,
  ARROW_BODY_LENGTH,
  getArrowStyles,
} from '../utils/arrowStyles';

const generateUniqueID = () => {
  return (Date.now() + Math.floor(Math.random() * 100)).toString();
};

type ClientConfig = {
  isEnterprise?: boolean;
  contextPath?: string;
  baseName?: string;
  organizationId?: null | string;
  clusterId?: null | string;
  canLogout?: null | boolean;
  isLoginDelegated?: null | boolean;
  mixpanelToken?: null | string;
  mixpanelAPIHost?: null | string;
  tasklistUrl?: null | string;
  resourcePermissionsEnabled?: boolean;
  multiTenancyEnabled?: boolean;
};

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
        'An error occurred when drawing the arrow: target bounding box is null',
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
        'An error occurred when drawing the arrow: target bounding box is null',
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
        'An error occurred when drawing the arrow: target bounding box is null',
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
        'An error occurred when drawing the arrow: target bounding box is null',
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

  mockClientConfig(context: BrowserContext, config?: ClientConfig) {
    const defaultConfig = {
      isEnterprise: true,
      canLogout: true,
      isLoginDelegated: false,
      contextPath: '',
      baseName: '/operate',
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
