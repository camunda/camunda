/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Report from './ProcessReport.elements.js';

fixture('Process Report Filter')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('should apply a filter to the report result', async t => {
  await u.createNewReport(t);

  await u.selectDefinition(t, 'Invoice Receipt', '2');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');

  const unfiltered = +(await Report.reportRenderer.textContent);

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Variable'));

  await t.click(Report.variableFilterTypeahead);
  await t.click(Report.variableFilterTypeaheadOption('amount'));
  await t.click(Report.variableFilterOperatorButton('is less than'));

  await t.typeText(Report.variableFilterValueInput, '100', {replace: true});

  await t.click(Report.primaryModalButton);

  const filtered = +(await Report.reportRenderer.textContent);

  await t.expect(unfiltered).gt(filtered);
});
