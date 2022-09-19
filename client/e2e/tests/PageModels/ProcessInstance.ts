/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {screen, within} from '@testing-library/testcafe';
import {t} from 'testcafe';

class ProcessInstancePage {
  newVariableNameField = screen.queryByTestId('add-variable-name').shadowRoot();
  newVariableValueField = screen
    .queryByTestId('add-variable-value')
    .shadowRoot();
  editVariableValueField = screen
    .getByTestId('edit-variable-value')
    .shadowRoot();
  saveVariableButton = screen.queryByRole('button', {name: 'Save variable'});
  addVariableButton = screen.queryByRole('button', {name: 'Add variable'});
  variableSpinner = screen.queryByTestId('edit-variable-spinner');
  operationSpinner = screen.queryByTestId('operation-spinner');

  getNewVariableNameFieldSelector = (variableName: string) => {
    return within(screen.getByTestId(variableName))
      .getByTestId('new-variable-name')
      .shadowRoot();
  };

  getNewVariableValueFieldSelector = (variableName: string) => {
    return within(screen.getByTestId(variableName))
      .getByTestId('new-variable-value')
      .shadowRoot();
  };

  getEditVariableFieldSelector = (variableName: string) => {
    return within(screen.getByTestId(variableName))
      .getByTestId('edit-variable-value')
      .shadowRoot();
  };

  getNewVariableNameFieldValue = (variableName: string) => {
    return within(
      this.getNewVariableNameFieldSelector(variableName)
    ).queryByRole('textbox').value;
  };

  getNewVariableValueFieldValue = (variableName: string) => {
    return within(
      this.getNewVariableValueFieldSelector(variableName)
    ).queryByRole('textbox').value;
  };

  getEditVariableFieldValue = (variableName: string) => {
    return within(this.getEditVariableFieldSelector(variableName)).queryByRole(
      'textbox'
    ).value;
  };

  typeText = async (
    field: Selector | SelectorPromise,
    text: string,
    options?: TypeActionOptions
  ) => {
    await t.typeText(within(field).queryByRole('textbox'), text, options);
  };
}

export const processInstancePage = new ProcessInstancePage();
