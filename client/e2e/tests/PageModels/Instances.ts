/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {within, screen} from '@testing-library/testcafe';
import {IS_NEW_FILTERS_FORM} from '../../../src/modules/feature-flags';
import {t} from 'testcafe';

class InstancesPage {
  Filters = {
    active: {
      field: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-active').shadowRoot()
          ).queryByRole('checkbox')
        : screen.queryByRole('checkbox', {name: 'Active'}),
    },

    incidents: {
      field: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-incidents').shadowRoot()
          ).queryByRole('checkbox')
        : screen.queryByRole('checkbox', {name: 'Incidents'}),
    },

    runningInstances: {
      field: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-running-instances').shadowRoot()
          ).queryByRole('checkbox')
        : screen.queryByRole('checkbox', {name: 'Running Instances'}),
    },

    finishedInstances: {
      field: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-finished-instances').shadowRoot()
          ).queryByRole('checkbox')
        : screen.queryByRole('checkbox', {name: 'Finished Instances'}),
    },

    completed: {
      field: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-completed').shadowRoot()
          ).queryByRole('checkbox')
        : screen.queryByRole('checkbox', {name: 'Completed'}),
    },

    canceled: {
      field: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-canceled').shadowRoot()
          ).queryByRole('checkbox')
        : screen.queryByRole('checkbox', {name: 'Canceled'}),
    },

    errorMessage: {
      field: IS_NEW_FILTERS_FORM
        ? screen.queryByTestId('filter-error-message')
        : screen.queryByRole('textbox', {name: 'Error Message'}),
      value: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-error-message').shadowRoot()
          ).queryByRole('textbox')
        : screen.queryByRole('textbox', {name: 'Error Message'}),
    },

    parentInstanceId: {
      field: IS_NEW_FILTERS_FORM
        ? screen.queryByTestId('filter-parent-instance-id')
        : screen.queryByRole('textbox', {name: 'Parent Instance Id'}),
      value: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-parent-instance-id').shadowRoot()
          ).queryByRole('textbox')
        : screen.queryByRole('textbox', {name: 'Parent Instance Id'}),
    },

    startDate: {
      field: IS_NEW_FILTERS_FORM
        ? screen.queryByTestId('filter-start-date')
        : screen.queryByRole('textbox', {name: /start date/i}),
      value: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-start-date').shadowRoot()
          ).queryByRole('textbox')
        : screen.queryByRole('textbox', {name: /start date/i}),
    },

    endDate: {
      field: IS_NEW_FILTERS_FORM
        ? screen.queryByTestId('filter-end-date')
        : screen.queryByRole('textbox', {name: /end date/i}),
      value: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-end-date').shadowRoot()
          ).queryByRole('textbox')
        : screen.queryByRole('textbox', {name: /end date/i}),
    },

    operationId: {
      field: IS_NEW_FILTERS_FORM
        ? screen.queryByTestId('filter-operation-id')
        : screen.queryByRole('textbox', {name: /operation id/i}),
      value: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-operation-id').shadowRoot()
          ).queryByRole('textbox')
        : screen.queryByRole('textbox', {name: /operation id/i}),
    },

    variableName: {
      field: IS_NEW_FILTERS_FORM
        ? screen.queryByTestId('filter-variable-name')
        : screen.queryByRole('textbox', {name: /variable/i}),
      value: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-variable-name').shadowRoot()
          ).queryByRole('textbox')
        : screen.queryByRole('textbox', {name: /variable/i}),
    },

    variableValue: {
      field: IS_NEW_FILTERS_FORM
        ? screen.queryByTestId('filter-variable-value')
        : screen.queryByRole('textbox', {name: /value/i}),
      value: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-variable-value').shadowRoot()
          ).queryByRole('textbox')
        : screen.queryByRole('textbox', {name: /value/i}),
    },

    instanceIds: {
      field: IS_NEW_FILTERS_FORM
        ? screen.queryByTestId('filter-instance-ids')
        : screen.queryByRole('textbox', {
            name: 'Instance Id(s) separated by space or comma',
          }),
      value: IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('filter-instance-ids').shadowRoot()
          ).queryByRole('textbox')
        : screen.queryByRole('textbox', {
            name: 'Instance Id(s) separated by space or comma',
          }),
    },

    processName: {
      field: IS_NEW_FILTERS_FORM
        ? screen.queryByTestId('filter-process-name')
        : screen.queryByRole('combobox', {
            name: 'Process',
          }),
    },

    processVersion: {
      field: IS_NEW_FILTERS_FORM
        ? screen.queryByTestId('filter-process-version')
        : screen.queryByRole('combobox', {
            name: 'Process Version',
          }),
    },

    flowNode: {
      field: IS_NEW_FILTERS_FORM
        ? screen.queryByTestId('filter-flow-node')
        : screen.queryByRole('combobox', {
            name: /flow node/i,
          }),
    },
  };

  resetFiltersButton = screen.queryByRole('button', {name: /reset filters/i});
  selectAllInstancesCheckbox = screen.queryByTitle('Select all instances');

  typeText = async (
    field: Selector | SelectorPromise,
    text: string,
    options?: TypeActionOptions
  ) => {
    if (IS_NEW_FILTERS_FORM) {
      await t.typeText(
        within(field.shadowRoot()).queryByRole('textbox'),
        text,
        options
      );
    } else {
      await t.typeText(field, text, options);
    }
  };

  selectProcess = async (name: string) => {
    await t.click(
      IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('cm-flyout-process-name').shadowRoot()
          ).queryByText(name)
        : within(this.Filters.processName.field).queryByRole('option', {
            name,
          })
    );
  };

  selectVersion = async (version: string) => {
    await t.click(
      IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('cm-flyout-process-version').shadowRoot()
          ).queryByText(version)
        : within(this.Filters.processVersion.field).queryByRole('option', {
            name: version,
          })
    );
  };

  selectFlowNode = async (name: string) => {
    await t.click(
      IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('cm-flyout-flow-node').shadowRoot()
          ).queryByText(name)
        : within(this.Filters.flowNode.field).queryByRole('option', {
            name,
          })
    );
  };
}

export const instancesPage = new InstancesPage();
