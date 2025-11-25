/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type Locator, type Page} from '@playwright/test';

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

  constructor(page: Page) {
    this.page = page;
    this.diagram = this.page.getByTestId('diagram');
    this.popover = this.page.getByTestId('popover');
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

  showMetaData() {
    return this.popover
      .getByRole('button', {name: 'show more metadata'})
      .click();
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
    await this.showMetaData();

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
        ).toBeVisible();
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

  /**
   *
   * @param elementName corresponding element
   * @param state active/incidents/canceled/completedEndEvents
   * @returns locator to the state-overlay element
   */
  async getStateOverlayLocatorByElementNameAndState(
    elementName: string,
    state: string,
  ) {
    return this.page
      .locator(`[data-container-id=${elementName}]`)
      .getByTestId(`state-overlay-${state}`);
  }

  async verifyStateOverlay(
    flowNodeName: string,
    state: 'active' | 'canceled' | 'completedEndEvents',
    tokenAmount?: number,
  ): Promise<void> {
    const stateOverlayFlowNode =
      await this.getStateOverlayLocatorByElementNameAndState(
        flowNodeName,
        state,
      );
    await expect(stateOverlayFlowNode).toBeVisible({timeout: 30000});
    if (tokenAmount !== undefined) {
      expect(await stateOverlayFlowNode.innerText()).toContain(
        tokenAmount.toString(),
      );
    }
  }
}
