/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';

export class OperateDiagramPage {
  private page: Page;
  readonly diagram: Locator;
  readonly popover: Locator;
  readonly resetDiagramZoomButton: Locator;
  readonly diagramSpinner: Locator;

  constructor(page: Page) {
    this.page = page;
    this.diagram = this.page.getByTestId('diagram');
    this.popover = this.page.getByTestId('popover');
    this.resetDiagramZoomButton = this.page.getByRole('button', {
      name: 'Reset diagram zoom',
    });
    this.diagramSpinner = page.getByTestId('diagram-spinner');
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
    return this.getFlowNode(flowNodeName).click();
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
      .locator('.djs-element')
      .filter({hasText: new RegExp(`^${flowNodeName}$`, 'i')});
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
}
