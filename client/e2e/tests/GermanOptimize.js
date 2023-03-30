/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import config from '../config';
import {cleanEntities} from '../setup';
import {login, save} from '../utils';
import * as e from './Homepage.elements.js';

fixture('German Optimize')
  .page(config.endpoint)
  .beforeEach(login)
  .afterEach(cleanEntities)
  .clientScripts({
    content: `
      navigator.__defineGetter__('languages', function () {
        return ['de-DE'];
      });`,
  });

test('Create a report in the german version of optimize', async (t) => {
  await t.click(e.createNewMenu).hover(e.newReportOption);
  await t.click(e.submenuOption('Prozessbericht'));

  await t.click(e.templateModalProcessField);
  await t.click(e.firstTypeaheadOption);
  await t.click(e.carbonModalConfirmBtn);

  await save(t);
});
