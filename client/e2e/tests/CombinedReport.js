/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Homepage from './Homepage.elements.js';
import * as Report from './ProcessReport.elements.js';
import * as Combined from './CombinedReport.elements.js';

fixture('Combined Report')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('combine two single number reports', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Lead Qualification', '1');

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');

  await t.typeText(Report.nameEditField, 'Report 1', {replace: true});

  await u.save(t);

  await u.gotoOverview(t);

  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt', '2');

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');

  await t.typeText(Report.nameEditField, 'Report 2', {replace: true});

  await u.save(t);

  await u.gotoOverview(t);

  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Combined Process Report'));

  await t.click(Combined.report('Report 1'));
  await t.click(Combined.report('Report 2'));

  await t.expect(Combined.chartRenderer.visible).ok();

  await u.save(t);
});
