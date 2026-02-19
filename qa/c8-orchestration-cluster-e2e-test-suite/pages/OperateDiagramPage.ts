/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';
import {expect} from '@playwright/test';

export class OperateDiagramPage {
  private page: Page;
  readonly diagram: Locator;
  readonly popover: Locator;
  readonly resetDiagramZoomButton: Locator;
  readonly diagramSpinner: Locator;
  readonly monacoAriaContainer: Locator;
  readonly metadataModal: Locator;
  readonly metadataModalCloseButton: Locator;
  readonly monacoScrollableElement: Locator;
  readonly showIncidentButton: Locator;
  readonly showMetadataButton: Locator;
  readonly viewRootCauseDecisionLink: Locator;
  readonly popoverLink: (name: string | RegExp) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.diagram = this.page.getByTestId('diagram');
    this.popover = this.page.getByTestId('popover');
    this.popoverLink = (name: string | RegExp) =>
      this.popover.getByRole('link', {name});
    this.resetDiagramZoomButton = this.page.getByRole('button', {
      name: 'Reset diagram zoom',
    });
    this.diagramSpinner = page.getByTestId('diagram-spinner');
    this.monacoAriaContainer = page.locator('.monaco-aria-container');
    this.metadataModal = this.page.getByRole('dialog', {name: 'metadata'});
    this.metadataModalCloseButton = this.metadataModal.getByRole('button', {
      name: /close/i,
    });
    this.monacoScrollableElement = this.metadataModal.locator(
      '.monaco-scrollable-element',
    );
    this.showIncidentButton = this.popover.getByRole('button', {
      name: /show incident/i,
    });
    this.showMetadataButton = this.popover.getByRole('button', {
      name: 'show more metadata',
    });
    this.viewRootCauseDecisionLink = this.page.getByRole('link', {
      name: /View root cause decision/i,
    });
  }

  async moveCanvasHorizontally(dx: number) {
    const boundingBox = await this.page
      .getByTestId('diagram-body')
      .boundingBox();

    if (boundingBox === null) {
      throw new Error(
        'An error occurred when dragging the diagram: diagram bounding box is null',
      );
    }

    const startX = boundingBox.x + boundingBox.width / 2;
    const startY = boundingBox.y + 50;

    // move diagram into viewport to be fully visible
    await this.page.mouse.move(startX, startY);
    await this.page.mouse.down();
    await this.page.mouse.move(startX + dx, startY);
    await this.page.mouse.up();
  }

  clickFlowNode(flowNodeName: string) {
    return this.getFlowNode(flowNodeName).first().click({timeout: 20000});
  }

  clickSubProcess(subProcessName: string) {
    // Click on the top left corner of the sub process.
    // This avoids clicking on child elements inside the sub process.
    return this.getFlowNode(subProcessName).click({
      position: {x: 5, y: 5},
      force: true,
    });
  }

  getFlowNode(flowNodeName: string) {
    return this.diagram
      .locator('.djs-group')
      .locator(`[data-element-id="${flowNodeName}"]`);
  }

  async clickDiagramElement(elementName: string) {
    const element = await this.getLabeledElement(elementName);
    return element.click();
  }

  async getLabeledElement(eventName: string) {
    const eventLabel = this.diagram
      .locator('.djs-element')
      .filter({hasText: new RegExp(`${eventName}`, 'i')});

    const labelId = await eventLabel.getAttribute('data-element-id');
    const eventId = labelId?.split(/_label$/)[0];
    return this.diagram.locator(`[data-element-id="${eventId}"]`);
  }

  async clickShowMetaData() {
    await this.showMetadataButton.click();
  }

  async clickShowIncident() {
    await this.showIncidentButton.click();
  }

  getPopoverButton(name: string | RegExp) {
    return this.popover.getByRole('button', {name});
  }

  getPopoverText(text: string | RegExp): Locator {
    return this.popover.getByText(text);
  }

  getIncidentsOverlay(elementId: string): Locator {
    return this.diagram.locator(
      `[data-container-id="${elementId}"] [data-testid="state-overlay-incidents"] span`,
    );
  }

  getExecutionCountOverlay(elementId: string): Locator {
    return this.diagram.locator(
      `[data-container-id="${elementId}"] [data-testid="state-overlay-completed"][title="Execution Count"] span`,
    );
  }

  getExecutionCount(elementId: string) {
    return this.diagram.evaluate(
      (node, {elementId}) => {
        const completedOverlay: HTMLDivElement | null = node.querySelector(
          `[data-container-id="${elementId}"] [data-testid="state-overlay-completed"]`,
        );

        return completedOverlay?.innerText;
      },
      {elementId},
    );
  }

  getStateOverlay(elementId: string) {
    return this.page
      .locator(`[data-container-id="${elementId}"]`)
      .getByTestId('state-overlay-active');
  }

  async verifyFlowNodeMetadata(
    flowNodeId: string,
    options: {
      expectedText?: string | string[];
      hiddenText?: string | string[];
      isSubProcess?: boolean;
    } = {},
  ) {
    if (options.isSubProcess) {
      await this.clickSubProcess(flowNodeId);
    } else {
      await this.clickFlowNode(flowNodeId);
    }
    await this.clickShowMetaData();

    await this.monacoAriaContainer.waitFor({state: 'visible'});

    // Scroll to the bottom of the editor
    await this.monacoScrollableElement.evaluate((el) => {
      el.scrollTop = el.scrollHeight;
    });

    if (options.expectedText) {
      const expectedTexts = Array.isArray(options.expectedText)
        ? options.expectedText
        : [options.expectedText];
      for (const text of expectedTexts) {
        await expect(
          this.metadataModal.getByText(text, {exact: false}),
        ).toBeVisible({timeout: 15000});
      }
    }

    if (options.hiddenText) {
      const hiddenTexts = Array.isArray(options.hiddenText)
        ? options.hiddenText
        : [options.hiddenText];
      for (const text of hiddenTexts) {
        await expect(
          this.metadataModal.getByText(text, {exact: false}),
        ).toBeHidden();
      }
    }

    await this.metadataModalCloseButton.click();

    // Deselect the flow node by clicking it again
    if (options.isSubProcess) {
      await this.clickSubProcess(flowNodeId);
    } else {
      await this.clickFlowNode(flowNodeId);
    }
  }

  async verifyIncidentInPopover(incidentPattern: RegExp) {
    await expect(
      this.popover.getByRole('heading', {
        name: 'Incident',
      }),
    ).toBeVisible();

    await expect(this.popover.getByText(incidentPattern)).toBeVisible();
  }

  async closeMetadataModal(): Promise<void> {
    await this.metadataModalCloseButton.click();
  }

  async clickViewRootCauseDecisionLink(): Promise<void> {
    await this.viewRootCauseDecisionLink.scrollIntoViewIfNeeded();
    await this.viewRootCauseDecisionLink.waitFor({
      state: 'visible',
      timeout: 30000,
    });
    await this.viewRootCauseDecisionLink.click();
  }

  async clickPopoverLink(name: string | RegExp): Promise<void> {
    await this.popoverLink(name).click();
  }
}
