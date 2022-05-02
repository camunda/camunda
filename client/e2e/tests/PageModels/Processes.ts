/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {within, screen} from '@testing-library/testcafe';
import {t} from 'testcafe';

class ProcessesPage {
  Filters = {
    active: {
      field: within(
        screen.queryByTestId('filter-active').shadowRoot()
      ).queryByRole('checkbox'),
    },

    incidents: {
      field: within(
        screen.queryByTestId('filter-incidents').shadowRoot()
      ).queryByRole('checkbox'),
    },

    runningInstances: {
      field: within(
        screen.queryByTestId('filter-running-instances').shadowRoot()
      ).queryByRole('checkbox'),
    },

    finishedInstances: {
      field: within(
        screen.queryByTestId('filter-finished-instances').shadowRoot()
      ).queryByRole('checkbox'),
    },

    completed: {
      field: within(
        screen.queryByTestId('filter-completed').shadowRoot()
      ).queryByRole('checkbox'),
    },

    canceled: {
      field: within(
        screen.queryByTestId('filter-canceled').shadowRoot()
      ).queryByRole('checkbox'),
    },

    errorMessage: {
      field: screen.queryByTestId('filter-errorMessage'),
      value: within(
        screen.queryByTestId('filter-errorMessage').shadowRoot()
      ).queryByRole('textbox'),
    },

    parentInstanceId: {
      field: screen.queryByTestId('filter-parentInstanceId'),
      value: within(
        screen.queryByTestId('filter-parentInstanceId').shadowRoot()
      ).queryByRole('textbox'),
    },

    startDate: {
      field: screen.queryByTestId('filter-startDate'),
      value: within(
        screen.queryByTestId('filter-startDate').shadowRoot()
      ).queryByRole('textbox'),
    },

    endDate: {
      field: screen.queryByTestId('filter-endDate'),
      value: within(
        screen.queryByTestId('filter-endDate').shadowRoot()
      ).queryByRole('textbox'),
    },

    operationId: {
      field: screen.queryByTestId('filter-operationId'),
      value: within(
        screen.queryByTestId('filter-operationId').shadowRoot()
      ).queryByRole('textbox'),
    },

    variableName: {
      field: screen.queryByTestId('filter-variable-name'),
      value: within(
        screen.queryByTestId('filter-variable-name').shadowRoot()
      ).queryByRole('textbox'),
    },

    variableValue: {
      field: screen.queryByTestId('filter-variable-value'),
      value: within(
        screen.queryByTestId('filter-variable-value').shadowRoot()
      ).queryByRole('textbox'),
    },

    instanceIds: {
      field: screen.queryByTestId('filter-ids'),
      value: within(
        screen.queryByTestId('filter-ids').shadowRoot()
      ).queryByRole('textbox'),
    },

    processName: {
      field: screen.queryByTestId('filter-process-name'),
    },

    processVersion: {
      field: screen.queryByTestId('filter-process-version'),
    },

    flowNode: {
      field: screen.queryByTestId('filter-flow-node'),
    },
  };

  resetFiltersButton = screen.queryByRole('button', {name: /reset filters/i});
  selectAllInstancesCheckbox = screen.queryByTitle('Select all instances');

  typeText = async (
    field: Selector | SelectorPromise,
    text: string,
    options?: TypeActionOptions
  ) => {
    await t.typeText(
      within(field.shadowRoot()).queryByRole('textbox'),
      text,
      options
    );
  };

  selectProcess = async (name: string) => {
    await t.click(
      within(
        screen.queryByTestId('cm-flyout-process-name').shadowRoot()
      ).queryByText(name)
    );
  };

  selectVersion = async (version: string) => {
    await t.click(
      within(
        screen.queryByTestId('cm-flyout-process-version').shadowRoot()
      ).queryByText(version)
    );
  };

  selectFlowNode = async (name: string) => {
    await t.click(
      within(
        screen.queryByTestId('cm-flyout-flow-node').shadowRoot()
      ).queryByText(name)
    );
  };
}

export const processesPage = new ProcessesPage();
