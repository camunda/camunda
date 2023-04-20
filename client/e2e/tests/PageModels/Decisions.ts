/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {within, screen} from '@testing-library/testcafe';
import {t} from 'testcafe';
import {IS_COMBOBOX_ENABLED} from '../../../src/modules/feature-flags';
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
    if (IS_COMBOBOX_ENABLED) {
      await selectComboBoxOption({
        fieldName: 'Name',
        option,
        listBoxLabel: 'Select a Decision',
      });
    } else {
      await t.click(this.Filters.decisionName.field);
      await t.click(
        within(
          screen.queryByTestId('cm-flyout-decision-name').shadowRoot()
        ).queryByText(option)
      );
    }
  };

  selectVersion = async (option: string) => {
    if (IS_COMBOBOX_ENABLED) {
      await t.click(screen.queryByLabelText('Version', {selector: 'button'}));
      await t.click(
        within(screen.queryByLabelText('Select a Decision Version')).getByRole(
          'option',
          {name: option}
        )
      );
    } else {
      await t.click(this.Filters.decisionVersion.field);
      await t.click(
        within(
          screen.queryByTestId('cm-flyout-decision-version').shadowRoot()
        ).queryByText(option)
      );
    }
  };
}

export const decisionsPage = new DecisionsPage();
