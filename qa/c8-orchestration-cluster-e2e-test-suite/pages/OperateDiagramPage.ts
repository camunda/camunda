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
  readonly instanceHistory: Locator;
  readonly panelTabs: Locator;
  readonly detailsTabButton: Locator;
  readonly incidentsTabButton: Locator;
  readonly listenersTabButton: Locator;
  readonly listenersListTable: Locator;
  readonly elementInstanceDetailsTable: Locator;
  readonly incidentsTable: Locator;
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
    this.diagram = this.page.getByTestId('diagram-canvas');
    this.instanceHistory = this.page.getByTestId('instance-history');
    this.panelTabs = this.page.getByLabel('Process Instance Bottom Panel Tabs');
    this.detailsTabButton = this.page
      .getByLabel('Process Instance Bottom Panel Tabs')
      .getByRole('link', {name: /^Details$/i});
    this.incidentsTabButton = this.panelTabs.getByRole('link', {
      name: /^Incidents$/i,
    });
    this.listenersTabButton = this.panelTabs.getByRole('link', {
      name: /^Listeners$/i,
    });
    this.listenersListTable = this.page.getByRole('table', {
      name: /Listeners List/i,
    });
    this.elementInstanceDetailsTable = this.page.getByRole('table', {
      name: /Element Instance Details/i,
    });
    this.incidentsTable = this.page.getByTestId('data-list');
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
    elementId: string,
    options: {
      expectedText?: string | string[];
      hiddenText?: string | string[];
      isSubProcess?: boolean;
    } = {},
  ) {
    if (options.isSubProcess) {
      await this.clickSubProcess(elementId);
    } else {
      await this.clickFlowNode(elementId);
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
        ).toBeVisible({timeout: 30000});
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
      await this.clickSubProcess(elementId);
    } else {
      await this.clickFlowNode(elementId);
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

  async verifyMigratedFlowNodeInDetails(
    elementId: string,
    flowNodeLabel?: string | RegExp,
    options: {
      expectedStatusIcon?: 'ACTIVE' | 'COMPLETED' | 'TERMINATED';
      expectIncident?: boolean;
    } = {},
  ) {
    await this.clickFlowNode(elementId);

    // New UI: migration state is represented in the Listeners tab.
    await expect(this.listenersTabButton).toBeVisible({timeout: 30000});
    await this.listenersTabButton.click();
    await expect(this.listenersListTable).toBeVisible({timeout: 30000});
    await expect(
      this.listenersListTable.getByRole('row', {name: /\bmigrated\b/i}).first(),
    ).toBeVisible({timeout: 30000});

    // For generic migration verification, listener state is sufficient.
    if (!options.expectedStatusIcon && options.expectIncident === undefined) {
      return;
    }

    await expect(this.detailsTabButton).toBeVisible({timeout: 30000});
    await this.detailsTabButton.click();

    // The Details table is the most stable assertion for the new UI.
    await expect(this.elementInstanceDetailsTable).toBeVisible({
      timeout: 30000,
    });

    let historyNode: Locator;
    if (flowNodeLabel) {
      const nodeByLabel = this.instanceHistory
        .getByRole('treeitem', {name: flowNodeLabel})
        .first();

      if ((await nodeByLabel.count()) > 0) {
        historyNode = nodeByLabel;
      } else {
        // Fallback for nested/collapsed or renamed labels: rely on the selected history entry.
        historyNode = this.instanceHistory
          .getByRole('treeitem', {selected: true})
          .first();
      }
    } else {
      historyNode = this.instanceHistory
        .getByRole('treeitem', {selected: true})
        .first();
    }

    await expect(historyNode).toBeVisible({timeout: 30000});

    if (options.expectedStatusIcon) {
      await expect(
        historyNode.getByTestId(`${options.expectedStatusIcon}-icon`),
      ).toBeVisible();
    }

    if (options.expectIncident === true) {
      await expect(historyNode.getByTestId('INCIDENT-icon')).toBeVisible();
    }

    if (options.expectIncident === false) {
      await expect(historyNode.getByTestId('INCIDENT-icon')).toHaveCount(0);
    }
  }

  async verifyIncidentInIncidentsTab(
    errorTypePattern: RegExp,
    incidentMessagePattern: RegExp,
  ) {
    await expect(this.incidentsTabButton).toBeVisible({timeout: 30000});
    await this.incidentsTabButton.click();
    await expect(this.incidentsTable).toBeVisible({timeout: 30000});

    const errorTypeCell = this.incidentsTable
      .getByTestId('cell-errorType')
      .filter({hasText: errorTypePattern})
      .first();
    await expect(errorTypeCell).toBeVisible({timeout: 30000});

    const incidentRow = errorTypeCell.locator(
      'xpath=ancestor::*[@role="row"][1]',
    );
    const expandRowButton = incidentRow.getByRole('button', {
      name: /expand current row/i,
    });

    if ((await expandRowButton.count()) > 0) {
      const expanded = await expandRowButton.getAttribute('aria-expanded');
      if (expanded !== 'true') {
        await expandRowButton.click();
      }
    }

    await expect(
      this.incidentsTable.getByText(incidentMessagePattern),
    ).toBeVisible({
      timeout: 30000,
    });
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
