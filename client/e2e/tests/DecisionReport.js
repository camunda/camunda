/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Homepage from './Homepage.elements.js';
import * as Report from './DecisionReport.elements.js';

fixture('Decision Report')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('create a dmn js table report', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Invoice Classification', '2');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'Rules');

  await t.expect(Report.decisionTable.visible).ok();
  await t.expect(Report.decisionTableCell(1, 5).textContent).eql('2 (33.3%)');
});
