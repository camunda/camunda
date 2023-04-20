/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {within, screen} from '@testing-library/testcafe';
import {t} from 'testcafe';
import {selectComboBoxOption} from '../utils/selectComboBoxOption';

class DecisionsPage {
  Filters = {
    decisionName: {
      field: screen.queryByTestId('filter-decision-name'),
    },

    decisionVersion: {
      field: screen.queryByTestId('filter-decision-version'),
    },
  };

  selectDecision = async (option: string) => {
    await selectComboBoxOption({
      fieldName: 'Name',
      option,
      listBoxLabel: 'Select a Decision',
    });
  };

  selectVersion = async (option: string) => {
    await t.click(screen.queryByLabelText('Version', {selector: 'button'}));
    await t.click(
      within(screen.queryByLabelText('Select a Decision Version')).getByRole(
        'option',
        {name: option}
      )
    );
  };
}

export const decisionsPage = new DecisionsPage();
