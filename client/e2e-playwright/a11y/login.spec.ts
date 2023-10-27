/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test} from '../test-fixtures';
import {Paths} from 'modules/Routes';
import {validateResults} from './validateResults';

test.describe('login', () => {
  for (const theme of ['light', 'dark']) {
    test(`have no violations in ${theme} theme`, async ({
      page,
      commonPage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.goto(Paths.login(), {
        waitUntil: 'networkidle',
      });

      const results = await makeAxeBuilder().analyze();

      validateResults(results);
    });
  }
});
