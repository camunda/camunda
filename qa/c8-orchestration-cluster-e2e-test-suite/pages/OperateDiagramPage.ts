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
  readonly resetDiagramZoomButton: Locator;
  readonly diagramSpinner: Locator;

  constructor(page: Page) {
    this.page = page;
    this.diagram = this.page.getByTestId('diagram-canvas');
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

  getStateOverlay(elementId: string) {
    return this.page
      .locator(`[data-container-id="${elementId}"]`)
      .getByTestId('state-overlay-active');
  }

  // Dedicated WaitingStateOverlay component, not part of the state-overlay-{state} family above.
  getWaitingOverlay(elementId: string): Locator {
    return this.page
      .locator(`[data-container-id="${elementId}"]`)
      .getByTestId('waiting-state-overlay');
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
    state: 'active' | 'incidents' | 'canceled' | 'completedEndEvents',
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
