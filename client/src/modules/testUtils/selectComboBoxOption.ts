/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {screen} from '@testing-library/react';
import {UserEvent} from 'modules/testing-library';

const selectComboBoxOption = async ({
  user,
  fieldName,
  itemName,
}: {
  user: UserEvent;
  fieldName: string;
  itemName: string;
}) => {
  await user.click(screen.getByLabelText(fieldName));
  await user.selectOptions(screen.getByRole('listbox'), [
    screen.getByRole('option', {name: itemName}),
  ]);
};

export {selectComboBoxOption};
