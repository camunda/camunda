/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {within, screen} from '@testing-library/testcafe';
import {t} from 'testcafe';

class DecisionsPage {
  Filters = {
    decisionName: {
      field: screen.queryByTestId('filter-decision-name'),
    },

    decisionVersion: {
      field: screen.queryByTestId('filter-decision-version'),
    },
  };

  selectDecision = async (name: string) => {
    await t.click(this.Filters.decisionName.field);
    await t.click(
      within(
        screen.queryByTestId('cm-flyout-decision-name').shadowRoot()
      ).queryByText(name)
    );
  };

  selectVersion = async (version: string) => {
    await t.click(this.Filters.decisionVersion.field);
    await t.click(
      within(
        screen.queryByTestId('cm-flyout-decision-version').shadowRoot()
      ).queryByText(version)
    );
  };
}

export const decisionsPage = new DecisionsPage();
