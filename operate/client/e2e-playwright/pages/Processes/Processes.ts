/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';
import {convertToQueryString} from '../../utils/convertToQueryString';
import {DeleteResourceModal} from '../components/DeleteResourceModal';
import MigrationModal from '../components/MigrationModal';
import MoveModificationModal from '../components/MoveModificationModal';
import {Diagram} from '../components/Diagram';
import {FiltersPanel} from './FiltersPanel';

export class Processes {
  private page: Page;
  readonly deleteResourceModal: InstanceType<typeof DeleteResourceModal>;
  readonly migrationModal: InstanceType<typeof MigrationModal>;
  readonly moveModificationModal: InstanceType<typeof MoveModificationModal>;
  readonly filtersPanel: InstanceType<typeof FiltersPanel>;
  readonly diagram: InstanceType<typeof Diagram>;
  readonly operationSpinner: Locator;
  readonly deleteResourceButton: Locator;
  readonly migrateButton: Locator;
  readonly moveButton: Locator;
  readonly processInstancesTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.deleteResourceModal = new DeleteResourceModal(page, {
      name: /Delete Process Definition/i,
    });
    this.migrationModal = new MigrationModal(page);
    this.moveModificationModal = new MoveModificationModal(page);
    this.filtersPanel = new FiltersPanel(page);
    this.diagram = new Diagram(page);
    this.operationSpinner = page.getByTestId('operation-spinner');
    this.deleteResourceButton = page.getByRole('button', {
      name: 'Delete Process Definition',
    });

    this.processInstancesTable = page.getByRole('region', {
      name: /process instances panel/i,
    });

    this.migrateButton = this.processInstancesTable.getByRole('button', {
      name: /^migrate$/i,
    });

    this.moveButton = this.processInstancesTable.getByRole('button', {
      name: /^move$/i,
    });
  }

  async gotoProcessesPage({
    searchParams,
    options,
  }: {
    searchParams?: Parameters<typeof convertToQueryString>[0];
    options?: Parameters<Page['goto']>[1];
  }) {
    if (searchParams === undefined) {
      await this.page.goto('/operate/processes');
      return;
    }

    await this.page.goto(
      `/operate/processes?${convertToQueryString(searchParams)}`,
      options,
    );
  }

  getNthProcessInstanceCheckbox = (index: number) => {
    return this.processInstancesTable
      .getByRole('row', {name: /select row/i})
      .nth(index)
      .locator('label');
  };

  getNthProcessInstanceLink = (index: number) => {
    return this.processInstancesTable
      .getByRole('link', {name: /view instance/i})
      .nth(index);
  };

      this.migrateButton = this.processInstancesTable.getByRole('button', {
      name: /^migrate$/i,
    });

    this.moveButton = this.processInstancesTable.getByRole('button', {
      name: /^move$/i,
    });
}
