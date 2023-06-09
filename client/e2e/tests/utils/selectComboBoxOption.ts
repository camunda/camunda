/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {t} from 'testcafe';
import {within, screen} from '@testing-library/testcafe';

const selectComboBoxOption = async ({
  fieldName,
  option,
  listBoxLabel,
}: {
  fieldName: string;
  option: string;
  listBoxLabel: string;
}) => {
  await t
    .expect(screen.queryByLabelText(fieldName).hasAttribute('disabled'))
    .notOk();
  await t.click(screen.getByLabelText(fieldName));
  await t.click(
    within(screen.getByRole('listbox', {name: listBoxLabel})).getByRole(
      'option',
      {name: option}
    )
  );
};

export {selectComboBoxOption};
