/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {screen} from '@testing-library/testcafe';

class InstancePage {
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
}

export const instancePage = new InstancePage();
