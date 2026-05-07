/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Response} from '@playwright/test';
import {BasePage} from '../base-page.ts';

class TaskDetailsPage extends BasePage {
  override goto(): Promise<Response | null> {
    return this.page.goto('/tasklist');
  }

  taskHeader = this.page.locator('header[title="Task details header"]');
  assignedToMe = this.taskHeader.getByText('Assigned to me');
  assignTaskButton = this.page.getByRole('button', {name: 'Assign to me'});
  completeButton = this.page.getByRole('button', {name: 'Complete Task'});

  formField = (fieldName: string) =>
    this.page.getByRole('textbox', {name: fieldName});

  taskCompletedToast = this.page
    .getByRole('status')
    .filter({hasText: 'Task completed'});
}

export {TaskDetailsPage};
