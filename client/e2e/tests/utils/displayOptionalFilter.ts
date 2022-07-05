/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {t} from 'testcafe';
import {screen, within} from '@testing-library/testcafe';

type OptionalFilter =
  | 'Variable'
  | 'Process Instance Key(s)'
  | 'Parent Process Instance Key'
  | 'Operation Id'
  | 'Error Message'
  | 'Start Date'
  | 'End Date';

const displayOptionalFilter = async (filterName: OptionalFilter) => {
  await t
    .click(screen.queryByTestId('more-filters-dropdown').shadowRoot().child())
    .click(
      within(
        screen.getByTestId('more-filters-dropdown').shadowRoot()
      ).getByText(filterName)
    );
};

export {displayOptionalFilter};
