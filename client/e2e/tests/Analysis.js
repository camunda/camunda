/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Analysis from './Analysis.elements.js';

fixture('Process Analysis')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('Branch Analysis', async t => {
  await t.click(Analysis.navItem);

  await u.selectDefinition(t, 'Lead Qualification', '1');

  await t.click(Analysis.flowNode('call_right_away'));
  await t.click(Analysis.flowNode('msLeadIsOpp'));

  await t.expect(Analysis.statisticsDiagram.visible).ok();
});
