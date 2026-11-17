/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Response} from '@playwright/test';
import {BasePage} from '../base-page.ts';

class ProcessesPage extends BasePage {
  override goto(): Promise<Response | null> {
    return this.page.goto('/tasklist/processes');
  }

  heading = this.page.getByRole('heading', {name: 'Processes'});

  tutorialDialog = this.page.getByRole('dialog', {
    name: 'Start your process on demand',
  });
  tutorialContinueButton = this.tutorialDialog.getByRole('button', {
    name: 'Continue',
  });

  processTile(definitionId: string) {
    const container = this.page
      .getByTestId('process-tile')
      .filter({hasText: definitionId});
    return {
      container,
      startButton: container.getByRole('button', {name: 'Start process'}),
    };
  }
}

export {ProcessesPage};
