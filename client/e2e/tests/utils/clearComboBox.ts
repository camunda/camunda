/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {t} from 'testcafe';
import {within, screen} from '@testing-library/testcafe';
import {IS_COMBOBOX_ENABLED} from '../../../src/modules/feature-flags';

const clearComboBox = async ({fieldName}: {fieldName: string}) => {
  if (!IS_COMBOBOX_ENABLED) {
    return;
  }

  const parentElement = screen.getByLabelText(fieldName).parent(0);

  await t.click(
    within(parentElement).getByRole('button', {
      name: 'Clear selected item',
    })
  );
};

export {clearComboBox};
