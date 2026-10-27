/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ProcessInstanceKey,
  ProcessInstanceStateEnum,
} from '@camunda8/orchestration-cluster-api';
import {type Response} from '@playwright/test';
import {BasePage, View} from '../base-page.ts';

class ProcessInstancePage extends BasePage {
  override goto(
    processInstanceKey: ProcessInstanceKey,
  ): Promise<Response | null> {
    return this.page.goto(`/operate/processes/${processInstanceKey}`);
  }

  #instanceHeader = this.page.getByTestId('instance-header');
  stateIcon = (state: ProcessInstanceStateEnum | 'INCIDENT') =>
    this.#instanceHeader.getByTestId(`${state}-icon`);
  retryButton = this.#instanceHeader.getByRole('button', {
    name: 'Retry Instance',
  });
  cancelButton = this.#instanceHeader.getByRole('button', {
    name: 'Cancel Instance',
  });
  modifyButton = this.#instanceHeader.getByRole('button', {
    name: 'Modify Instance',
  });
  migrateButton = this.#instanceHeader.getByRole('button', {
    name: 'Migrate Instance',
  });

  retryScheduledToast = this.page
    .getByRole('status')
    .getByText('Incidents are scheduled for retry');

  updateNoticeDialog = this.page.getByRole('dialog', {
    name: "Here's what moved in Operate",
  });
  dismissUpdateNoticeButton = this.updateNoticeDialog.getByRole('button', {
    name: 'Got it',
  });

  variablesPanel = new VariablesPanel(this.page);
}

class VariablesPanel extends View {
  trigger = this.page.getByRole('link', {name: 'Variables'});

  variablesTable = this.page.getByRole('table', {name: 'Variable List'});
  addVariableButton = this.page.getByRole('button', {name: 'Add variable'});

  variableUpdatedToast = this.page
    .getByRole('status')
    .getByText('Variable updated');

  variable(name: string) {
    const container = this.variablesTable
      .getByRole('row')
      .filter({hasText: name});

    return {
      container,
      viewButton: container.getByRole('button', {name: 'View full value'}),
      editButton: container.getByRole('button', {name: 'Edit variable'}),
      editField: container.getByRole('textbox', {name: 'Value'}),
      exitButton: container.getByRole('button', {name: 'Exit edit mode'}),
      saveButton: container.getByRole('button', {name: 'Save variable'}),
    };
  }
}

export {ProcessInstancePage};
